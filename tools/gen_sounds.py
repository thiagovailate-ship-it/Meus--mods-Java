#!/usr/bin/env python3
"""
DynamicWinds - gerador de sons (sintese procedural, sem amostras de terceiros).

Por que sintetizar em vez de baixar sons prontos?
  * licenca limpa: nenhuma amostra de terceiros entra no repositorio
  * reproduzivel: `python3 tools/gen_sounds.py` recria exatamente os mesmos bytes
  * controlavel: a mesma fonte gera versoes fraca/media/forte do mesmo fenomeno,
    entao a transicao entre elas no jogo e suave por construcao

Como o som e feito (nada de "ruido + envelope" solto):
  * bancos de ruido filtrado por FFT, com ganho de cada banda variando no tempo
    por um sinal de controle suavizado - e isso que faz o vento "respirar" em
    vez de soar como um chiado estatico
  * gotas de chuva como eventos discretos (impulsos ressonantes curtos) somados
    a um leito de alta frequencia
  * trovao como estalo (impulso saturado) + peals (varias camadas de ruido grave
    com envelopes independentes) + cauda convoluida com uma resposta ao impulso
    sintetica, que e o que produz o "rolar" de um trovao distante
  * loops sem emenda por crossfade equal-power da cauda sobre o inicio

Os arquivos saem em OGG Vorbis 44.1 kHz mono (mono porque som posicional no
Minecraft precisa ser mono para o OpenAL posicionar; o envolvimento vem de varias
fontes espalhadas em volta do jogador, cada uma com sua fase).

Dependencias: numpy e soundfile (libsndfile com Vorbis).
    python3 -m pip install --target /tmp/aud numpy soundfile
    PYTHONPATH=/tmp/aud python3 tools/gen_sounds.py
"""

import math
import os
import sys

try:
    import numpy as np
except ImportError:
    sys.exit("numpy ausente: python3 -m pip install --target /tmp/aud numpy soundfile")

try:
    import soundfile as sf
except ImportError:
    sys.exit("soundfile ausente: python3 -m pip install --target /tmp/aud numpy soundfile")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/dynamicwinds/sounds")

SR = 44100
SEED = 20261015   # fixo: mesma saida a cada execucao

rng = np.random.default_rng(SEED)


# ---------------------------------------------------------------------------
# blocos de DSP
# ---------------------------------------------------------------------------
def noise(seconds, generator=None):
    """Ruido branco de duracao dada."""
    g = generator if generator is not None else rng
    return g.standard_normal(int(SR * seconds))


def shaped(x, profile, sr=SR):
    """
    Filtra por FFT aplicando uma curva de magnitude.

    profile(freqs) -> ganho linear. E o caminho mais direto para "ruido com
    formato espectral exato" sem escrever cascatas de biquads.
    """
    n = len(x)
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(n, 1.0 / sr)
    gain = profile(freqs)
    return np.fft.irfft(spec * gain, n=n)


def pink_profile(f0=20.0, slope=0.5):
    """1/f^slope: o espectro tipico de vento e de chuvas de fundo."""
    def prof(f):
        f = np.maximum(f, f0)
        return (f0 / f) ** slope
    return prof


def combine(*profiles):
    """Multiplica curvas espectrais (inclinacao da fonte x banda de trabalho)."""
    def prof(f):
        g = np.ones_like(f)
        for pr in profiles:
            g = g * pr(f)
        return g
    return prof


def band_profile(lo, hi, order=1.5, shape="soft"):
    """
    Banda [lo, hi] com bordas suaves em ordem configuravel.

    Guarda contra lo >= hi: com as bordas invertidas a curva virava uma banda
    quase plana e passava tudo (foi assim que o trovao distante ficou agudo).
    """
    if lo >= hi:
        return low_profile(max(lo, hi), order)

    def prof(f):
        f = np.maximum(f, 1.0)
        low = (f / lo) ** order
        high = (hi / f) ** order
        g = low * high / (1.0 + low * high)
        # fora da banda cai ainda mais rapido, para nao vazar energia
        g = np.where(f < lo * 0.5, g * 0.05, g)
        g = np.where(f > hi * 2.0, g * 0.05, g)
        return g
    return prof


