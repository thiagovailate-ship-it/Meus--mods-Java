"""
DynamicWinds - prancha da geometria da tempestade (Python puro, sem dependencias).

Desenha, a partir das MESMAS formulas de `StormRenderer.java`/`StormKind.java`:

  1. quatro vistas de topo, uma por tipo de sistema (vortice, tropical, furacao e
     furacao maior), na mesma escala - o que mostra o quanto o olho, o raio do
     vento maximo e a borda externa crescem de um tipo para o outro;
  2. dois cortes verticais (furacao maior e vortice), com o teto de nuvem, o
     rodape, as paredes, as bandas de chuva e, no vortice, o funil inclinado;
  3. o perfil de alpha do teto, do rodape e das paredes em funcao do raio/altura.

Serve para conferir a forma sem abrir o jogo: nao ha renderizador OpenGL nesta
sandbox, entao aqui a geometria e verificada por desenho.

O mesmo arquivo roda uma **varredura de degeneracao**: para cada tipo, cada
intensidade e cada altura de terreno, checa as condicoes que o renderizador usa
para decidir se desenha (raio maior que o de dentro, altura positiva, raio
minimo). No Java essas condicoes sao checadas ANTES de abrir o lote do
Tessellator - o que importa porque `startDrawing` lanca excecao se ja houver lote
aberto.

Uso: python3 tools/preview_storm.py [--check]
     -> docs/storm_shape.png  (+ relatorio no terminal)
"""
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))

import gen_textures as gt   # Canvas, mix, blend de alpha - sem dependencia extra

OUT = os.path.join(ROOT, "docs/storm_shape.png")

# ---------------------------------------------------------------------------
# Constantes espelhadas de core/StormKind.java
# ---------------------------------------------------------------------------
VORTEX, TROPICAL, HURRICANE, MAJOR = 0, 1, 2, 3
KIND_NAMES = ["VORTEX", "TROPICAL", "HURRICANE", "MAJOR"]
VMAX = [28.0, 24.0, 46.0, 72.0]
RMAX = [9.0, 55.0, 85.0, 130.0]
ROUT = [150.0, 260.0, 400.0, 560.0]
TOP = [110.0, 150.0, 180.0, 210.0]

# Constantes espelhadas de client/storm/StormRenderer.java
DECK_FRACTION = 0.55
SKY_LIMIT = 214.0
DECK_DROP = 18.0
SKIRT_DROP = 24.0
BAND_ARMS = 3
BAND_STEPS = 22

CW, CH = 1240, 720          # tamanho da prancha
GROUND = 63.0               # altura de terreno usada nos desenhos

# cores
C_GROUND = (86, 70, 58, 255)
C_SKY = (16, 19, 28, 255)
C_DECK = (108, 120, 146, 255)
C_DECK_EDGE = (196, 206, 224, 255)
C_WALL = (132, 148, 176, 255)
C_SKIRT = (96, 110, 134, 255)
C_SHELL = (176, 186, 208, 255)
C_BAND = (104, 176, 214, 255)
C_EYE = (10, 10, 16, 255)
C_ARM = [C_BAND, (86, 154, 196, 255), (124, 196, 226, 255)]
C_TEXT = (188, 196, 212, 255)
C_LINE = (62, 70, 88, 255)
C_A_DECK = (196, 206, 224, 255)
C_A_SKIRT = (96, 110, 134, 255)
C_A_WALL = (132, 148, 176, 255)
C_A_BAND = (104, 176, 214, 255)


# ---------------------------------------------------------------------------
# Modelo (espelho do Java)
# ---------------------------------------------------------------------------
def smoothstep(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3.0 - 2.0 * t)


def intensity_at(t01):
    """Curva de intensidade do StormSystem, copiada termo a termo."""
    t = max(0.0, min(1.0, t01))
    if t < 0.15:
        return smoothstep(0.0, 0.15, t) * 0.55
    if t < 0.45:
        return 0.55 + smoothstep(0.15, 0.45, t) * 0.45
    if t < 0.75:
        dip = 0.10 * math.exp(-((t - 0.60) / 0.05) ** 2)
        return 1.0 - dip
    return 1.0 - smoothstep(0.75, 1.0, t)


def rmax_at(kind, t01):
    return RMAX[kind] * (1.0 + 0.55 * (1.0 - intensity_at(t01)))


