package com.dynamicwinds.client.render.tesr;

/**
 * Descricao estatica da malha de um cubo alinhado aos eixos, usada pelos TESRs.
 *
 * <p>Nao depende de nenhuma classe do Minecraft: e apenas tabela de dados, o que
 * permite validar winding (orientacao das faces) e UV em teste unitario, sem
 * abrir o jogo. Essa validacao e importante porque a implementacao anterior
 * ({@code renderTexturedBox} com GL11 direto) tinha metade das faces com
 * winding invertido - elas eram descartadas pelo culling e o bloco ficava
 * "vazado" visto de alguns lados.</p>
 *
 * <p>Os vertices de cada face estao em sentido anti-horario quando vistos de
 * fora do cubo (convencao de face frontal do OpenGL).</p>
 */
public final class BoxMesh {

    /** Indices das faces. Devem casar com a ordem dos parametros de UV. */
    public static final int FACE_XP = 0;
    public static final int FACE_XN = 1;
    public static final int FACE_YP = 2;
    public static final int FACE_YN = 3;
    public static final int FACE_ZP = 4;
    public static final int FACE_ZN = 5;
    public static final int FACE_COUNT = 6;
    public static final int VERTS_PER_FACE = 4;

    /**
     * Cantos do cubo em coordenadas normalizadas (0 = x1/y1/z1, 1 = x2/y2/z2).
     * O indice e formado pelos bits (x, y, z): bit0 = x, bit1 = y, bit2 = z.
     */
    public static final float[][] CORNER = {
        {0f, 0f, 0f}, // 0
        {1f, 0f, 0f}, // 1
        {0f, 0f, 1f}, // 2
        {1f, 0f, 1f}, // 3
        {0f, 1f, 0f}, // 4
        {1f, 1f, 0f}, // 5
        {0f, 1f, 1f}, // 6
        {1f, 1f, 1f}  // 7
    };

    /**
     * Vertices de cada face. Ultima dimensao:
     * {@code { indice do canto, selecao de u (0 = u0, 1 = u1), selecao de v (0 = v0, 1 = v1) }}.
     *
     * <p>A escolha de u/v mantem a textura "em pe" e sem espelhamento quando a
     * face e olhada de frente: nas faces laterais v cresce para cima (+Y), nas
     * faces de topo/fundo u cresce para +X.</p>
     */
    public static final int[][][] FACES = {
        // +X (leste): olhando de +X, a direita do observador e -Z
        {{1, 1, 0}, {5, 1, 1}, {7, 0, 1}, {3, 0, 0}},
        // -X (oeste): a direita do observador e +Z
        {{0, 0, 0}, {2, 1, 0}, {6, 1, 1}, {4, 0, 1}},
        // +Y (topo): u = +X, v = -Z
        {{4, 0, 1}, {6, 0, 0}, {7, 1, 0}, {5, 1, 1}},
        // -Y (fundo): u = +X, v = +Z
        {{2, 0, 1}, {0, 0, 0}, {1, 1, 0}, {3, 1, 1}},
        // +Z (sul): a direita do observador e +X
        {{2, 0, 0}, {3, 1, 0}, {7, 1, 1}, {6, 0, 1}},
        // -Z (norte): a direita do observador e -X
        {{1, 0, 0}, {0, 1, 0}, {4, 1, 1}, {5, 0, 1}}
    };

    /** Normal esperada (para fora) de cada face, na mesma ordem de {@link #FACES}. */
    public static final float[][] OUTWARD = {
        {1f, 0f, 0f},
        {-1f, 0f, 0f},
        {0f, 1f, 0f},
        {0f, -1f, 0f},
        {0f, 0f, 1f},
        {0f, 0f, -1f}
    };

    private BoxMesh() {}
}