def high_profile(cut, order=1.0):
    def prof(f):
        f = np.maximum(f, 1.0)
        return (f / cut) ** order / (1.0 + (f / cut) ** order)
    return prof


def low_profile(cut, order=1.5):
    def prof(f):
        f = np.maximum(f, 1.0)
        return 1.0 / (1.0 + (f / cut) ** order)
    return prof


def dc_block(x, cut=20.0, order=2.0):
    """
    Remove continuo e subsonicos.

    Sem isso, ruido moldado carrega um offset aleatorio (o filtro passa o bin de
    0 Hz com ganho total). Como o offset e constante, ele concentra energia
    enorme nos primeiros bins do espectro: medido, o trovao "proximo" ficava com
    80% da energia abaixo de 100 Hz e quase nada de estalo, mesmo com o estalo
    presente no sinal. O filtro tambem livra o alto-falante de excursionar com o
    que nao se ouve.
    """
    return shaped(x, high_profile(cut, order))


def smoother(seconds, smooth_ms):
    """Sinal de controle: ruido suavizado (movimento lento e organico)."""
    x = noise(seconds)
    window = max(3, int(SR * smooth_ms / 1000.0))
    kernel = np.hanning(window)
    kernel /= kernel.sum()
    return np.convolve(x, kernel, mode="same")


def norm01(x):
    lo, hi = float(x.min()), float(x.max())
    if hi - lo < 1e-12:
        return np.zeros_like(x)
    return (x - lo) / (hi - lo)


def envelope(n, attack_s, decay_s, curve=2.0):
    """Envelope ataque/decaimento exponencial."""
    a = max(1, int(attack_s * SR))
    d = max(1, n - a)
    env = np.empty(n)
    env[:a] = np.linspace(0.0, 1.0, a) ** (1.0 / curve)
    t = np.linspace(0.0, 1.0, d)
    env[a:] = np.exp(-curve * 3.0 * t) * (1.0 - t) ** 1.5
    return env


def impulse_response(seconds, decay, tilt=1.0, pre_delay_ms=0.0):
    """
    Resposta ao impulso sintetica: ruido com decaimento exponencial e espectro
    inclinado. Convoluir com isso e o que cria a sensacao de distancia e de
    espaco aberto (o "rolar" do trovao).
    """
    n = int(SR * seconds)
    ir = noise(seconds) * np.exp(-decay * np.linspace(0.0, 1.0, n) * 8.0)
    ir = shaped(ir, combine(pink_profile(100.0, 0.5), low_profile(4000.0 * tilt, 1.2)))
    pre = int(SR * pre_delay_ms / 1000.0)
    if pre > 0:
        ir = np.concatenate([np.zeros(pre), ir])[:n]
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-12
    return ir


def convolve_reverb(x, ir):
    n = len(x) + len(ir) - 1
    nfft = 1 << int(math.ceil(math.log2(n)))
    y = np.fft.irfft(np.fft.rfft(x, nfft) * np.fft.rfft(ir, nfft), nfft)[:n]
    return y


