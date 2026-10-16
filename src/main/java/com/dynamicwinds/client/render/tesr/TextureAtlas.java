package com.dynamicwinds.client.render.tesr;

import org.lwjgl.opengl.GL11;

/**
 * Emissao de geometria texturizada dos TESRs (immediate mode, como em 1.7.10).
 *
 * <p>Cada cubo e desenhado como 6 quads independentes. Como o UV e dado por
 * celula do atlas, as faces nao repetem a textura inteira esticada - o defeito
 * do codigo anterior, em que todas as faces recebiam o intervalo [0,0]-[1,1].</p>
 *
 * <p>As faces recebem um fator de sombreamento fixo por orientacao, ja que o
 * modelo e desenhado com luz constante; isso e o que faz um cubo branco parecer
 * um cubo e nao um borrao chapado.</p>
 */
public final class TextureAtlas {

    /**
     * Sombreamento por face, na ordem de {@link BoxMesh#FACES}:
     * +X, -X, +Y, -Y, +Z, -Z. Topo mais claro, fundo bem mais escuro,
     * laterais opostas com valores diferentes.
     */
    public static final float[] FACE_SHADE = {0.76f, 0.68f, 1.00f, 0.48f, 0.90f, 0.82f};

    private TextureAtlas() {}

    /** Todos os 6 indices de face iguais. */
    public static int[] all(int cell) {
        return new int[] {cell, cell, cell, cell, cell, cell};
    }

    /** Monta o conjunto de UV na ordem esperada por {@link #boxUV}. */
    public static int[] faces(int cellZPos, int cellZNeg, int cellXPos, int cellXNeg,
                             int cellYPos, int cellYNeg) {
        return new int[] {cellXPos, cellXNeg, cellYPos, cellYNeg, cellZPos, cellZNeg};
    }

    /**
     * Desenha um cubo texturizado. A textura precisa estar ligada
     * ({@code GL_TEXTURE_2D}) e o atlas ja vinculado.
     */
    public static void boxUV(double x1, double y1, double z1, double x2, double y2, double z2,
                             int cellXPos, int cellXNeg, int cellYPos, int cellYNeg,
                             int cellZPos, int cellZNeg, float brightness) {
        GL11.glBegin(GL11.GL_QUADS);
        for (int face = 0; face < BoxMesh.FACE_COUNT; face++) {
            int cell;
            switch (face) {
                case BoxMesh.FACE_XP: cell = cellXPos; break;
                case BoxMesh.FACE_XN: cell = cellXNeg; break;
                case BoxMesh.FACE_YP: cell = cellYPos; break;
                case BoxMesh.FACE_YN: cell = cellYNeg; break;
                case BoxMesh.FACE_ZP: cell = cellZPos; break;
                default:              cell = cellZNeg; break;
            }
            float shade = FACE_SHADE[face] * brightness;
            GL11.glColor4f(shade, shade, shade, 1.0f);
            for (int v = 0; v < BoxMesh.VERTS_PER_FACE; v++) {
                int[] entry = BoxMesh.FACES[face][v];
                GL11.glTexCoord2f(AtlasCells.uFor(cell, entry[1]), AtlasCells.vFor(cell, entry[2]));
                float[] corner = BoxMesh.CORNER[entry[0]];
                GL11.glVertex3d(x1 + (x2 - x1) * corner[0],
                                y1 + (y2 - y1) * corner[1],
                                z1 + (z2 - z1) * corner[2]);
            }
        }
        GL11.glEnd();
    }

    /** Cubo com a mesma celula em todas as faces. */
    public static void boxSingle(double x1, double y1, double z1, double x2, double y2, double z2,
                                 int cell, float brightness) {
        boxUV(x1, y1, z1, x2, y2, z2, cell, cell, cell, cell, cell, cell, brightness);
    }

    /** Cubo com celula por familia de face (topo, lateral e fundo). */
    public static void boxSides(double x1, double y1, double z1, double x2, double y2, double z2,
                                int cellSides, int cellTop, int cellBottom, float brightness) {
        boxUV(x1, y1, z1, x2, y2, z2,
              cellSides, cellSides, cellTop, cellBottom, cellSides, cellSides, brightness);
    }
}
