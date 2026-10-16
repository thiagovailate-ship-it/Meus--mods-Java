#!/usr/bin/env python3
"""
DynamicWinds - gerador de texturas (Python puro, sem dependencias).

Gera as texturas de blocos (128x128, atlas 4x4 de celulas de 32px) e as
texturas de particulas usadas pelo mod.

Por que um gerador em vez de PNGs editados a mao?
  * reproduzivel: `python3 tools/gen_textures.py` recria exatamente os mesmos bytes
  * sem dependencia de Pillow/numpy, funciona em qualquer CI
  * as celulas do atlas ficam alinhadas com as constantes de UV em
    src/main/java/com/dynamicwinds/client/render/tesr/AtlasCells.java

Layout do atlas 4x4 (coluna, linha) - linha 0 = TOPO da imagem:
  A convencao do OpenGL e v=0 embaixo, mas a imagem PNG e guardada de cima
  para baixo. AtlasCells faz a conversao (v0 = 1 - (row+1)*0.25).
"""

import math
import os
import struct
import zlib

# Single source for the seam threshold consumed by tools/check_seams.py.
SEAM_RATIO_LIMIT = 1.6

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCKS = os.path.join(ROOT, "src/main/resources/assets/dynamicwinds/textures/blocks")
PARTICLES = os.path.join(ROOT, "src/main/resources/assets/dynamicwinds/textures/particle")
ITEMS = os.path.join(ROOT, "src/main/resources/assets/dynamicwinds/textures/items")
ENVIRONMENT = os.path.join(ROOT, "src/main/resources/assets/dynamicwinds/textures/environment")