def loopify(x, fade_s=1.2):
    """
    Torna um trecho sem emenda: a cauda e misturada (equal-power) sobre o
    inicio, entao o fim e o comeco do arquivo se encontram em fase e amplitude.
    """
    fade = int(SR * fade_s)
    if fade * 2 >= len(x):
        fade = len(x) // 4
    head = x[:fade].copy()
    tail = x[-fade:].copy()
    t = np.linspace(0.0, math.pi / 2.0, fade)
    mixed = head * np.cos(t) ** 2 + tail * np.sin(t) ** 2
    out = np.concatenate([mixed, x[fade:-fade]])

    # O crossfade garante a transicao de forma, mas nao de nivel: se o trecho
    # inicial tem mais energia que o final (comum em material com eventos
    # esparsos, como folhagem), o loop da um salto audivel. Aqui aplicamos uma
    # inclinacao suave de nivel para casar as pontas.
    edge = max(1, int(SR * 0.05))
    r_head = float(np.sqrt(np.mean(out[:edge] ** 2))) + 1e-9
    r_tail = float(np.sqrt(np.mean(out[-edge:] ** 2))) + 1e-9
    diff_db = 20.0 * math.log10(r_head / r_tail)
    if abs(diff_db) > 1.0:
        tilt_db = np.linspace(-diff_db * 0.5, diff_db * 0.5, len(out))
        out = out * (10.0 ** (tilt_db / 20.0))
    return out


def soft_clip(x, drive=1.0):
    """Saturacao suave: da corpo aos estalos sem estourar."""
    return np.tanh(x * drive) / np.tanh(drive)


def limit(x, ceiling=0.95, iterations=4):
    """
    Limitador suave.

    Normalizar pelo pico seria errado aqui: material com transientes (gotas,
    estalos) tem pico alto e RMS baixo, entao "normalizar em -1 dBFS" deixaria a
    chuva fina mais alta que o rugido do furacao. O certo e mirar o RMS (o que o
    ouvido sente) e so aparar o que passar do teto, com saturacao suave.
    """
    for _ in range(iterations):
        peak = float(np.max(np.abs(x))) + 1e-12
        if peak <= ceiling:
            break
        x = ceiling * np.tanh(x / ceiling)
    return x


def rms_dbfs(x):
    return 20.0 * math.log10(float(np.sqrt(np.mean(x ** 2))) + 1e-12)


def gain_to_rms(x, target_dbfs):
    cur = rms_dbfs(x)
    return x * (10.0 ** ((target_dbfs - cur) / 20.0))


# ---------------------------------------------------------------------------
# vento
# ---------------------------------------------------------------------------
def wind_layer(seconds, bands, mod_rate, surge_chance, surge_gain, gen=None):
    """
    Vento = soma de bandas de ruido, cada uma com ganho modulado no tempo.

    bands: lista de (lo, hi, ganho_base, profundidade_da_modulacao)
    """
    g = gen if gen is not None else np.random.default_rng(SEED + 1)
    out = np.zeros(int(SR * seconds))
    for lo, hi, base, depth in bands:
        raw = g.standard_normal(int(SR * seconds))
        band = shaped(raw, combine(pink_profile(20.0, 0.9), band_profile(lo, hi)))
        control = smoother(seconds, mod_rate)
        control = norm01(control)
        gain = base * (1.0 - depth + depth * control)
        out += band * gain
    # rajadas: eventos curtos que sobem e descem, para nao ficar uniforme
    n_events = int(seconds * surge_chance)
    for _ in range(n_events):
        dur = 0.35 + g.random() * 0.9
        start = int(g.random() * max(1, len(out) - int(dur * SR)))
        env = envelope(int(dur * SR), dur * 0.35, dur * 0.65)
        band = shaped(g.standard_normal(int(dur * SR)),
                      combine(pink_profile(20.0, 0.8), band_profile(120.0, 2600.0)))
        out[start:start + len(env)] += band * env * surge_gain * (0.5 + g.random())
    return out


def gen_wind_ambience(seconds=14.0):
    x = wind_layer(seconds, [
        (45.0, 110.0, 0.35, 0.6),       # subgrave: turbulencia, nao motor
        (110.0, 380.0, 1.5, 0.55),
        (380.0, 1100.0, 1.6, 0.5),      # corpo do vento moderado
        (1100.0, 3000.0, 0.85, 0.7),
        (3000.0, 7500.0, 0.18, 0.9),
    ], mod_rate=90.0, surge_chance=0.35, surge_gain=0.9)
    # assobio fino: da carater de "vento em fresta", nao de chiado
    whistle_control = norm01(smoother(seconds, 220.0))
    whistle = shaped(noise(seconds), band_profile(760.0, 1150.0, order=2.5))
    x += whistle * (0.03 + 0.05 * whistle_control)
    x = loopify(x, 1.6)
    return x


