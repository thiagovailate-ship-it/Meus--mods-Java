package com.dynamicwinds.content;

/**
 * Tipos de renderizacao usados pelos blocos do mod.
 *
 * <p>{@link #DYNAMICWINDS_INSTRUMENT} e um tipo proprio, que nao esta na tabela
 * de {@code RenderBlocks.renderBlockByRenderType}. O efeito pratico e que o
 * bloco nao entra na malha do chunk - quem desenha e o TESR. Isso e necessario
 * aqui porque o modelo do instrumento depende do estado do vento e nao pode ser
 * "assado" em uma malha estatica.</p>
 *
 * <p>Como o tipo nao esta na lista de {@code RenderBlocks.renderItemIn3d}, o
 * ItemBlock cai automaticamente no caminho de icone 2D, que e o que faz o item
 * aparecer no inventario (veja os sprites {@code *_item.png}).</p>
 */
public final class RenderTypes {

    public static final int DYNAMICWINDS_INSTRUMENT = 63;

    private RenderTypes() {}
}
