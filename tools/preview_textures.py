#!/usr/bin/env python3
"""
DynamicWinds - prancha de previa das texturas (Python puro, sem dependencias).

Monta docs/textures_preview.png com os blocos ampliados (com a grade das
celulas do atlas) e as particulas, sobre um fundo xadrez. Serve para revisar as
texturas sem abrir o jogo e para comparar antes/depois de rodar
tools/gen_textures.py.

Uso: python3 tools/preview_textures.py
"""

import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src/main/resources/assets/dynamicwinds/textures")
OUT = os.path.join(ROOT, "docs/textures_preview.png")

BLOCK_SCALE = 3
PARTICLE_SCALE = 8
CHECKER = 10


# --------------------------------------------------------------------------
# PNG de entrada (leitura) - mesma implementacao usada no gerador
# --------------------------------------------------------------------------
def load_png(path):
    data = open(path, "rb").read()
    pos, idat, w, h, ct = 8, b"", 0, 0, 0
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            w, h, _bd, ct = struct.unpack(">IIBB", chunk[:10])
        elif tag == b"IDAT":
            idat += chunk
        elif tag == b"IEND":
            break
        pos += 12 + length
    raw = zlib.decompress(idat)
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    stride = w * channels
    rows, prev = [], bytearray(stride)
    for y in range(h):
        ft = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - channels] if i >= channels else 0
            b = prev[i]
            c = prev[i - channels] if i >= channels else 0
            if ft == 1:
                line[i] = (line[i] + a) & 255
            elif ft == 2:
                line[i] = (line[i] + b) & 255
            elif ft == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif ft == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        rows.append([tuple(line[i:i + channels]) for i in range(0, stride, channels)])
        prev = line
    if channels == 3:
        rows = [[(r, g, b, 255) for (r, g, b) in row] for row in rows]
    elif channels == 1:
        rows = [[(v, v, v, 255) for (v,) in row] for row in rows]
    return w, h, rows


# --------------------------------------------------------------------------
# canvas de saida
# --------------------------------------------------------------------------
def checkerboard(w, h, size=CHECKER):
    return [[(70, 70, 74, 255) if ((x // size + y // size) % 2 == 0) else (98, 98, 102, 255)
             for x in range(w)] for y in range(h)]


def blit(dst, dw, dh, src, sw, sh, ox, oy, scale, alpha_bg=True):
    for y in range(sh * scale):
        for x in range(sw * scale):
            c = src[y // scale][x // scale]
            dx, dy = ox + x, oy + y
            if not (0 <= dx < dw and 0 <= dy < dh):
                continue
            if c[3] < 255 and alpha_bg:
                d = dst[dy][dx]
                a = c[3] / 255.0
                c = (int(c[0] * a + d[0] * (1 - a)),
                     int(c[1] * a + d[1] * (1 - a)),
                     int(c[2] * a + d[2] * (1 - a)), 255)
            dst[dy][dx] = c


def grid(dst, dw, dh, x0, y0, cell, scale, lines=4, color=(255, 70, 70, 255)):
    for r in range(lines):
        for c in range(lines):
            gx, gy = x0 + c * cell * scale, y0 + r * cell * scale
            for i in range(cell * scale):
                if gx + i < dw:
                    dst[gy][gx + i] = color
                if gy + i < dh:
                    dst[gy + i][gx] = color


def save_png(path, px, w, h):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw += bytes(px[y][x][:4])

    def chunk(tag, payload):
        return (struct.pack(">I", len(payload)) + tag + payload
                + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n"
                 + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                 + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
                 + chunk(b"IEND", b""))


def main():
    blocks = ["wind_vane", "windsock", "anemometer"]
    items = ["wind_vane_item", "windsock_item", "anemometer_item"]

    pad = 16
    block_imgs = [load_png(os.path.join(TEX, "blocks", name + ".png")) for name in blocks]
    item_imgs = [load_png(os.path.join(TEX, "blocks", name + ".png")) for name in items]

    # particulas primeiro as menores, para as prateleiras ficarem compactas
    particle_names = sorted(os.listdir(os.path.join(TEX, "particle")),
                            key=lambda n: os.path.getsize(os.path.join(TEX, "particle", n)))
    particles = [(name, load_png(os.path.join(TEX, "particle", name)))
                 for name in particle_names]

    # primeira faixa: cada bloco (atlas ampliado + icone de item, na mesma escala)
    unit = BLOCK_SCALE
    width = pad
    first_row = []
    for (bw, bh, brows), (iw, ih, irows) in zip(block_imgs, item_imgs):
        first_row.append((bw, bh, brows, iw, ih, irows, width))
        width += bw * unit + 8 + iw * unit + pad
    width += pad

    block_h = max(bh for (_bw, bh, _r) in block_imgs) * unit
    row2_y = pad + block_h + pad * 2

    # segunda faixa: particulas, em prateleiras (cada uma no seu tamanho)
    max_w = max(width, 900)
    shelves, shelf, shelf_w, shelf_h = [], [], pad, 0
    for (name, (pw, ph, prows)) in particles:
        w, h = pw * PARTICLE_SCALE, ph * PARTICLE_SCALE
        if shelf and shelf_w + w + pad > max_w:
            shelves.append((shelf, shelf_h))
            shelf, shelf_w, shelf_h = [], pad, 0
        shelf.append((pw, ph, prows, shelf_w, w, h))
        shelf_w += w + pad
        shelf_h = max(shelf_h, h)
    if shelf:
        shelves.append((shelf, shelf_h))

    particle_h = sum(h + pad for (_s, h) in shelves) + pad
    height = row2_y + particle_h
    canvas = checkerboard(width, height)

    for (bw, bh, brows, iw, ih, irows, x) in first_row:
        blit(canvas, width, height, brows, bw, bh, x, pad, unit)
        grid(canvas, width, height, x, pad, 32, unit)
        # icone do ItemBlock ao lado do atlas, na mesma escala
        blit(canvas, width, height, irows, iw, ih,
             x + bw * unit + 8, pad, unit)

    y = row2_y
    for (shelf, shelf_h) in shelves:
        for (pw, ph, prows, x, w, h) in shelf:
            blit(canvas, width, height, prows, pw, ph, x, y + (shelf_h - h) // 2,
                 PARTICLE_SCALE)
        y += shelf_h + pad

    save_png(OUT, canvas, width, height)
    print("gerado %s (%dx%d)" % (os.path.relpath(OUT, ROOT), width, height))


if __name__ == "__main__":
    main()