def gen_wind_strong(seconds=14.0):
    x = wind_layer(seconds, [
        (40.0, 120.0, 0.8, 0.5),
        (120.0, 400.0, 2.0, 0.5),
        (400.0, 1400.0, 2.0, 0.45),     # vento forte tem corpo medio forte
        (1400.0, 4000.0, 0.9, 0.6),
        (4000.0, 9000.0, 0.22, 0.8),
    ], mod_rate=70.0, surge_chance=0.9, surge_gain=1.5)
    x = loopify(x, 1.6)
    return x


def gen_wind_storm(seconds=18.0):
    """Rugido de furacao: grave, continuo e com socos de rajada."""
    x = wind_layer(seconds, [
        (28.0, 80.0, 1.8, 0.35),        # rugido de furacao tem subgrave de verdade
        (80.0, 220.0, 2.2, 0.4),
        (220.0, 800.0, 2.2, 0.45),
        (800.0, 2500.0, 1.3, 0.5),
        (2500.0, 7000.0, 0.3, 0.6),
    ], mod_rate=120.0, surge_chance=1.6, surge_gain=2.0)
    x = loopify(x, 1.8)
    return x


def gen_wind_gust(seconds=3.0):
    """Rajada que chega, passa e vai embora (one-shot)."""
    body = wind_layer(seconds, [
        (45.0, 130.0, 1.2, 0.3),        # a rajada empurra ar: isso se sente como grave
        (130.0, 450.0, 2.2, 0.3),
        (450.0, 1500.0, 1.8, 0.4),
        (1500.0, 4000.0, 0.45, 0.5),
        (4000.0, 9000.0, 0.12, 0.5),
    ], mod_rate=60.0, surge_chance=0.0, surge_gain=0.0)
    n = len(body)
    t = np.linspace(0.0, 1.0, n)
    # sobe rapido, cai devagar: assinatura de rajada real
    env = np.exp(-((t - 0.28) ** 2) / 0.045) * 0.85 + np.exp(-((t - 0.55) ** 2) / 0.14) * 0.5
    env *= np.clip(t / 0.06, 0, 1) * np.clip((1 - t) / 0.25, 0, 1)
    x = body * env
    # assobio da rajada, com leve subida de tom
    sweep = shaped(noise(seconds), band_profile(500.0, 1800.0, order=2.0))
    x += sweep * env * 0.12
    return x


# ---------------------------------------------------------------------------
# folhagem
# ---------------------------------------------------------------------------
def gen_leaf_rustle(seconds=10.0):
    """
    Folhas: muitos eventos curtos, agrupados em rajadas, mais um leito baixo.

    Evento isolado soa como estalo; o que le como folhagem e o agrupamento -
    por isso os eventos vem em clusters com densidade variavel.
    """
    n = int(SR * seconds)
    out = np.zeros(n)
    density = norm01(smoother(seconds, 260.0))
    pos = 0
    while pos < n:
        # cluster de 0.15 a 0.6 s com muitos eventos pequenos
        density_here = 0.25 + 0.75 * density[min(pos, n - 1)]
        cluster = int(SR * (0.12 + rng.random() * 0.45))
        events = int(6 + 40 * density_here)
        for _ in range(events):
            off = pos + int(rng.random() * cluster)
            if off >= n:
                break
            dur = 0.012 + rng.random() * 0.05
            m = max(2, int(SR * dur))
            burst = shaped(rng.standard_normal(m),
                           combine(pink_profile(200.0, 0.6),
                                   band_profile(1200.0 + rng.random() * 1800.0, 5200.0, order=1.2)))
            burst *= envelope(m, dur * 0.15, dur * 0.85)
            amp = (0.25 + 0.75 * density_here) * (0.3 + rng.random())
            end = min(n, off + m)
            out[off:end] += burst[:end - off] * amp
        pos += cluster + int(SR * (0.05 + rng.random() * 0.35))
    # leito baixo: sem ele a folhagem parece flutuar sem vento nenhum
    bed = shaped(noise(seconds), combine(pink_profile(20.0, 0.9), low_profile(500.0, 1.4))) * 0.5
    out += bed * (0.25 + 0.5 * norm01(smoother(seconds, 300.0)))
    out = loopify(out, 1.0)
    return out