def geometry(kind, intensity, ground):
    """Toda a geometria de um sistema, igual a drawSystem()."""
    narrow = kind == VORTEX
    rmax = RMAX[kind] * (1.0 + 0.55 * (1.0 - intensity))
    outer = ROUT[kind]
    base = ground - 1.0 if narrow else ground + 2.0
    deck = min(ground + TOP[kind] * DECK_FRACTION, SKY_LIMIT)
    if deck <= base + 2.0:
        deck = base + 2.0
    g = {
        "kind": kind, "intensity": intensity, "narrow": narrow,
        "rmax": rmax, "outer": outer, "base": base, "deck": deck, "ground": ground,
        "deck_in": rmax * (1.35 if narrow else 0.9),
        "deck_out": outer * 1.04,
        "shell_y": min(deck + TOP[kind] * 0.18, SKY_LIMIT + 26.0),
        "shell_in": rmax * 1.05,
        "shell_out": outer * 1.16,
        "skirt_bottom": deck - SKIRT_DROP * (0.55 + 0.45 * intensity),
        "skirt_top_radius": rmax * (1.35 if narrow else 0.9),
        "skirt_bottom_radius": rmax * (1.35 if narrow else 0.9) * 1.04,
        "wall": [] if narrow else [(rmax * 1.02, 0.60), (rmax * 1.45, 0.26)],
        "band_in": rmax * 6.0 if narrow else rmax * 1.15,
        "band_out": outer * 1.02,
        "band_bottom": ground + 2.0,
        "band_top": max(ground + 6.0, deck - 30.0),
        "funnel_top": rmax * 2.6,
        "funnel_bottom": rmax * 0.42,
        "funnel_height": deck - ground,
        "lean": rmax * 0.55,
    }
    return g


def alpha_deck(f):
    """Mesma curva do renderizador: zero no olho, zero na borda."""
    return 0.92 * min(1.0, f / 0.12) * min(1.0, (1.0 - f) / 0.35)


# ---------------------------------------------------------------------------
# Varredura das condicoes de desenho
# ---------------------------------------------------------------------------
def verify():
    """
    Confere as condicoes de desenho em todas as combinacoes.

    A distincao que importa: um caso pode cair numa GUARDA (o renderizador
    decide nao desenhar aquela peca, e o faz antes de abrir o lote - sem risco)
    ou ser um PROBLEMA de verdade (geometria invertida ou nula). So o segundo e
    erro; o primeiro e a guarda fazendo o trabalho dela.
    """
    guards = {}
    problems = []
    cases = 0
    for kind in (VORTEX, TROPICAL, HURRICANE, MAJOR):
        for t01 in (0.02, 0.1, 0.2, 0.3, 0.45, 0.6, 0.75, 0.9, 0.99):
            intensity = max(intensity_at(t01), 0.03)
            for ground in (4.0, 20.0, 63.0, 100.0, 150.0, 200.0, 245.0):
                g = geometry(kind, intensity, ground)
                cases += 1
                tag = "%s I=%.2f solo=%.0f" % (KIND_NAMES[kind], intensity, ground)

                def guard(name, condition):
                    if condition:
                        guards[name] = guards.get(name, 0) + 1
                        return True
                    return False

                # problemas de verdade: geometria impossivel
                if g["deck_out"] <= g["deck_in"]:
                    problems.append(tag + ": teto sem anel")
                if not g["narrow"] and g["band_out"] <= g["band_in"]:
                    problems.append(tag + ": bandas sem anel")
                if g["narrow"] and g["funnel_top"] <= g["funnel_bottom"]:
                    problems.append(tag + ": funil invertido")
                if g["narrow"] and g["funnel_bottom"] <= 0.2:
                    problems.append(tag + ": funil com base nula")

                # guardas (decisao de nao desenhar, sempre antes do lote)
                guard("parede sem altura", g["deck"] <= g["base"] + 1.0)
                guard("rodape sem altura", g["deck"] - g["skirt_bottom"] <= 1.0)
                guard("rodape com raio nulo", g["skirt_top_radius"] <= 0.5)
                guard("banda com menos de 4 blocos", g["band_top"] - g["band_bottom"] < 4.0)
                if g["narrow"]:
                    guard("funil sem altura (teto limitado pelo mundo)",
                          g["funnel_height"] <= 2.0)
                else:
                    for radius, _ in g["wall"]:
                        guard("parede com raio nulo", radius <= 0.5)
    return cases, guards, problems