def _tileable_noise(cv, x, y, scale, period, s=0):
    """Value noise periodico: a textura repete sem costura visivel.

    O ruido comum de value noise nao repete, e o teto de nuvem e desenhado como
    varias repeticoes da mesma textura - sem periodicidade apareceriam emendas
    retas atravessando o ceu.
    """
    fx, fy = x / scale, y / scale
    x0, y0 = int(fx // 1), int(fy // 1)
    tx, ty = fx - x0, fy - y0
    tx = tx * tx * (3 - 2 * tx)
    ty = ty * ty * (3 - 2 * ty)

    def h(ix, iy):
        return cv.hash01(ix % period, iy % period, s)

    a = h(x0, y0)
    b = h(x0 + 1, y0)
    c = h(x0, y0 + 1)
    d = h(x0 + 1, y0 + 1)
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def _fbm(cv, x, y, base_scale, size, octaves=4, s=0):
    """
    Soma de oitavas de ruido periodico (fractal brownian motion).

    A escala de cada oitava e ajustada para que o periodo caia num numero
    inteiro de celulas dentro de `size`. Sem isso o ruido nao fecha o tile: um
    periodo de 3 celulas de 20 px cobre 60 px de uma textura de 64, e a emenda
    aparece como uma linha reta atravessando o ceu ou a parede. O ajuste move o
    tamanho da celula em poucos por cento (24 px viram 25,6) e em troca garante
    que a primeira e a ultima coluna (e linha) sejam vizinhas de verdade.
    """
    total = 0.0
    norm = 0.0
    amp = 1.0
    scale = base_scale
    for o in range(octaves):
        period = max(1, int(round(size / scale)))
        snapped = size / float(period)
        total += amp * _tileable_noise(cv, x, y, snapped, period, s + o * 17)
        norm += amp
        amp *= 0.5
        scale *= 0.5
    return total / norm


def _streak_noise(cv, x, y, cells_x, cells_y, s=0):
    """
    Ruido periodico anisotropico: `cells_x` celulas na horizontal e `cells_y` na
    vertical, cobrindo a textura inteira.

    E o que produz rajas alongadas verticalmente (poucas celulas em y) que ainda
    fecham o tile nas duas direcoes. Comprimir o eixo y de um ruido quadrado -
    o que era feito antes, com `y * 0.22` - alonga a raja, mas a distancia entre
    as celulas deixa de dividir a altura da textura, e a parede ganha uma
    costura horizontal a cada repeticao.
    """
    fx = x * cells_x / float(cv.w)
    fy = y * cells_y / float(cv.h)
    x0, y0 = int(fx // 1), int(fy // 1)
    tx, ty = fx - x0, fy - y0
    tx = tx * tx * (3 - 2 * tx)
    ty = ty * ty * (3 - 2 * ty)
    a = cv.hash01(x0 % cells_x, y0 % cells_y, s)
    b = cv.hash01((x0 + 1) % cells_x, y0 % cells_y, s)
    c = cv.hash01(x0 % cells_x, (y0 + 1) % cells_y, s)
    d = cv.hash01((x0 + 1) % cells_x, (y0 + 1) % cells_y, s)
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty



# --------------------------------------------------------------------------
# Canvas RGBA simples
# --------------------------------------------------------------------------
class Canvas:
    def __init__(self, w, h):
        self.w = w
        self.h = h
        self.px = [[(0, 0, 0, 0) for _ in range(w)] for _ in range(h)]

    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def put(self, x, y, c):
        if self.inside(x, y):
            self.px[y][x] = c

    def get(self, x, y):
        if self.inside(x, y):
            return self.px[y][x]
        return (0, 0, 0, 0)

    def blend(self, x, y, c, a=1.0):
        """Blend src-over com alpha explicito."""
        if not self.inside(x, y) or a <= 0:
            return
        sr, sg, sb, sa = c
        sa = sa / 255.0 * a
        if sa <= 0:
            return
        dr, dg, db, da = self.px[y][x]
        da = da / 255.0
        oa = sa + da * (1 - sa)
        if oa <= 0:
            self.px[y][x] = (0, 0, 0, 0)
            return
        r = (sr * sa + dr * da * (1 - sa)) / oa
        g = (sg * sa + dg * da * (1 - sa)) / oa
        b = (sb * sa + db * da * (1 - sa)) / oa
        self.px[y][x] = (int(r + 0.5), int(g + 0.5), int(b + 0.5), int(oa * 255 + 0.5))

    # ---- formas -----------------------------------------------------------
    def rect(self, x0, y0, x1, y1, c):
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.put(x, y, c)

    def disc(self, cx, cy, r, c, soft=0.75):
        """Disco antialiasado por cobertura (distancia ao centro)."""
        for y in range(int(cy - r - 2), int(cy + r + 2) + 1):
            for x in range(int(cx - r - 2), int(cx + r + 2) + 1):
                d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
                cov = (r - d) / soft + 0.5
                cov = max(0.0, min(1.0, cov))
                if cov > 0:
                    self.blend(x, y, c, cov)

    def ring(self, cx, cy, r, thick, c, soft=0.75):
        for y in range(int(cy - r - thick - 2), int(cy + r + thick + 2) + 1):
            for x in range(int(cx - r - thick - 2), int(cx + r + thick + 2) + 1):
                d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
                cov = (thick * 0.5 - abs(d - r)) / soft + 0.5
                cov = max(0.0, min(1.0, cov))
                if cov > 0:
                    self.blend(x, y, c, cov)

    def line(self, x0, y0, x1, y1, c, thick=1.0):
        """Linha (Bresenham com espessura por stamp de disco)."""
        dx, dy = x1 - x0, y1 - y0
        n = int(max(abs(dx), abs(dy))) + 1
        for i in range(n + 1):
            t = i / n if n else 0
            self.disc(x0 + dx * t, y0 + dy * t, thick * 0.5, c, soft=0.6)

    def triangle(self, p0, p1, p2, c):
        """Triangulo por half-plane test (com AA leve via borda suave)."""
        xs = [p0[0], p1[0], p2[0]]
        ys = [p0[1], p1[1], p2[1]]
        for y in range(int(min(ys)) - 1, int(max(ys)) + 2):
            for x in range(int(min(xs)) - 1, int(max(xs)) + 2):
                px, py = x + 0.5, y + 0.5
                d1 = (px - p1[0]) * (p0[1] - p1[1]) - (p0[0] - p1[0]) * (py - p1[1])
                d2 = (px - p2[0]) * (p1[1] - p2[1]) - (p1[0] - p2[0]) * (py - p2[1])
                d3 = (px - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (py - p0[1])
                neg = (d1 < 0) or (d2 < 0) or (d3 < 0)
                pos = (d1 > 0) or (d2 > 0) or (d3 > 0)
                if not (neg and pos):
                    self.blend(x, y, c, 1.0)

    # ---- utilitarios deterministicos -------------------------------------
    @staticmethod
    def hash01(x, y, s=0):
        """Hash determinista -> [0,1). Nao usa random() para ser reproduzivel."""
        n = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
        n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
        return ((n ^ (n >> 16)) & 0xFFFFFF) / float(0xFFFFFF)

    def value_noise(self, x, y, scale, s=0):
        """Value noise bilinear (grade scale x scale)."""
        fx, fy = x / scale, y / scale
        x0, y0 = int(fx // 1), int(fy // 1)
        tx, ty = fx - x0, fy - y0
        tx = tx * tx * (3 - 2 * tx)
        ty = ty * ty * (3 - 2 * ty)
        a = self.hash01(x0, y0, s)
        b = self.hash01(x0 + 1, y0, s)
        c = self.hash01(x0, y0 + 1, s)
        d = self.hash01(x0 + 1, y0 + 1, s)
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty

    # ---- PNG --------------------------------------------------------------
    def to_png(self, path):
        raw = bytearray()
        for y in range(self.h):
            raw.append(0)  # filtro "None" por scanline
            for x in range(self.w):
                r, g, b, a = self.px[y][x]
                raw += bytes((r, g, b, a))

        def chunk(tag, data):
            out = struct.pack(">I", len(data)) + tag + data
            return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        ihdr = struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0)
        png = (b"\x89PNG\r\n\x1a\n"
               + chunk(b"IHDR", ihdr)
               + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
               + chunk(b"IEND", b""))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as fh:
            fh.write(png)
        return len(png)


# --------------------------------------------------------------------------
# Helpers de desenho "material"
# --------------------------------------------------------------------------
def mix(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t + 0.5) for i in range(len(c1)))


def shade(c, f, a=None):
    r, g, b = c[0], c[1], c[2]
    out = (max(0, min(255, int(r * f))), max(0, min(255, int(g * f))), max(0, min(255, int(b * f))))
    return (out[0], out[1], out[2], c[3] if a is None else a)


def brushed_metal(cv, x0, y0, size, base, light, dark, seed=0, streaks=26, vertical=True):
    """Metal escovado: gradiente + estrias finas + graos."""
    for y in range(y0, y0 + size):
        for x in range(x0, x0 + size):
            t = (y - y0) / float(size - 1)
            c = mix(light, dark, t)
            # estrias
            k = x if vertical else y
            s = cv.hash01(k, seed, 7)
            if s > 0.82:
                c = mix(c, light, 0.16)
            elif s < 0.16:
                c = mix(c, dark, 0.18)
            # grao fino
            g = cv.value_noise(x, y, 2.6, seed + 3)
            c = shade(c, 0.95 + g * 0.10)
            cv.put(x, y, (c[0], c[1], c[2], 255))


def bevel(cv, x0, y0, size, base, strength=0.30, light=1.0, dark=1.0, inset=1):
    """Escurece a borda e clareia o topo -> leitura de volume em 16/32px."""
    for i in range(inset):
        for x in range(x0, x0 + size):
            cv.blend(x, y0 + i, shade(base, 1 - strength * dark), 0.55)
            cv.blend(x, y0 + size - 1 - i, shade(base, 1 - strength * dark * 0.7), 0.45)
        for y in range(y0, y0 + size):
            cv.blend(x0 + i, y, shade(base, 1 - strength * dark), 0.5)
            cv.blend(x0 + size - 1 - i, y, shade(base, 1 - strength * dark), 0.5)
    for x in range(x0 + inset, x0 + size - inset):
        cv.blend(x, y0 + inset, shade(base, 1 + strength * light), 0.5)


def rivets(cv, x0, y0, size, base, positions, r=1.35):
    for (rx, ry) in positions:
        cv.disc(x0 + rx, y0 + ry, r + 0.8, shade(base, 0.62, 255), soft=1.0)
        cv.disc(x0 + rx, y0 + ry - 0.35, r, shade(base, 1.28, 255), soft=1.0)


def weave(cv, x0, y0, size, base, seed=0, strength=0.11, step=2):
    """Trama de tecido: linhas horizontais/verticais alternadas."""
    for y in range(y0, y0 + size):
        for x in range(x0, x0 + size):
            over = ((x - x0) // step + (y - y0) // step) % 2 == 0
            f = 1 + (strength if over else -strength)
            n = cv.value_noise(x, y, 3.4, seed + 11)
            f *= 0.97 + n * 0.07
            c = shade(base, f)
            cv.put(x, y, (c[0], c[1], c[2], 255))


def fill_cell(cv, col, row, size=32):
    return col * size, row * size, size


# --------------------------------------------------------------------------
# Atlas 1 - Cata-vento (wind vane)
# --------------------------------------------------------------------------
def gen_wind_vane():
    cv = Canvas(128, 128)
    STEEL = (126, 133, 140, 255)
    STEEL_HI = (196, 204, 212, 255)
    STEEL_LO = (58, 63, 70, 255)
    CHARCOAL = (56, 60, 66, 255)
    BRASS = (196, 152, 66, 255)
    PLATE = (208, 205, 194, 255)
    RED = (168, 58, 52, 255)

    # (0,0) POST - metal escovado vertical
    x, y, s = fill_cell(cv, 0, 0)
    brushed_metal(cv, x, y, s, STEEL, STEEL_HI, STEEL_LO, seed=1)
    bevel(cv, x, y, s, STEEL, strength=0.55)
    rivets(cv, x, y, s, STEEL, [(7, 6), (24, 6), (7, 25), (24, 25)])
    # sombra em um lado (leitura de cilindro) + faixa especular
    for yy in range(y, y + s):
        for xx in range(x, x + 5):
            cv.blend(xx, yy, shade(STEEL, 0.62), 0.34)
        for xx in range(x + 8, x + 12):
            cv.blend(xx, yy, shade(STEEL_HI, 1.06), 0.30)

    # (1,0) POST_CAP - topo do poste com parafuso
    x, y, s = fill_cell(cv, 1, 0)
    brushed_metal(cv, x, y, s, STEEL_LO, STEEL, shade(STEEL_LO, 0.7), seed=2, streaks=18)
    bevel(cv, x, y, s, STEEL_LO, strength=0.5)
    cv.disc(x + 16, y + 16, 6.2, shade(STEEL_HI, 1.02, 255), soft=1.0)
    cv.disc(x + 16, y + 16, 4.0, shade(STEEL, 0.86, 255), soft=1.0)
    cv.line(x + 12.2, y + 16, x + 19.8, y + 16, shade(STEEL_LO, 0.85, 255), 1.6)

    # (2,0) SHAFT - haste escura com brilho e ranhuras
    x, y, s = fill_cell(cv, 2, 0)
    for yy in range(y, y + s):
        for xx in range(x, x + s):
            t = (yy - y) / float(s - 1)
            c = mix(shade(CHARCOAL, 1.45), shade(CHARCOAL, 0.85), t)
            cv.put(xx, yy, (c[0], c[1], c[2], 255))
    for xx in range(x + 9, x + 24):
        cv.blend(xx, y + 2, shade(STEEL_HI, 1.0), 0.70)
        cv.blend(xx, y + 3, shade(STEEL_HI, 1.0), 0.34)
        cv.blend(xx, y + 29, shade(STEEL_LO, 0.8), 0.40)
    for xx in range(x + 5, x + 10):
        cv.blend(xx, y + 16, shade(CHARCOAL, 0.55), 0.35)
    for xx in range(x + 23, x + 28):
        cv.blend(xx, y + 16, shade(CHARCOAL, 0.55), 0.35)
    bevel(cv, x, y, s, CHARCOAL, strength=0.6)

    # (3,0) HUB - cubo de bronze
    x, y, s = fill_cell(cv, 3, 0)
    brushed_metal(cv, x, y, s, BRASS, shade(BRASS, 1.35), shade(BRASS, 0.6), seed=4, streaks=14)
    bevel(cv, x, y, s, BRASS, strength=0.4)
    cv.disc(x + 16, y + 16, 11.5, shade(BRASS, 1.12, 255), soft=1.0)
    cv.disc(x + 16, y + 16, 10.0, shade(BRASS, 0.9, 255), soft=1.0)
    cv.ring(x + 16, y + 16, 10.0, 1.6, shade(BRASS, 1.3, 255), soft=1.0)
    cv.disc(x + 16, y + 16, 3.4, shade(BRASS, 0.55, 255), soft=1.0)

    # (0,1) FIN - aleta pintada com chevron
    x, y, s = fill_cell(cv, 0, 1)
    brushed_metal(cv, x, y, s, PLATE, shade(PLATE, 1.06), shade(PLATE, 0.80), seed=5, streaks=12)
    for i, off in enumerate((0, 6, 12)):
        cv.triangle((x + 6 + off, y + 8), (x + 13 + off, y + 16), (x + 6 + off, y + 24),
                    (RED[0], RED[1], RED[2], 255) if i < 2 else shade(RED, 0.85, 255))
    bevel(cv, x, y, s, PLATE, strength=0.42)
    rivets(cv, x, y, s, PLATE, [(4, 4), (27, 4), (4, 27), (27, 27)], r=1.1)

    # (1,1) ARROWHEAD - aco escuro com bisel claro
    x, y, s = fill_cell(cv, 1, 1)
    brushed_metal(cv, x, y, s, CHARCOAL, shade(CHARCOAL, 1.6), shade(CHARCOAL, 0.72), seed=6, streaks=16)
    for yy in range(y + 3, y + 29):
        cv.blend(x + 27, yy, shade(STEEL_HI, 0.95), 0.34)
    bevel(cv, x, y, s, CHARCOAL, strength=0.55)
    cv.line(x + 4, y + 28, x + 28, y + 4, shade(STEEL, 1.1, 255), 1.4)

    # (2,1) BASE_PAD - base de madeira (Material.wood)
    x, y, s = fill_cell(cv, 2, 1)
    WOOD = (128, 92, 52, 255)
    for yy in range(y, y + s):
        for xx in range(x, x + s):
            n = cv.value_noise(xx, yy, 5.5, 21)
            grain = cv.hash01(0, yy, 9) * 0.06
            c = shade(WOOD, 0.86 + n * 0.30 - grain)
            cv.put(xx, yy, (c[0], c[1], c[2], 255))
    for t in (10, 21):
        cv.line(x, y + t, x + s - 1, y + t, shade(WOOD, 0.55, 255), 1.2)
    bevel(cv, x, y, s, WOOD, strength=0.45)

    # (3,1) STEEL_LIGHT - aco claro (hastes finas)
    x, y, s = fill_cell(cv, 3, 1)
    brushed_metal(cv, x, y, s, shade(STEEL, 1.15), STEEL_HI, STEEL, seed=8, streaks=22)
    bevel(cv, x, y, s, STEEL, strength=0.3)

    # (0,2) RUST - manchas de ferrugem (detalhe de envelhecimento)
    x, y, s = fill_cell(cv, 0, 2)
    RUST = (150, 84, 40, 255)
    for yy in range(y, y + s):
        for xx in range(x, x + s):
            n = cv.value_noise(xx, yy, 7.0, 31)
            k = cv.value_noise(xx + 40, yy, 3.2, 32)
            t = max(0.0, min(1.0, (n * 0.7 + k * 0.3 - 0.42) * 2.4))
            c = mix(shade(STEEL, 0.9), RUST, t)
            cv.put(xx, yy, (c[0], c[1], c[2], 255))
    bevel(cv, x, y, s, STEEL, strength=0.35)

    # (1,2) BOLT - parafuso isolado (usado em detalhes)
    x, y, s = fill_cell(cv, 1, 2)
    cv.rect(x, y, x + s, y + s, shade(STEEL, 0.55, 255))
    cv.disc(x + 16, y + 16, 11.0, shade(STEEL_HI, 0.95, 255), soft=1.0)
    cv.disc(x + 16, y + 16, 9.0, shade(STEEL, 1.0, 255), soft=1.0)
    for a in range(6):
        ang = a * math.pi / 3
        cv.line(x + 16, y + 16, x + 16 + 9 * math.cos(ang), y + 16 + 9 * math.sin(ang),
                shade(STEEL_LO, 0.9, 255), 1.1)

    # (2,2) BRASS_ROD - haste de bronze (eixo do cata-vento)
    x, y, s = fill_cell(cv, 2, 2)
    for yy in range(y, y + s):
        for xx in range(x, x + s):
            t = (yy - y) / float(s - 1)
            c = mix(shade(BRASS, 1.3), shade(BRASS, 0.7), t)
            cv.put(xx, yy, (c[0], c[1], c[2], 255))
    for yy in range(y + 1, y + s - 1):
        cv.blend(x + 12, yy, shade(BRASS, 1.5), 0.5)
        cv.blend(x + 13, yy, shade(BRASS, 1.4), 0.3)
        cv.blend(x + 26, yy, shade(BRASS, 0.5), 0.5)
    bevel(cv, x, y, s, BRASS, strength=0.5)

    # (3,2) PLANK - madeira envelhecida (cauda/norte do cata-vento)
    x, y, s = fill_cell(cv, 3, 2)
    for yy in range(y, y + s):
        for xx in range(x, x + s):
            n = cv.value_noise(xx, yy, 4.5, 91)
            g = cv.hash01(0, yy, 13) * 0.10
            c = shade(WOOD, 0.78 + n * 0.34 - g)
            cv.put(xx, yy, (c[0], c[1], c[2], 255))
    for yy in range(y, y + s, 7):
        cv.line(x, yy, x + s - 1, yy, shade(WOOD, 0.5, 255), 1.0)
    # furos de pregos
    for (px, py) in ((6, 4), (25, 4), (6, 27), (25, 27)):
        cv.disc(x + px, y + py, 1.7, shade(WOOD, 0.45, 255), soft=0.9)
        cv.disc(x + px, y + py - 0.4, 1.1, shade(STEEL, 0.9, 255), soft=0.9)
    bevel(cv, x, y, s, WOOD, strength=0.55)

    return cv


# --------------------------------------------------------------------------
# Atlas 2 - Manga de vento (windsock)
# --------------------------------------------------------------------------
def gen_windsock():
    cv = Canvas(128, 128)
    ORANGE = (222, 124, 30, 255)
    WHITE = (232, 232, 226, 255)
    STEEL = (128, 136, 143, 255)
    STEEL_HI = (198, 206, 214, 255)
    STEEL_LO = (56, 62, 69, 255)

    # Faixas classicas de manga de vento: laranja / branco alternados
    for i, base in enumerate((ORANGE, WHITE, ORANGE, shade(ORANGE, 0.78))):
        x, y, s = fill_cell(cv, i, 0)
        weave(cv, x, y, s, base, seed=100 + i, strength=0.17)
        bevel(cv, x, y, s, base, strength=0.30)

    # (0,1) POLE_STEEL
    x, y, s = fill_cell(cv, 0, 1)
    brushed_metal(cv, x, y, s, STEEL, STEEL_HI, STEEL_LO, seed=201)
    bevel(cv, x, y, s, STEEL)
    rivets(cv, x, y, s, STEEL, [(6, 16), (25, 16)])

    # (1,1) BASE_PAD - placa de concreto/ac o
    x, y, s = fill_cell(cv, 1, 1)
    CONC = (148, 148, 146, 255)
    for yy in range(y, y + s):
        for xx in range(x, x + s):
            n = cv.value_noise(xx, yy, 4.0, 55)
            k = cv.value_noise(xx, yy, 1.7, 56)
            c = shade(CONC, 0.90 + n * 0.18 + k * 0.06)
            cv.put(xx, yy, (c[0], c[1], c[2], 255))
    bevel(cv, x, y, s, CONC, strength=0.4)
    rivets(cv, x, y, s, CONC, [(5, 5), (26, 5), (5, 26), (26, 26)], r=1.2)

    # (2,1) FRINGE - boca aberta com fios soltos (usada na ponta da manga)
    x, y, s = fill_cell(cv, 2, 1)
    weave(cv, x, y, s, shade(ORANGE, 0.74), seed=300, strength=0.14)
    for i in range(0, s, 3):
        tip = 1 + (i * 7 % 3)
        cv.line(x + i, y + 1, x + i, y + s - 1 - tip, shade(ORANGE, 0.48, 255), 1.0)
        cv.line(x + i, y + 2, x + i, y + s - 3 - tip, shade(ORANGE, 1.14, 255), 0.6)
    cv.rect(x, y, x + s, y + 3, (0, 0, 0, 0))

    # (3,1) SEAM - tecido com costura/bainha
    x, y, s = fill_cell(cv, 3, 1)
    weave(cv, x, y, s, shade(ORANGE, 0.90), seed=400, strength=0.15)
    cv.line(x + 15, y + 1, x + 15, y + s - 2, shade(ORANGE, 0.48, 255), 1.2)
    for yy in range(y + 3, y + s - 2, 4):
        cv.disc(x + 15, yy, 1.0, shade(WHITE, 1.0, 255), soft=0.8)

    # (0,2) WHITE_WEAVE - branco com trama (banda)
    x, y, s = fill_cell(cv, 0, 2)
    weave(cv, x, y, s, WHITE, seed=500, strength=0.15)
    bevel(cv, x, y, s, WHITE, strength=0.22)

    # (1,2) DIRT_FABRIC - tecido sujo/desgastado
    x, y, s = fill_cell(cv, 1, 2)
    weave(cv, x, y, s, shade(WHITE, 0.88), seed=600, strength=0.15)
    for yy in range(y, y + s):
        for xx in range(x, x + s):
            n = cv.value_noise(xx, yy, 6.0, 61)
            if n > 0.62:
                cv.blend(xx, yy, (96, 82, 58, 255), (n - 0.62) * 1.7)

    # (2,2) CAP_FABRIC - interior/tampa do barril (banda escura)
    x, y, s = fill_cell(cv, 2, 2)
    weave(cv, x, y, s, shade(ORANGE, 0.62), seed=700, strength=0.13)
    cv.ring(x + 16, y + 16, 12.0, 3.2, shade(ORANGE, 0.85, 255), soft=1.0)
    cv.disc(x + 16, y + 16, 8.0, shade(ORANGE, 0.42, 255), soft=1.0)

    # (3,2) CAP_RING - aro metalico da boca
    x, y, s = fill_cell(cv, 3, 2)
    brushed_metal(cv, x, y, s, STEEL_LO, STEEL, shade(STEEL_LO, 0.7), seed=800, streaks=16)
    cv.ring(x + 16, y + 16, 12.5, 3.0, shade(STEEL_HI, 0.95, 255), soft=1.0)
    cv.ring(x + 16, y + 16, 8.5, 1.6, shade(STEEL, 0.75, 255), soft=1.0)
    bevel(cv, x, y, s, STEEL_LO, strength=0.5)

    return cv


# --------------------------------------------------------------------------
# Atlas 3 - Anemometro
# --------------------------------------------------------------------------
def gen_anemometer():
    cv = Canvas(128, 128)
    STEEL = (122, 129, 136, 255)
    STEEL_HI = (198, 206, 214, 255)
    STEEL_LO = (54, 60, 66, 255)
    RED = (176, 62, 54, 255)

    # (0,0) POLE
    x, y, s = fill_cell(cv, 0, 0)
    brushed_metal(cv, x, y, s, STEEL, STEEL_HI, STEEL_LO, seed=701)
    bevel(cv, x, y, s, STEEL)
    for yy in range(y, y + s, 8):
        cv.line(x + 1, yy, x + s - 2, yy, shade(STEEL_LO, 1.1, 255), 0.9)

    # (1,0) BASE_PAD metalico
    x, y, s = fill_cell(cv, 1, 0)
    brushed_metal(cv, x, y, s, shade(STEEL, 0.9), STEEL, shade(STEEL_LO, 0.85), seed=702, streaks=20)
    bevel(cv, x, y, s, STEEL, strength=0.5)
    rivets(cv, x, y, s, STEEL, [(5, 5), (26, 5), (5, 26), (26, 26)])

    # (2,0) HUB - cubo escuro com parafuso
    x, y, s = fill_cell(cv, 2, 0)
    brushed_metal(cv, x, y, s, STEEL_LO, STEEL, shade(STEEL_LO, 0.65), seed=703, streaks=16)
    cv.disc(x + 16, y + 16, 12.0, shade(STEEL_LO, 1.15, 255), soft=1.0)
    cv.ring(x + 16, y + 16, 12.0, 1.8, shade(STEEL_HI, 0.85, 255), soft=1.0)
    cv.disc(x + 16, y + 16, 4.0, shade(STEEL_HI, 1.05, 255), soft=1.0)

    # (3,0) CUP_OUTER - lado externo do cono (vermelho classico)
    x, y, s = fill_cell(cv, 3, 0)
    for yy in range(y, y + s):
        for xx in range(x, x + s):
            t = (xx - x) / float(s - 1)
            c = mix(shade(RED, 1.22), shade(RED, 0.72), t)
            n = cv.value_noise(xx, yy, 3.0, 77)
            c = shade(c, 0.96 + n * 0.09)
            cv.put(xx, yy, (c[0], c[1], c[2], 255))
    bevel(cv, x, y, s, RED, strength=0.3)
    for yy in range(y + 4, y + s - 4, 7):
        cv.line(x + 2, yy, x + s - 3, yy, shade(RED, 0.8, 255), 0.8)

    # (0,1) CUP_INNER - lado interno da concha (sombreado, ainda legivel)
    x, y, s = fill_cell(cv, 0, 1)
    for yy in range(y, y + s):
        for xx in range(x, x + s):
            n = cv.value_noise(xx, yy, 2.8, 88)
            c = shade(RED, 0.58 + n * 0.14)
            cv.put(xx, yy, (c[0], c[1], c[2], 255))
    bevel(cv, x, y, s, RED, strength=0.6)
    # brilho da borda superior do cono
    for xx in range(x + 4, x + s - 4):
        cv.blend(xx, y + 2, shade(RED, 1.25), 0.45)

    # (1,1) ARM - haste fina
    x, y, s = fill_cell(cv, 1, 1)
    brushed_metal(cv, x, y, s, shade(STEEL, 1.05), STEEL_HI, STEEL_LO, seed=704, streaks=24)
    bevel(cv, x, y, s, STEEL, strength=0.32)

    # (2,1) METAL_LIGHT
    x, y, s = fill_cell(cv, 2, 1)
    brushed_metal(cv, x, y, s, shade(STEEL, 1.2), STEEL_HI, STEEL, seed=705, streaks=20)
    bevel(cv, x, y, s, STEEL, strength=0.28)

    # (3,1) RUBBER / anilha de vedacao
    x, y, s = fill_cell(cv, 3, 1)
    RUB = (48, 48, 52, 255)
    for yy in range(y, y + s):
        for xx in range(x, x + s):
            n = cv.value_noise(xx, yy, 2.2, 99)
            c = shade(RUB, 0.85 + n * 0.4)
            cv.put(xx, yy, (c[0], c[1], c[2], 255))
    cv.ring(x + 16, y + 16, 12.0, 2.4, shade(RUB, 1.35, 255), soft=1.0)

    # (0,2) ARM_DARK - haste em sombra (lado oposto do cubo)
    x, y, s = fill_cell(cv, 0, 2)
    brushed_metal(cv, x, y, s, shade(STEEL, 0.68), STEEL, shade(STEEL_LO, 0.8), seed=706, streaks=24)
    bevel(cv, x, y, s, STEEL, strength=0.45)

    # (1,2) STEEL_PLATE - chapa com parafusos (suporte inferior)
    x, y, s = fill_cell(cv, 1, 2)
    brushed_metal(cv, x, y, s, shade(STEEL, 1.08), STEEL_HI, STEEL, seed=707, streaks=14)
    bevel(cv, x, y, s, STEEL, strength=0.4)
    rivets(cv, x, y, s, STEEL, [(6, 6), (25, 6), (6, 25), (25, 25)], r=1.3)

    # (2,2) GAUGE_FACE - mostrador do anemometro (detalhe do topo)
    x, y, s = fill_cell(cv, 2, 2)
    cv.rect(x, y, x + s, y + s, shade(STEEL_LO, 1.2, 255))
    cv.disc(x + 16, y + 16, 12.5, shade(STEEL_HI, 0.9, 255), soft=1.0)
    cv.disc(x + 16, y + 16, 11.0, (238, 236, 226, 255), soft=1.0)
    for a in range(9):
        ang = math.pi * (1.0 + a / 8.0)
        r0, r1 = 9.5, 11.0
        cv.line(x + 16 + r0 * math.cos(ang), y + 16 - r0 * math.sin(ang),
                x + 16 + r1 * math.cos(ang), y + 16 - r1 * math.sin(ang), (60, 60, 64, 255), 0.9)
    cv.line(x + 16, y + 16, x + 16 + 7.0 * math.cos(math.pi * 1.3), y + 16 - 7.0 * math.sin(math.pi * 1.3),
            (176, 62, 54, 255), 1.3)
    cv.disc(x + 16, y + 16, 1.6, (60, 60, 64, 255), soft=0.8)

    return cv


# --------------------------------------------------------------------------
# Particulas
# --------------------------------------------------------------------------
def gen_item_icons():
    """Icones 16x16 usados pelo ItemBlock no inventario.

    Os blocos nao desenham cubo no mundo (render handler proprio devolve
    'nada'), portanto o icone do item precisa ser um sprite proprio em vez do
    atlas 128x128.
    """
    out = {}

    # cata-vento visto de lado: poste + seta com cauda
    cv = Canvas(16, 16)
    STEEL = (162, 170, 178, 255)
    STEEL_LO = (86, 92, 100, 255)
    WOOD = (128, 92, 52, 255)
    RED = (168, 58, 52, 255)
    cv.rect(7, 7, 9, 15, STEEL_LO)                 # poste
    cv.rect(7, 7, 8, 15, STEEL)                    # brilho do poste
    cv.rect(2, 8, 14, 10, STEEL_LO)                # haste
    cv.rect(2, 8, 14, 9, STEEL)
    cv.triangle((13.0, 3.0), (13.0, 14.0), (6.0, 8.5), RED)   # ponta da seta
    cv.triangle((2.0, 5.0), (6.5, 8.0), (2.0, 11.0), WOOD)    # cauda
    cv.rect(2, 12, 14, 13, STEEL_LO)               # base
    out["wind_vane_item.png"] = cv

    # manga de vento: poste + manga listrada
    cv = Canvas(16, 16)
    ORANGE = (226, 128, 32, 255)
    WHITE = (234, 234, 228, 255)
    DARK = (150, 84, 20, 255)
    cv.rect(3, 4, 5, 15, STEEL_LO)
    cv.rect(3, 4, 4, 15, STEEL)
    cv.triangle((4.0, 5.0), (14.5, 7.5), (4.0, 10.0), ORANGE)  # boca
    for i, xx in enumerate(range(6, 15, 2)):
        cv.rect(xx, 6 + i // 2, xx + 2, 10 - i // 2, WHITE if i % 2 else ORANGE)
    cv.triangle((4.0, 5.0), (5.5, 7.5), (4.0, 10.0), DARK)     # sombra da boca
    cv.rect(2, 13, 6, 15, STEEL_LO)                # base
    out["windsock_item.png"] = cv

    # anemometro: poste + tres conchas
    cv = Canvas(16, 16)
    RED = (176, 62, 54, 255)
    RED_D = (120, 40, 36, 255)
    cv.rect(7, 6, 9, 15, STEEL_LO)
    cv.rect(7, 6, 8, 15, STEEL)
    cv.rect(3, 3, 13, 5, STEEL_LO)                 # travessa
    for (cx, cy) in ((2.6, 3.0), (8.0, 1.6), (13.4, 3.0)):
        cv.disc(cx, cy, 2.5, RED, soft=1.0)
        cv.disc(cx, cy + 0.5, 1.5, RED_D, soft=1.0)
    cv.disc(8.0, 4.0, 1.6, STEEL, soft=1.0)        # cubo central
    cv.rect(5, 14, 11, 16, STEEL_LO)               # base
    out["anemometer_item.png"] = cv

    return out


def gen_particles():
    out = {}

    # leaf 16x16 - folha lanceolada na diagonal (le como folha em movimento)
    cv = Canvas(16, 16)
    GREEN = (74, 148, 56, 255)
    DARK = (40, 96, 34, 255)
    LIGHT = (132, 200, 88, 255)
    VEIN = (33, 80, 28, 255)
    STEM = (112, 84, 42, 255)
    ax0, ay0, ax1, ay1 = 3.2, 12.8, 13.0, 3.0     # eixo base -> ponta
    dx, dy = ax1 - ax0, ay1 - ay0
    length = (dx * dx + dy * dy) ** 0.5
    ux, uy = dx / length, dy / length
    px_, py_ = -uy, ux                            # perpendicular ao eixo
    for y in range(16):
        for x in range(16):
            rx, ry = x + 0.5 - ax0, y + 0.5 - ay0
            u = (rx * ux + ry * uy) / length      # 0..1 ao longo do eixo
            v = (rx * px_ + ry * py_)             # deslocamento perpendicular
            if not (0.0 <= u <= 1.0):
                continue
            half = 3.05 * (math.sin(math.pi * (u ** 0.78)) ** 0.9)
            half *= 0.94 + 0.06 * math.sin(u * 22.0)   # serrilhado leve da borda
            if abs(v) <= half:
                edge = abs(v) / max(0.001, half)
                if edge > 0.78:
                    c = DARK
                elif edge > 0.52:
                    c = mix(DARK, GREEN, (0.78 - edge) / 0.26)
                else:
                    c = mix(GREEN, LIGHT, (0.52 - edge) / 0.52 * 0.70)
                cv.put(x, y, c)
    for t in range(0, 101):                       # nervura central
        f = t / 100.0
        vx, vy = int(ax0 + dx * f), int(ay0 + dy * f)
        if cv.get(vx, vy)[3] > 0:                 # nao vaza alpha para fora da lamina
            cv.blend(vx, vy, mix(VEIN, GREEN, 0.30), 0.85)
    for i, f in enumerate((0.26, 0.42, 0.58, 0.74)):   # nervuras laterais
        bx, by = ax0 + dx * f, ay0 + dy * f
        sgn = 1 if i % 2 == 0 else -1
        cv.line(bx, by, bx + px_ * 2.4 * sgn + ux * 1.1, by + py_ * 2.4 * sgn + uy * 1.1,
                mix(VEIN, GREEN, 0.45), 0.5)
    cv.line(ax0, ay0, ax0 - ux * 2.0, ay0 - uy * 2.0, STEM, 1.0)   # peciolo
    out["leaf.png"] = cv

    # pollen 8x8 - graozinho suave
    cv = Canvas(8, 8)
    for y in range(8):
        for x in range(8):
            d = ((x + 0.5 - 4) ** 2 + (y + 0.5 - 4) ** 2) ** 0.5
            a = max(0.0, min(1.0, (3.6 - d) / 1.2))
            if a > 0:
                c = mix((250, 226, 120, 255), (255, 250, 190, 255), max(0.0, 1 - d / 3.6))
                cv.put(x, y, (c[0], c[1], c[2], int(a * 235)))
    out["pollen.png"] = cv

    # dust 8x8 - speck de poeira
    cv = Canvas(8, 8)
    TAN = (176, 148, 108, 255)
    for y in range(8):
        for x in range(8):
            d = ((x + 0.5 - 4) ** 2 + (y + 0.5 - 4) ** 2) ** 0.5 + cv.hash01(x, y, 3) * 1.1
            a = max(0.0, min(1.0, (3.9 - d) / 1.1))
            if a > 0:
                c = shade(TAN, 0.82 + cv.hash01(x, y, 7) * 0.42)
                cv.put(x, y, (c[0], c[1], c[2], int(a * 210)))
    out["dust.png"] = cv

    # snowflake 8x8 - floco de 6 pontas
    cv = Canvas(8, 8)
    W = (246, 250, 255, 255)
    cv.disc(3.5, 3.5, 0.85, W, soft=0.8)
    for a in range(6):
        ang = a * math.pi / 3
        cv.line(3.5, 3.5, 3.5 + 3.1 * math.cos(ang), 3.5 + 3.1 * math.sin(ang), W, 0.85)
        cv.line(3.5 + 1.8 * math.cos(ang), 3.5 + 1.8 * math.sin(ang),
                3.5 + 1.8 * math.cos(ang) + 1.1 * math.cos(ang + 1.05),
                3.5 + 1.8 * math.sin(ang) + 1.1 * math.sin(ang + 1.05), W, 0.7)
    out["snowflake.png"] = cv

    # raindrop 4x8 - gota
    cv = Canvas(4, 8)
    for y in range(8):
        for x in range(4):
            u = (x + 0.5 - 2.0)
            v = (y + 0.5)
            rx = 1.55 * min(1.0, (v + 0.6) / 4.0) + 0.28
            a = max(0.0, min(1.0, (rx - abs(u)) / 0.7))
            if v > 7.2:
                a *= max(0.0, (8.0 - v) / 0.8)
            if a > 0:
                c = mix((120, 178, 228, 255), (200, 232, 255, 255), max(0.0, 1 - v / 8.0))
                cv.put(x, y, (c[0], c[1], c[2], int(a * 225)))
    out["raindrop.png"] = cv

    # rainsplash 8x8 - anel de respingo
    cv = Canvas(8, 8)
    SP = (198, 224, 246, 255)
    for y in range(8):
        for x in range(8):
            d = ((x + 0.5 - 4) ** 2 + ((y + 0.5 - 4) * 1.7) ** 2) ** 0.5
            a = max(0.0, min(1.0, (1.6 - abs(d - 2.6)) / 0.9))
            if a > 0:
                cv.put(x, y, (SP[0], SP[1], SP[2], int(a * 200)))
    cv.disc(4, 3.2, 0.9, (222, 240, 255, 230), soft=0.8)
    out["rainsplash.png"] = cv

    # swirl 16x16 - redemoinho translucido
    cv = Canvas(16, 16)
    for arm in range(2):
        for t in range(0, 200):
            f = t / 199.0
            ang = f * 3.4 + arm * math.pi
            r = 1.2 + f * 6.6
            cx = 8 + r * math.cos(ang)
            cy = 8 + r * math.sin(ang) * 0.92
            a = (1 - f) * 0.55
            cv.disc(cx, cy, 1.35 - f * 0.55, (226, 234, 244, 255), soft=1.6)
            cv.blend(int(cx), int(cy), (240, 246, 255, 255), a)
    out["swirl.png"] = cv

    # gust 16x16 - risco de rajada
    cv = Canvas(16, 16)
    for i, (y0, y1, x0, x1) in enumerate((
            (4, 3, 1, 12), (8, 7, 2, 14), (12, 11, 3, 11))):
        for t in range(60):
            f = t / 59.0
            x = x0 + (x1 - x0) * f
            y = y0 + (y1 - y0) * f - math.sin(f * 3.1) * 1.5
            a = math.sin(f * math.pi) * (0.5 - i * 0.08)
            cv.disc(x, y, 1.15 - i * 0.15, (238, 244, 252, 255), soft=1.5)
            cv.blend(int(x), int(y), (255, 255, 255, 255), a)
    out["gust.png"] = cv

    # debris 16x16 - graveto/cavaco
    cv = Canvas(16, 16)
    BROWN = (118, 84, 48, 255)
    cv.line(2, 12, 13, 3, BROWN, 2.4)
    cv.line(2, 11, 13, 2, shade(BROWN, 1.3, 255), 0.9)
    cv.line(5, 8, 9, 11, shade(BROWN, 0.8, 255), 1.4)
    cv.line(8, 5, 11, 8, shade(BROWN, 0.8, 255), 1.2)
    out["debris.png"] = cv

    # tornado 64x64 - funil com alpha
    cv = Canvas(64, 64)
    for y in range(64):
        v = y / 63.0
        half = 4.0 + 26.0 * (v ** 0.85)         # alarga para baixo
        cx = 32 + math.sin(v * 3.4) * 5.0 * (0.3 + v)
        for x in range(64):
            dx = x + 0.5 - cx
            t = abs(dx) / max(1.0, half)
            if t < 1.0:
                edge = 1.0 - t
                a = min(1.0, edge * 2.0) * (0.30 + 0.45 * (1 - v))
                n = cv.value_noise(x, y, 2.6, 5)
                a *= 0.72 + n * 0.55
                # bandas horizontais de poeira
                band = 0.5 + 0.5 * math.sin(v * 34.0 + n * 3.0)
                a *= 0.62 + 0.5 * band
                a = max(0.0, min(1.0, a))
                if a > 0.02:
                    c = mix((158, 152, 146, 255), (206, 200, 192, 255), 1 - v)
                    c = shade(c, 0.85 + n * 0.32)
                    cv.put(x, y, (c[0], c[1], c[2], int(a * 215)))
    out["tornado.png"] = cv

    return out


# --------------------------------------------------------------------------
def gen_storm_particles():
    """Particulas novas: chuva inclinada e respingo da parede do olho."""
    out = {}

    # chuva: raja fina e alongada na vertical (a inclinacao vem da fisica)
    cv = Canvas(8, 16)
    for y in range(16):
        # mais estreita nas pontas, cheia no meio: le como gota em movimento
        t = y / 15.0
        half = 1.15 * math.sin(math.pi * t) ** 0.6 + 0.35
        for x in range(8):
            dx = abs(x + 0.5 - 4.0)
            if dx > half + 0.6:
                continue
            a = 1.0 - max(0.0, (dx - half) / 0.7)
            # corpo azul-claro com nucleo branco
            c = mix((120, 168, 224), (232, 244, 255), 1.0 - dx / max(1.0, half))
            cv.put(x, y, (c[0], c[1], c[2], int(210 * a)))
    out["storm_rain.png"] = cv

    # respingo: espuma levantada onde o vento bate na agua ou no chao
    cv = Canvas(8, 8)
    cv.disc(4.0, 5.2, 3.0, (206, 226, 244, 190), soft=0.9)
    cv.disc(4.0, 5.6, 2.1, (238, 246, 255, 225), soft=0.9)
    for (sx, sy, r) in ((2.2, 2.6, 1.1), (5.6, 2.2, 1.3), (4.0, 1.6, 0.9)):
        cv.disc(sx, sy, r, (246, 250, 255, 170), soft=1.0)
    out["spray.png"] = cv

    return out


def gen_staff_and_environment():
    """Icone do Cajado da Catastrofe e as texturas de ceu da tempestade."""
    out = ([], [])

    # ---- cajado: haste de obsidiana + orbe com vortice ---------------------
    cv = Canvas(16, 16)
    OBS = (26, 20, 38, 255)
    OBS_HI = (66, 54, 92, 255)
    VIOLET = (96, 62, 148, 255)
    VIOLET_HI = (158, 128, 226, 255)
    CYAN = (126, 224, 236, 255)
    EYE = (12, 10, 20, 255)
    SPARK = (250, 238, 172, 255)

    cv.line(3.0, 14.6, 10.2, 6.4, OBS, thick=2.8)          # haste
    cv.line(3.8, 14.0, 10.6, 6.0, OBS_HI, thick=0.9)       # faceta iluminada
    for k in range(4):                                     # enrolado do cabo
        t = 0.16 + k * 0.17
        wx = 3.0 + (10.2 - 3.0) * t
        wy = 14.6 + (6.4 - 14.6) * t
        cv.line(wx + 1.0, wy - 1.0, wx - 1.0, wy + 1.0, OBS_HI, thick=0.7)
    cv.disc(2.5, 15.3, 1.5, OBS, soft=0.9)                 # pomo da base
    cv.disc(2.5, 15.3, 0.9, OBS_HI, soft=0.8)

    cx, cy = 11.2, 4.0                                     # orbe
    cv.disc(cx, cy, 4.4, (18, 12, 32, 255), soft=0.8)      # contorno escuro
    cv.disc(cx, cy, 3.9, mix(VIOLET, EYE, 0.45), soft=1.0)  # base profunda
    cv.disc(cx, cy, 3.9, (34, 16, 62, 120), soft=1.0)
    # espiral: arcos com raio crescente, que e o que le como vortice em 16x16
    steps = 34
    for i in range(steps + 1):
        t = i / float(steps)
        ang = t * 3.4 * math.pi
        r = 0.9 + t * 2.6
        glow = mix(CYAN, (236, 240, 255, 255), t * 0.55)
        cv.disc(cx + math.cos(ang) * r, cy + math.sin(ang) * r, 0.7, glow, soft=0.95)
    cv.disc(cx, cy, 1.2, EYE, soft=1.0)                    # olho do furacao
    cv.disc(cx - 2.0, cy - 2.2, 0.5, (244, 250, 255, 255), soft=1.0)
    out[0].append(("catastrophe_staff.png", cv))

    # ---- barometro: caixa metalica, mostrador e agulha ----------------------
    cv = Canvas(16, 16)
    cv.rect(3, 2, 13, 15, (62, 68, 78, 255))
    cv.rect(4, 3, 12, 14, (166, 176, 184, 255))
    cv.rect(5, 4, 11, 10, (226, 226, 210, 255))
    cv.ring(8, 7, 2.6, 0.6, (56, 64, 72, 255), soft=0.8)
    cv.line(8, 8, 9.8, 5.7, (174, 46, 38, 255), thick=0.9)
    cv.disc(8, 8, 0.8, (60, 64, 68, 255), soft=0.8)
    cv.rect(6, 11, 10, 13, (70, 76, 84, 255))
    out[0].append(("barometer.png", cv))

    # ---- teto de nuvem: massa escura, repetivel ---------------------------
    size = 128
    cv = Canvas(size, size)
    DEEP = (12, 15, 24)
    MID = (78, 88, 110)
    TOP = (198, 206, 220)
    for y in range(size):
        for x in range(size):
            big = _fbm(cv, x, y, 64.0, size, 3, s=5)
            mid = _fbm(cv, x, y, 24.0, size, 3, s=41)
            fine = _fbm(cv, x, y, 8.0, size, 3, s=97)
            v = big * 0.52 + mid * 0.33 + fine * 0.15
            alpha = (v - 0.42) / 0.18
            if alpha <= 0.06:
                continue
            if alpha > 1.0:
                alpha = 1.0
            alpha = alpha ** 0.7
            t = min(1.0, max(0.0, (v - 0.40) / 0.42))
            c = mix(DEEP, MID, t)
            # As bordas superiores das celulas pegam luz. O realce vem de uma
            # terceira oitava de ruido, e NAO de um gradiente em y: a textura e
            # repetida varias vezes pela malha (3x no anel, 2,4x no raio), entao
            # qualquer gradiente assado se repete junto e vira faixa visivel. A
            # luz geral quem faz e o renderizador, por vertice.
            cell = _fbm(cv, x, y, 40.0, size, 2, s=203)
            hi = min(1.0, max(0.0, (v - 0.58) / 0.42))
            c = mix(c, TOP, hi * 0.72 * (0.72 + 0.56 * cell))
            cv.put(x, y, (c[0], c[1], c[2], int(alpha * 236)))
    out[1].append(("storm_clouds.png", cv))

    # ---- parede do olho / cortina de chuva --------------------------------
    w, h = 64, 64
    cv = Canvas(w, h)
    W_DARK = (30, 36, 50)
    W_LIGHT = (144, 154, 172)
    for y in range(h):
        for x in range(w):
            # Duas escalas de raja, ambas fechando o tile: 12x3 celulas (raja de
            # ~5 px de largura por ~21 px de altura) e 24x6 (detalhe fino). O
            # alongamento vem de haver poucas celulas em y, nao de comprimir o
            # eixo - comprimir quebrava a costura.
            streak = (_streak_noise(cv, x, y, 12, 3, s=131) * 0.62
                      + _streak_noise(cv, x, y, 24, 6, s=241) * 0.38)
            mass = _fbm(cv, x, y, 20.0, w, 3, s=77)
            v = streak * 0.62 + mass * 0.38
            # So estrutura: a densidade que cai com a altura e do renderizador
            # (alpha por vertice), nao da textura. A textura repete na vertical
            # (1 unidade de UV por ~40 blocos de altura da parede), entao uma
            # rampa de alpha aqui dentro apareceria repetida como faixas.
            alpha = (v - 0.30) / 0.34
            if alpha <= 0.04:
                continue
            if alpha > 1.0:
                alpha = 1.0
            alpha = alpha ** 0.8
            # um pouco mais de contraste: a cortina precisa ler como rajas
            t = min(1.0, max(0.0, (v - 0.34) / 0.40))
            c = mix(W_DARK, W_LIGHT, t ** 0.85)
            cv.put(x, y, (c[0], c[1], c[2], int(alpha * 224)))
    out[1].append(("storm_wall.png", cv))

    return out


def main():
    total = 0
    icons, environment = gen_staff_and_environment()
    for name, cv in icons:
        n = cv.to_png(os.path.join(ITEMS, name))
        print("items/%-16s %3dx%-3d %6d bytes" % (name, cv.w, cv.h, n))
        total += n
    for name, cv in environment:
        n = cv.to_png(os.path.join(ENVIRONMENT, name))
        print("environment/%-12s %3dx%-3d %6d bytes" % (name, cv.w, cv.h, n))
        total += n
    for name, cv in (("wind_vane.png", gen_wind_vane()),
                     ("windsock.png", gen_windsock()),
                     ("anemometer.png", gen_anemometer())):
        n = cv.to_png(os.path.join(BLOCKS, name))
        print("blocks/%-16s %3dx%-3d %6d bytes" % (name, cv.w, cv.h, n))
        total += n
    for name, cv in sorted(gen_item_icons().items()):
        n = cv.to_png(os.path.join(BLOCKS, name))
        print("blocks/%-16s %3dx%-3d %6d bytes" % (name, cv.w, cv.h, n))
        total += n
    all_particles = gen_particles()
    all_particles.update(gen_storm_particles())
    for name, cv in sorted(all_particles.items()):
        n = cv.to_png(os.path.join(PARTICLES, name))
        print("particle/%-16s %3dx%-3d %6d bytes" % (name, cv.w, cv.h, n))
        total += n
    print("total: %d bytes" % total)


if __name__ == "__main__":
    main()