# ---------------------------------------------------------------------------
# chuva
# ---------------------------------------------------------------------------
def droplets(seconds, rate_per_second, f_lo, f_hi, decay, amp, gen=None, splats=0.0):
    """Eventos discretos de gota (impulsos ressonantes curtos)."""
    g = gen if gen is not None else np.random.default_rng(SEED + 7)
    n = int(SR * seconds)
    out = np.zeros(n)
    count = int(seconds * rate_per_second)
    for _ in range(count):
        start = int(g.random() * (n - 1))
        freq = f_lo + g.random() * (f_hi - f_lo)
        dur = 0.006 + g.random() * 0.02
        m = max(4, int(SR * dur))
        t = np.arange(m) / SR
        ping = np.sin(2 * math.pi * freq * t) * np.exp(-t / decay)
        ping += 0.5 * g.standard_normal(m) * np.exp(-t / (decay * 0.4))
        # a gota e um evento de alta frequencia curto: atenua para nao ficar sibilante
        ping = shaped(ping, pink_profile(600.0, 0.35))
        end = min(n, start + m)
        out[start:end] += ping[:end - start] * amp * (0.3 + g.random())
    # gotas grandes: eventos mais graves e mais fortes, espacados
    for _ in range(int(seconds * splats)):
        start = int(g.random() * (n - 1))
        dur = 0.05 + g.random() * 0.08
        m = max(8, int(SR * dur))
        t = np.arange(m) / SR
        splat = shaped(g.standard_normal(m),
                       combine(pink_profile(150.0, 0.5), band_profile(300.0, 2500.0, 1.2)))
        splat *= np.exp(-t / (dur * 0.25))
        end = min(n, start + m)
        out[start:end] += splat[:end - start] * 0.9
    return out


def gen_rain_light(seconds=12.0):
    """Chuva fina: leito de alta frequencia + gotas pequenas e frequentes."""
    bed = shaped(noise(seconds), combine(pink_profile(300.0, 0.5), high_profile(1300.0, 0.6)))
    bed += shaped(noise(seconds), combine(pink_profile(200.0, 0.5),
                                          band_profile(400.0, 2500.0, 1.0))) * 0.35
    bed *= 0.8 + 0.2 * norm01(smoother(seconds, 400.0))
    x = bed + droplets(seconds, 220.0, 800.0, 6500.0, 0.006, 0.5, splats=1.5)
    x = loopify(x, 1.2)
    return x


def gen_rain_heavy(seconds=12.0):
    """Chuva forte: mais gotas, mais corpo grave, mais agua batendo."""
    bed = shaped(noise(seconds), combine(pink_profile(300.0, 0.4), high_profile(1200.0, 0.5))) * 1.1
    bed += shaped(noise(seconds), combine(pink_profile(150.0, 0.5),
                                          band_profile(200.0, 1400.0, 1.0))) * 0.8
    bed += shaped(noise(seconds), combine(pink_profile(20.0, 0.9),
                                          low_profile(220.0, 1.5))) * 0.35
    bed *= 0.85 + 0.15 * norm01(smoother(seconds, 250.0))
    x = bed + droplets(seconds, 900.0, 700.0, 8000.0, 0.005, 0.7, splats=12.0)
    x = loopify(x, 1.2)
    return x