# ---------------------------------------------------------------------------
# Desenho
# ---------------------------------------------------------------------------
def top_view(cv, x0, y0, size, kind, intensity=1.0):
    """Vista de topo: teto (anel), paredes, bandas espirais, olho e deslocamento."""
    g = geometry(kind, intensity, GROUND)
    reach = g["shell_out"] * 1.04
    scale = (size * 0.5 - 8.0) / reach
    cx = x0 + size * 0.5
    cy = y0 + size * 0.5

    def px(r, a):
        return cx + math.cos(a) * r * scale, cy + math.sin(a) * r * scale

    # borda externa do sistema
    cv.ring(cx, cy, g["outer"] * scale, 1.0, (58, 66, 84, 255), soft=1.0)

    # teto de nuvem: uma passada de pixels, com o alpha da curva do renderer.
    # (Fazer isso com aneis desenhados um a um varreria a caixa toda a cada anel;
    # aqui cada pixel descobre o proprio raio uma vez.)
    r_out_px = g["deck_out"] * scale
    r_in_px = g["deck_in"] * scale
    span = int(r_out_px) + 2
    for yy in range(int(cy - span), int(cy + span) + 1):
        for xx in range(int(cx - span), int(cx + span) + 1):
            d = math.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
            if d < r_in_px or d > r_out_px:
                continue
            f = (d - r_in_px) / max(1.0, r_out_px - r_in_px)
            a = alpha_deck(f)
            if a <= 0.02:
                continue
            shade = 0.55 + 0.45 * (1.0 - f)
            edge = 1.0 if d < r_out_px - 1.5 else 0.35
            cv.blend(xx, yy, (int(C_DECK[0] * shade), int(C_DECK[1] * shade),
                              int(C_DECK[2] * shade), 255), a * 0.92 * edge)

    # paredes do olho
    for radius, alpha in g["wall"]:
        cv.ring(cx, cy, radius * scale, 2.2, (C_WALL[0], C_WALL[1], C_WALL[2], int(alpha * 255)), soft=1.0)

    # bandas espirais (mesma espiral do renderizador)
    r_in, r_out = g["band_in"], g["band_out"]
    if r_out > r_in:
        for arm in range(BAND_ARMS):
            base = (arm / float(BAND_ARMS)) * math.tau
            prev = None
            for step in range(BAND_STEPS * 2):
                f = step / float(BAND_STEPS * 2)
                r = r_in + (r_out - r_in) * f
                s = base + math.log(r / r_in) * 1.6
                p = px(r, s)
                if prev is not None:
                    a = int(40 + 150 * (1.0 - f))
                    col = C_ARM[arm % len(C_ARM)]
                    cv.line(prev[0], prev[1], p[0], p[1], (col[0], col[1], col[2], a),
                            thick=2.0 + 3.0 * (0.3 + f))
                prev = p

    # olho: aberto de proposito
    cv.disc(cx, cy, max(1.5, g["rmax"] * scale * 0.9), (0, 0, 0, 90), soft=1.0)

    # sentido de rotacao (hemisferio norte) e deslocamento
    ar = g["outer"] * 0.72 * scale
    for k in range(28):
        t = k / 28.0
        a = -0.5 + t * 1.6
        p = px(g["outer"] * (0.55 + 0.16 * t), a)
        cv.disc(p[0], p[1], 1.6, (150, 220, 190, 200), soft=1.0)
    tx, ty = px(0, 0)
    cv.line(tx, ty, tx + ar * 0.55, ty, (236, 196, 120, 230), thick=2.0)
    cv.disc(tx + ar * 0.55, ty, 3.0, (236, 196, 120, 240), soft=1.0)
    return g


