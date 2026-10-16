package com.dynamicwinds.client.render.tesr;

/**
 * Enderecamento de celulas dentro do atlas 4x4 de 128x128 usado pelos blocos.
 *
 * <p>O atlas e dividido em 16 celulas de 32x32 px. A numeracao interna segue a
 * ordem de leitura da imagem: {@code celula = linha * 4 + coluna}, com a linha 0
 * no topo da imagem. Como o OpenGL usa v = 0 na base da textura, {@link #v0} e
 * {@link #v1} fazem a conversao - inverter isso deixaria o bloco de cabeca para
 * baixo.</p>
 *
 * <p>As constantes precisam acompanhar o layout definido em
 * {@code tools/gen_textures.py}.</p>
 */
public final class AtlasCells {

    public static final int COLS = 4;
    public static final int ROWS = 4;
    public static final int CELL_PX = 32;
    public static final float U_SIZE = 1.0f / COLS;
    public static final float V_SIZE = 1.0f / ROWS;

    private AtlasCells() {}

    /** Converte (coluna, linha) no indice de celula usado pelos TESRs. */
    public static int index(int col, int row) {
        return row * COLS + col;
    }

    public static int col(int cell) {
        return cell % COLS;
    }

    public static int row(int cell) {
        return cell / COLS;
    }

    public static float u0(int col) {
        return col * U_SIZE;
    }

    public static float u1(int col) {
        return (col + 1) * U_SIZE;
    }

    /** v=0 e a base da imagem em OpenGL, ou seja, a ultima linha da celula no PNG. */
    public static float v0(int row) {
        return 1.0f - (row + 1) * V_SIZE;
    }

    public static float v1(int row) {
        return 1.0f - row * V_SIZE;
    }

    public static float u(int col, int sel) {
        return sel == 0 ? u0(col) : u1(col);
    }

    public static float v(int row, int sel) {
        return sel == 0 ? v0(row) : v1(row);
    }

    /** UV de um canto da celula, com selecao (u, v) em {0,1}. */
    public static float uFor(int cell, int sel) {
        return u(col(cell), sel);
    }

    public static float vFor(int cell, int sel) {
        return v(row(cell), sel);
    }
}