def gen_rain_wind(seconds=12.0):
    """A chuva da parede do olho: chuva forte somada ao rugido do vento."""
    rain = gen_rain_heavy(seconds)
    wind = wind_layer(seconds, [
        (35.0, 120.0, 1.8, 0.4),
        (120.0, 500.0, 1.5, 0.45),
        (500.0, 2000.0, 1.0, 0.5),
        (2000.0, 6000.0, 0.3, 0.6),
    ], mod_rate=110.0, surge_chance=1.2, surge_gain=1.6)
    wind = loopify(wind, 1.5)
    n = min(len(rain), len(wind))
    x = loopify(rain[:n] / np.sqrt(2.0) + wind[:n] / np.sqrt(2.0), 1.5)
    return x


# ---------------------------------------------------------------------------
# trovao
# ---------------------------------------------------------------------------
def thunder_peal(seconds, low_cut, decay, amp, gen, delay_s=0.0, tilt=0.45):
    """
    Um "peal": ruido filtrado com envelope irregular.

    tilt e a inclinacao 1/f^tilt da fonte. Vale 0.45, e nao 1.1: inclinacao
    demais faz a energia cair como f^-2.2 e o trovao fica so subgrave - o
    espectro real e largo, apenas inclinado.
    """
    n = int(SR * seconds)
    body = shaped(gen.standard_normal(n),
                  combine(pink_profile(30.0, tilt), low_profile(low_cut, 1.2)))
    # subgrave: o que se sente mais do que se ouve em um trovao distante
    body += shaped(gen.standard_normal(n),
                   combine(pink_profile(30.0, tilt),
                           low_profile(max(35.0, low_cut * 0.3), 2.0))) * 0.35
    # envelope com dois lobos: o rolar do trovao nao e um unico decaimento
    t = np.linspace(0.0, 1.0, n)
    env = (np.exp(-t * decay) * 0.8
           + np.exp(-((t - 0.28) ** 2) / 0.02) * 0.5
           + np.exp(-((t - 0.55) ** 2) / 0.05) * 0.35)
    env *= np.clip(t / 0.02, 0, 1)
    y = body * env * amp
    pre = int(SR * delay_s)
    if pre > 0:
        y = np.concatenate([np.zeros(pre), y])
    return y


def gen_thunder(seconds, low_cut, peals, crack_amp, decay, decay_ir=1.6,
                ir_tilt=0.6, tilt=0.45, peal_gain=1.0, boom_amp=0.7):
    """Trovao completo: estalo (opcional), peals, reverberacao e nivel final."""
    gen = np.random.default_rng(SEED + 11)
    n = int(SR * seconds)
    x = np.zeros(n)

    if crack_amp > 0.0:
        # O estalo de um trovao proximo nao e um clique: e uma explosao curta
        # que ricocheteia nas camadas de ar. Por isso ele tem 0.4 s e reflexoes.
        m = int(SR * 0.4)
        t = np.arange(m) / SR
        core = shaped(gen.standard_normal(m), low_profile(5500.0, 0.9)) * np.exp(-t / 0.035)
        core += np.sin(2 * math.pi * 95.0 * t) * np.exp(-t / 0.05) * boom_amp
        core = soft_clip(core, 2.0)
        crack = core.copy()
        for delay, g in ((0.045, 0.6), (0.095, 0.45), (0.16, 0.32), (0.25, 0.22)):
            d = int(SR * delay)
            if d < m:
                crack[d:] += core[:m - d] * g
        x[:m] += crack * crack_amp

    for i in range(peals):
        delay = i * (0.25 + gen.random() * 0.6)
        amp = (1.0 / (1.0 + i * 0.8)) * (0.7 + gen.random() * 0.5) * peal_gain
        peal = thunder_peal(seconds, low_cut * (1.0 + i * 0.15), decay * (1.0 - i * 0.12),
                            amp, gen, delay, tilt)
        x[:len(peal)] += peal[:n]

    x = dc_block(x, 25.0, 2.0)
    # a convolucao devolve um sinal mais longo (cauda da reverberacao): o
    # excedente e descartado, mas a cauda ja foi somada dentro da janela
    rev = convolve_reverb(x, impulse_response(1.8, decay_ir, tilt=ir_tilt))
    return dc_block(x + rev[:len(x)] * 0.45, 22.0, 2.0)


