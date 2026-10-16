package com.dynamicwinds.client.render;

import com.dynamicwinds.client.render.tesr.AtlasCells;
import com.dynamicwinds.client.render.tesr.BoxMesh;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida a malha e o mapeamento de UV usados pelos TESRs.
 *
 * <p>Estes testes existem porque a implementacao anterior desenhava as faces
 * com winding invertido (metade delas era descartada pelo culling) e aplicava a
 * textura inteira em todas as faces. Nenhum dos dois defeitos aparece na
 * compilacao - so no jogo - entao eles ficam travados aqui.</p>
 */
class BoxMeshTest {

    @Test
    void facesApontamParaFora() {
        for (int face = 0; face < BoxMesh.FACE_COUNT; face++) {
            float[] a = corner(face, 0);
            float[] b = corner(face, 1);
            float[] c = corner(face, 2);

            float[] normal = cross(sub(b, a), sub(c, b));
            float len = length(normal);
            assertTrue(len > 1.0E-4f, "face " + face + " degenerada");

            float[] expected = BoxMesh.OUTWARD[face];
            float dot = (normal[0] * expected[0] + normal[1] * expected[1] + normal[2] * expected[2]) / len;
            assertEquals(1.0f, dot, 1.0E-4f,
                    "face " + face + " tem winding invertido (normal para dentro do cubo)");
        }
    }

    @Test
    void facesUsamQuatroCantosDistintos() {
        for (int face = 0; face < BoxMesh.FACE_COUNT; face++) {
            int[] seen = new int[BoxMesh.VERTS_PER_FACE];
            for (int v = 0; v < BoxMesh.VERTS_PER_FACE; v++) {
                int corner = BoxMesh.FACES[face][v][0];
                for (int k = 0; k < v; k++) {
                    assertNotEquals(seen[k], corner,
                            "face " + face + " repete o canto " + corner);
                }
                seen[v] = corner;
            }
        }
    }

    @Test
    void facesLateraisTemTexturaEmPe() {
        int[] sides = {BoxMesh.FACE_XP, BoxMesh.FACE_XN, BoxMesh.FACE_ZP, BoxMesh.FACE_ZN};
        for (int face : sides) {
            for (int v = 0; v < BoxMesh.VERTS_PER_FACE; v++) {
                int[] entry = BoxMesh.FACES[face][v];
                float[] corner = BoxMesh.CORNER[entry[0]];
                if (corner[1] == 1f) {
                    assertEquals(1, entry[2],
                            "face " + face + ": vertice de cima deveria usar v1 da celula");
                } else {
                    assertEquals(0, entry[2],
                            "face " + face + ": vertice de baixo deveria usar v0 da celula");
                }
            }
        }
    }

    @Test
    void topoEBaseNaoUsamUVPorAltura() {
        // nas faces de topo/fundo v deve seguir o eixo Z, e nao a altura
        for (int face : new int[] {BoxMesh.FACE_YP, BoxMesh.FACE_YN}) {
            boolean sawBoth = false;
            int[] seen = {-1, -1};
            for (int v = 0; v < BoxMesh.VERTS_PER_FACE; v++) {
                int[] entry = BoxMesh.FACES[face][v];
                float[] corner = BoxMesh.CORNER[entry[0]];
                assertEquals(0f, corner[1],
                        "face " + face + " deveria ser plana em Y");
                seen[entry[2]] = 1;
            }
            sawBoth = seen[0] == 1 && seen[1] == 1;
            assertTrue(sawBoth, "face " + face + " deveria varrer os dois valores de v");
        }
    }

    @Test
    void atlasCellsCobremAImagem() {
        assertEquals(AtlasCells.COLS * AtlasCells.ROWS, 16);
        for (int cell = 0; cell < 16; cell++) {
            int col = AtlasCells.col(cell);
            int row = AtlasCells.row(cell);
            assertTrue(col >= 0 && col < AtlasCells.COLS, "coluna fora do atlas: " + col);
            assertTrue(row >= 0 && row < AtlasCells.ROWS, "linha fora do atlas: " + row);
            assertTrue(AtlasCells.u0(col) >= 0f && AtlasCells.u1(col) <= 1f, "u fora de 0..1");
            assertTrue(AtlasCells.v0(row) >= 0f && AtlasCells.v1(row) <= 1f, "v fora de 0..1");
            // a linha 0 fica no topo do PNG, portanto no maior v do OpenGL
            assertTrue(AtlasCells.v0(row) < AtlasCells.v1(row), "v0 deve ser menor que v1");
            if (row == 0) {
                assertEquals(0.75f, AtlasCells.v0(row), 1.0E-6f);
                assertEquals(1.0f, AtlasCells.v1(row), 1.0E-6f);
            }
        }
        assertEquals(0, AtlasCells.index(0, 0));
        assertEquals(15, AtlasCells.index(3, 3));
    }

    private static float[] corner(int face, int vertex) {
        return BoxMesh.CORNER[BoxMesh.FACES[face][vertex][0]];
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }

    private static float length(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }
}
