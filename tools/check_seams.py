"""
DynamicWinds - conferencia da costura das texturas de ambiente (Python puro).

As texturas de ambiente (teto de nuvem e parede do olho) sao repetidas pela
malha: o teto repete ~3x no anel e ~2,4x no raio, a parede repete a cada ~40
blocos de altura. Se o ruido nao fechar o tile, cada repeticao desenha uma linha
reta atravessando o ceu - e como o defeito e geometrico (o periodo do ruido nao
divide o lado da textura), ele nao aparece em nenhum lugar a nao ser olhando.

O teste aqui e direto: numa textura que fecha, a ultima coluna e vizinha da
primeira (e a ultima linha e vizinha da primeira) quando a textura se repete.
Entao a diferenca entre elas deve ser do mesmo tamanho que a diferenca entre
duas colunas vizinhas quaisquer. A razao entre as duas medidas e o numero que
importa; acima de 1,6 ja se ve a emenda.

Uso: python3 tools/check_seams.py
"""

import os
import struct
import sys
import zlib
from gen_textures import SEAM_RATIO_LIMIT

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENVIRONMENT = os.path.join(ROOT, "src/main/resources/assets/dynamicwinds/textures/environment")

# Razao a partir da qual a costura aparece como linha reta (fonte no gerador).
LIMITE = SEAM_RATIO_LIMIT


def read_png(path):
    """Le um PNG RGBA8 e devolve (w, h, linhas), aplicando os filtros."""
    data = open(path, "rb").read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("nao e PNG: " + path)
    pos = 8
    w = h = None
    idat = bytearray()
    while pos + 8 <= len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            w, h, depth, color = struct.unpack(">IIBB", payload[:10])
            if depth != 8 or color != 6:
                raise ValueError("esperado RGBA8 em " + path)
        elif tag == b"IDAT":
            idat += payload
        elif tag == b"IEND":
            break
    raw = zlib.decompress(bytes(idat))
    bpp = 4
    stride = w * bpp
    rows = []
    prev = bytearray(stride)
    i = 0
    for _ in range(h):
        filt = raw[i]
        i += 1
        line = bytearray(raw[i:i + stride])
        i += stride
        if filt == 1:
            for x in range(bpp, stride):
                line[x] = (line[x] + line[x - bpp]) & 0xFF
        elif filt == 2:
            for x in range(stride):
                line[x] = (line[x] + prev[x]) & 0xFF
        elif filt == 3:
            for x in range(stride):
                left = line[x - bpp] if x >= bpp else 0
                line[x] = (line[x] + ((left + prev[x]) >> 1)) & 0xFF
        elif filt == 4:
            for x in range(stride):
                a = line[x - bpp] if x >= bpp else 0
                b = prev[x]
                c = prev[x - bpp] if x >= bpp else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pred) & 0xFF
        prev = line
        rows.append(line)
    return w, h, rows


def _diff(p, q):
    """Diferenca media dos canais entre dois pixels."""
    return (abs(p[0] - q[0]) + abs(p[1] - q[1]) + abs(p[2] - q[2])) / 3.0


def measure(path):
    """
    Devolve (razao_vertical, razao_horizontal, salto_v, interno_v, salto_h, interno_h).

    "Vertical" aqui e a emenda que aparece quando a textura repete na direcao x
    (a ultima coluna encosta na primeira).
    """
    w, h, rows = read_png(path)
    seam_v = sum(_diff(rows[y][0:4], rows[y][(w - 1) * 4:w * 4]) for y in range(h)) / h
    seam_h = sum(_diff(rows[0][x * 4:x * 4 + 4], rows[h - 1][x * 4:x * 4 + 4])
                 for x in range(w)) / w
    inner_v = sum(_diff(rows[y][x * 4:x * 4 + 4], rows[y][(x + 1) * 4:(x + 2) * 4])
                  for y in range(h) for x in range(w - 1)) / float(h * (w - 1))
    inner_h = sum(_diff(rows[y][x * 4:x * 4 + 4], rows[y + 1][x * 4:x * 4 + 4])
                  for y in range(h - 1) for x in range(w)) / float((h - 1) * w)
    return (seam_v / inner_v if inner_v else 0.0,
            seam_h / inner_h if inner_h else 0.0,
            seam_v, inner_v, seam_h, inner_h)


def main():
    # Sem argumentos, confere as texturas de ambiente; com argumentos, confere os
    # arquivos dados (util para comparar com uma versao antiga).
    if len(sys.argv) > 1:
        paths = [(p, p) for p in sys.argv[1:]]
    else:
        paths = [(f, os.path.join(ENVIRONMENT, f))
                 for f in sorted(os.listdir(ENVIRONMENT)) if f.endswith(".png")]
    if not paths:
        print("nenhuma textura encontrada")
        return 1
    falhas = 0
    for name, path in paths:
        ratio_v, ratio_h, seam_v, inner_v, seam_h, inner_h = measure(path)
        pior = max(ratio_v, ratio_h)
        if pior > LIMITE:
            eixo = "horizontal (repete em y)" if ratio_h >= ratio_v else "vertical (repete em x)"
            verdict = "COSTURA VISIVEL na direcao " + eixo
            falhas += 1
        else:
            verdict = "OK"
        print("%-20s emenda vertical x%.2f (%.1f vs %.1f interno)  "
              "horizontal x%.2f (%.1f vs %.1f)  %s"
              % (name, ratio_v, seam_v, inner_v, ratio_h, seam_h, inner_h, verdict))
    print("limite: razao %.1f, conferida nas duas direcoes" % LIMITE)
    return 1 if falhas else 0


if __name__ == "__main__":
    sys.exit(main())