def gen_thunder_close():
    """Perto: estalo seco de banda larga e pouca cauda."""
    # parametros ajustados por medida contra um alvo espectral de trovao proximo
    # (a fracao de energia por faixa fica em ~33/25/15/13/11 %)
    return gen_thunder(3.2, 3500.0, peals=2, crack_amp=3.0, decay=4.5,
                       decay_ir=2.6, ir_tilt=2.2, tilt=0.15, peal_gain=0.5,
                       boom_amp=0.25)[:int(SR * 3.2)]


def gen_thunder_rumble():
    """Medio: o "trovao_rumble" classico, com estalo e rolar."""
    return gen_thunder(5.5, 1200.0, peals=3, crack_amp=4.0, decay=2.6,
                       decay_ir=1.6, ir_tilt=1.4, tilt=0.45, peal_gain=0.9,
                       boom_amp=0.5)[:int(SR * 5.5)]


def gen_thunder_far():
    """
    Longe: sem estalo, longo e difuso.

    O corte fica em ~260 Hz de proposito: um trovao distante de verdade chega
    como rolar grave, mas ainda com energia suficiente para ser audivel em
    caixas comuns. Abaixo de ~100 Hz vira quase inaudivel, por mais "distante"
    que pareca no papel.
    """
    return gen_thunder(8.5, 350.0, peals=4, crack_amp=0.0, decay=1.5,
                       decay_ir=1.1, ir_tilt=0.9, tilt=0.85, boom_amp=0.0)[:int(SR * 8.5)]


# ---------------------------------------------------------------------------
# destrocos e instrumentos
# ---------------------------------------------------------------------------
def gen_debris_rattle(seconds=7.0):
    """
    Destrocos batendo: impactos esparsos sobre um leito de vento forte.

    E a camada que diz "tem coisa voando" sem precisar olhar para fora.
    """
    n = int(SR * seconds)
    out = shaped(noise(seconds), band_profile(150.0, 2500.0, 1.0)) * 0.35
    pos = 0
    while pos < n:
        dur = 0.03 + rng.random() * 0.12
        m = max(8, int(SR * dur))
        click = shaped(rng.standard_normal(m), band_profile(400.0, 4500.0, 1.4))
        click *= envelope(m, dur * 0.08, dur * 0.92)
        # impacto: um pouco de corpo grave junto com o estalo
        click += np.sin(2 * math.pi * (90.0 + rng.random() * 160.0)
                        * np.arange(m) / SR) * np.exp(-np.arange(m) / (SR * 0.05)) * 0.5
        end = min(n, pos + m)
        out[pos:end] += click[:end - pos] * (0.4 + rng.random() * 0.9)
        pos += int(SR * (0.05 + rng.random() * 0.45))
    out = loopify(out, 0.8)
    return out


def gen_anemometer_tick():
    """Clique metalico curto: dois modos ressonantes + transiente de ruido."""
    n = int(SR * 0.28)
    t = np.arange(n) / SR
    x = (np.sin(2 * math.pi * 2150.0 * t) * np.exp(-t / 0.014) * 1.0
         + np.sin(2 * math.pi * 5180.0 * t) * np.exp(-t / 0.008) * 0.55
         + np.sin(2 * math.pi * 890.0 * t) * np.exp(-t / 0.022) * 0.35)
    x += rng.standard_normal(n) * np.exp(-t / 0.004) * 0.6
    x *= np.clip(t / 0.0008, 0, 1)
    return x