def cross_section(cv, x0, y0, w, h, kind, intensity, ground):
    """Corte vertical: solo, paredes, rodape, teto, camada alta e funil."""
    g = geometry(kind, intensity, ground)
    top = max(g["shell_y"], g["deck"]) + 12.0
    y_min = ground - 10.0
    # Escalas independentes por eixo, com exagero vertical: um sistema de ~560
    # blocos de largura por ~175 de altura ficaria achatado numa prancha normal,
    # e o que interessa aqui e ver a estrutura vertical (teto, rodape, funil).
    sx = (w - 46.0) / (g["outer"] * 1.24)
    sy = (h - 46.0) / (top - y_min)
    ox = x0 + 40.0
    oy = y0 + h - 24.0

    def px(r, y):
        return ox + r * sx, oy - (y - y_min) * sy

    # ceu
    cv.rect(x0, y0, x0 + w, y0 + h, C_SKY)

    # solo
    gy0 = px(0, ground)
    gy1 = px(g["outer"] * 1.18, ground)
    cv.line(gy0[0], gy0[1], gy1[0], gy1[1], C_GROUND, thick=3.0)
    cv.rect(int(gy0[0]), int(gy0[1]) + 1, int(gy1[0]), y0 + h, (60, 48, 40, 255))

    # teto: do raio interno ate a borda, caindo 18*f^2
    rings = 28
    for i in range(rings):
        f0 = i / float(rings)
        f1 = (i + 1) / float(rings)
        r0 = g["deck_in"] + (g["deck_out"] - g["deck_in"]) * f0
        r1 = g["deck_in"] + (g["deck_out"] - g["deck_in"]) * f1
        y0d = g["deck"] - f0 * f0 * DECK_DROP
        y1d = g["deck"] - f1 * f1 * DECK_DROP
        p0 = px(r0, y0d)
        p1 = px(r1, y1d)
        a = alpha_deck(f0)
        cv.line(p0[0], p0[1], p1[0], p1[1], (C_DECK_EDGE[0], C_DECK_EDGE[1], C_DECK_EDGE[2],
                                              int(230 * max(0.25, a))), thick=3.0)
        for xx in range(int(p0[0]), int(p1[0]) + 1):
            cv.blend(xx, int(p0[1] - 1), (C_DECK[0], C_DECK[1], C_DECK[2], int(150 * a)), 1.0)
            cv.blend(xx, int(p0[1]), (C_DECK[0], C_DECK[1], C_DECK[2], int(200 * a)), 1.0)

    # camada alta
    if intensity > 0.35:
        p0 = px(g["shell_in"], g["shell_y"])
        p1 = px(g["shell_out"], g["shell_y"])
        cv.line(p0[0], p0[1], p1[0], p1[1], (C_SHELL[0], C_SHELL[1], C_SHELL[2], 150), thick=2.0)

    # rodape na borda interna do teto
    p_top = px(g["skirt_top_radius"], g["deck"])
    p_bot = px(g["skirt_bottom_radius"], g["skirt_bottom"])
    cv.line(p_top[0], p_top[1], p_bot[0], p_bot[1], (C_SKIRT[0], C_SKIRT[1], C_SKIRT[2], 220), thick=3.0)

    # paredes do olho (ou funil)
    if g["narrow"]:
        bands = 16
        for i in range(bands):
            f0 = i / float(bands)
            f1 = (i + 1) / float(bands)
            r0 = g["funnel_bottom"] + (g["funnel_top"] - g["funnel_bottom"]) * (f0 ** 0.75)
            r1 = g["funnel_bottom"] + (g["funnel_top"] - g["funnel_bottom"]) * (f1 ** 0.75)
            y0f = ground + g["funnel_height"] * (1.0 - f0)
            y1f = ground + g["funnel_height"] * (1.0 - f1)
            l0 = g["lean"] * (1.0 - f0)
            l1 = g["lean"] * (1.0 - f1)
            p0 = px(r0 + l0, y0f)
            p1 = px(r1 + l1, y1f)
            cv.line(p0[0], p0[1], p1[0], p1[1], (C_WALL[0], C_WALL[1], C_WALL[2], 235), thick=3.0)
    else:
        for radius, alpha in g["wall"]:
            p0 = px(radius, g["base"])
            p1 = px(radius, g["deck"])
            cv.line(p0[0], p0[1], p1[0], p1[1],
                    (C_WALL[0], C_WALL[1], C_WALL[2], int(90 + 140 * alpha)), thick=2.0)

    # bandas de chuva: cortinas verticais na espiral
    r_in, r_out = g["band_in"], g["band_out"]
    for arm in range(BAND_ARMS):
        base = (arm / float(BAND_ARMS)) * math.tau
        for step in range(0, BAND_STEPS, 2):
            f = step / float(BAND_STEPS)
            r = r_in + (r_out - r_in) * f
            s = base + math.log(r / r_in) * 1.6
            rr = r * math.cos(s)
            if rr < 0:
                continue
            t0 = g["band_top"] - f * 8.0
            p0 = px(rr, g["band_bottom"])
            p1 = px(rr, t0)
            cv.line(p0[0], p0[1], p1[0], p1[1], (C_BAND[0], C_BAND[1], C_BAND[2], 70), thick=1.0)

    # eixo e marcas de altura
    ax = px(0, y_min)
    cv.line(ax[0], ax[1], ax[0], px(0, top)[1], (70, 78, 96, 255), thick=1.0)
    for i in range(0, int(g["outer"] * 1.2) + 1, 100):
        p = px(i, ground)
        cv.line(p[0], p[1], p[0], p[1] + 3, (90, 98, 116, 255), thick=1.0)
    for i in range(0, int(top) + 1, 50):
        p = px(0, i)
        cv.line(p[0] - 3, p[1], p[0], p[1], (90, 98, 116, 255), thick=1.0)
    return g