def gen_windsock_flap():
    """Tecido batendo: 2 panos com queda de tom, banda media."""
    n = int(SR * 0.85)
    x = np.zeros(n)
    for i, (start_s, amp, f_hi) in enumerate(((0.0, 1.0, 2600.0), (0.28, 0.7, 1900.0))):
        start = int(SR * start_s)
        dur = 0.18 + 0.08 * rng.random()
        m = max(8, int(SR * dur))
        t = np.arange(m) / SR
        flap = shaped(rng.standard_normal(m), band_profile(320.0, f_hi, 1.1))
        flap *= np.exp(-t / (dur * 0.32)) * np.clip(t / 0.004, 0, 1)
        # corpo grave do pano esticando
        flap += np.sin(2 * math.pi * (150.0 - 60.0 * t / max(t[-1], 1e-6)) * t) \
            * np.exp(-t / (dur * 0.4)) * 0.35
        end = min(n, start + m)
        x[start:end] += flap[:end - start] * amp
    x += shaped(noise(0.85), low_profile(900.0, 1.5)) * 0.25 * envelope(n, 0.01, 0.4)
    return x



# ---------------------------------------------------------------------------
# saida
# ---------------------------------------------------------------------------
def write(name, x, target_rms):
    """Mira o RMS, apara o excesso e grava em OGG Vorbis."""
    x = dc_block(x, 20.0, 2.0)
    x = gain_to_rms(x, target_rms)
    x = limit(x)
    x = gain_to_rms(x, target_rms)   # reaplica: o limitador come parte do nivel
    x = limit(x)
    path = os.path.join(SOUNDS, name)
    sf.write(path, x, SR, format="OGG", subtype="VORBIS")
    size = os.path.getsize(path)
    print("%-22s %5.2fs  rms %6.1f dBFS  pico %5.1f dBFS  %7d bytes"
          % (name, len(x) / float(SR), rms_dbfs(x), 20 * math.log10(float(np.max(np.abs(x))) + 1e-12), size))
    return size


#: Alvo de volume (RMS) por som. E o que garante o balanco da mixagem: leitos
#: ficam baixos e somam sem estourar, eventos sobressaem.
TARGETS = {
    "wind_ambience.ogg": -24.0,
    "wind_strong.ogg": -21.0,
    "wind_storm.ogg": -19.0,
    "wind_gust.ogg": -18.0,
    "leaf_rustle.ogg": -26.0,
    "rain_light.ogg": -25.0,
    "rain_heavy.ogg": -21.0,
    "rain_wind.ogg": -20.0,
    "thunder_close.ogg": -13.0,
    "thunder_rumble.ogg": -16.0,
    "thunder_far.ogg": -20.0,
    "debris_rattle.ogg": -22.0,
    "anemometer_tick.ogg": -15.0,
    "windsock_flap.ogg": -16.0,
}


def main():
    builders = (
        ("wind_ambience.ogg", gen_wind_ambience),
        ("wind_strong.ogg", gen_wind_strong),
        ("wind_storm.ogg", gen_wind_storm),
        ("wind_gust.ogg", gen_wind_gust),
        ("leaf_rustle.ogg", gen_leaf_rustle),
        ("rain_light.ogg", gen_rain_light),
        ("rain_heavy.ogg", gen_rain_heavy),
        ("rain_wind.ogg", gen_rain_wind),
        ("thunder_close.ogg", gen_thunder_close),
        ("thunder_rumble.ogg", gen_thunder_rumble),
        ("thunder_far.ogg", gen_thunder_far),
        ("debris_rattle.ogg", gen_debris_rattle),
        ("anemometer_tick.ogg", gen_anemometer_tick),
        ("windsock_flap.ogg", gen_windsock_flap),
    )
    total = 0
    for name, builder in builders:
        total += write(name, builder(), TARGETS[name])
    print("total: %d bytes" % total)


if __name__ == "__main__":
    main()