def alpha_plot(cv, x0, y0, w, h):
    """Perfil de alpha: teto (por raio) e paredes (por altura)."""
    for yy in range(y0, y0 + h):
        for xx in range(x0, x0 + w):
            cv.blend(xx, yy, (12, 15, 22, 255), 1.0)
    ox, oy = x0 + 40, y0 + h - 26
    pw, ph = w - 60, h - 50
    for i in range(5):
        yy = oy - ph * i / 4.0
        cv.line(ox, yy, ox + pw, yy, C_LINE, thick=1.0)
        xx = ox + pw * i / 4.0
        cv.line(xx, oy, xx, oy - ph, C_LINE, thick=1.0)
    # alpha do teto em funcao do raio normalizado
    pts = []
    for i in range(181):
        f = i / 180.0
        a = alpha_deck(f)
        pts.append((ox + pw * f, oy - ph * a))
    for i in range(len(pts) - 1):
        cv.line(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], C_A_DECK, thick=2.0)
    # alpha das paredes em funcao da altura (base -> topo)
    pts = []
    for i in range(61):
        t = i / 60.0
        a = (0.60 * (0.75 + 0.25 * 1.0)) * (1.0 - t) + (0.60 * 0.5 * (0.6 + 0.4)) * t
        pts.append((ox + pw * t, oy - ph * a))
    for i in range(len(pts) - 1):
        cv.line(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], C_A_WALL, thick=2.0)
    # alpha do rodape (cai com a altura)
    pts = []
    for i in range(61):
        t = i / 60.0
        a = 0.40 * (1.0 - t) + 0.65 * t
        pts.append((ox + pw * t, oy - ph * a))
    for i in range(len(pts) - 1):
        cv.line(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], C_A_SKIRT, thick=2.0)
    # alpha das bandas (cai com o raio)
    pts = []
    for i in range(61):
        t = i / 60.0
        a = 0.26 * (1.0 - t) * 1.0
        pts.append((ox + pw * t, oy - ph * a))
    for i in range(len(pts) - 1):
        cv.line(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], C_A_BAND, thick=2.0)


def main():
    cases, guards, problems = verify()
    print("varredura de geometria: %d combinacoes (tipo x intensidade x solo)" % cases)
    for name in sorted(guards):
        print("  guarda   %-42s %3d casos" % (name, guards[name]))
    if problems:
        for pr in problems:
            print("  PROBLEMA %s" % pr)
    else:
        print("  nenhum problema de geometria (as guardas cobrem o resto)")

    cv = gt.Canvas(CW, CH)
    for y in range(CH):
        for x in range(CW):
            cv.put(x, y, (10, 12, 18, 255))

    # linha 1: vistas de topo, mesma escala
    panel = 288
    for i, kind in enumerate((VORTEX, TROPICAL, HURRICANE, MAJOR)):
        x0 = 12 + i * (panel + 8)
        y0 = 12
        cv.rect(x0, y0, x0 + panel, y0 + panel, (14, 17, 26, 255))
        g = top_view(cv, x0, y0, panel, kind, 1.0)
        print("%-10s rmax=%6.1f  teto=%6.1f..%6.1f  %s=%6.1f/%6.1f  bandas=%6.1f..%6.1f"
              % (KIND_NAMES[kind], g["rmax"], g["deck_in"], g["deck_out"],
                 "funil" if g["wall"] == [] else "parede",
                 g["funnel_top"] if g["wall"] == [] else g["wall"][0][0],
                 g["funnel_bottom"] if g["wall"] == [] else g["wall"][1][0],
                 g["band_in"], g["band_out"]))
        print("           teto em y=%.0f  rodape desce ate y=%.0f  (solo em %.0f)"
              % (g["deck"], g["skirt_bottom"], GROUND))

    # linha 2: dois cortes verticais e o grafico de alpha
    y1 = panel + 24
    cross_section(cv, 12, y1, 470, CH - y1 - 12, MAJOR, 1.0, GROUND)
    cross_section(cv, 492, y1, 330, CH - y1 - 12, VORTEX, 1.0, GROUND)
    alpha_plot(cv, 832, y1, CW - 832 - 12, CH - y1 - 12)

    n = cv.to_png(OUT)
    print("prancha: %s (%d bytes)" % (os.path.relpath(OUT, ROOT), n))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
