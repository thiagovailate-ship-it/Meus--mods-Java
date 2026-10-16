package com.dynamicwinds.client.render.tesr;

import com.dynamicwinds.content.tileentities.TileEntityWindsock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Manga de vento: mastro fixo + manga que gira contra o vento e estufa conforme
 * a intensidade.
 *
 * <p>Antes esta classe era um placeholder que desenhava tres caixas soltas e
 * desconectadas do vento. Agora a manga e uma sequencia de aneis de tecido, com
 * bandas de cor alternadas (como em uma manga real), secao transversal que
 * cresce com a inflacao e ponta que cai quando o vento esta fraco.</p>
 *
 * <p>A manga aponta para +Z local, igual ao cata-vento, e e rotacionada no
 * eixo do mastro.</p>
 */
public class TESRWindsock extends AbstractBlockTESR {

    private static final ResourceLocation TEX =
        new ResourceLocation("dynamicwinds", "textures/blocks/windsock.png");

    // O atlas da manga repete o padrao de tecido em quatro bandas (linha 0),
    // depois mastro/base/ponta/costura (linha 1) e tecidos extras (linha 2).
    private static final int BAND_ORANGE = AtlasCells.index(0, 0);
    private static final int BAND_WHITE = AtlasCells.index(1, 0);
    private static final int BAND_ORANGE_2 = AtlasCells.index(2, 0);
    private static final int BAND_DARK = AtlasCells.index(3, 0);
    private static final int POLE = AtlasCells.index(0, 1);
    private static final int BASE_PAD = AtlasCells.index(1, 1);
    private static final int FRINGE = AtlasCells.index(2, 1);
    private static final int SEAM = AtlasCells.index(3, 1);
    private static final int WHITE_WEAVE = AtlasCells.index(0, 2);
    private static final int CAP_FABRIC = AtlasCells.index(2, 2);
    private static final int CAP_RING = AtlasCells.index(3, 2);

    /** Centro vertical do eixo dos aneis de tecido. */
    private static final float AXIS_Y = 0.98f;

    @Override
    protected ResourceLocation modelTexture() {
        return TEX;
    }

    @Override
    protected float bobOffset(TileEntity tileEntity, float partialTicks) {
        if (!(tileEntity instanceof TileEntityWindsock)) {
            return 0.0f;
        }
        TileEntityWindsock sock = (TileEntityWindsock) tileEntity;
        if (sock.prevInflation == sock.inflation) {
            return 0.0f;
        }
        // movimento curto da ponta quando a inflacao esta mudando
        return (sock.inflation - sock.prevInflation) * 0.04f;
    }

    @Override
    protected void renderModel(TileEntity tileEntity, float partialTicks) {
        float inflation = 0.5f;
        float yaw = 0.0f;
        float droop = 0.0f;
        if (tileEntity instanceof TileEntityWindsock) {
            TileEntityWindsock sock = (TileEntityWindsock) tileEntity;
            inflation = sock.renderInflation(partialTicks);
            yaw = sock.renderYaw(partialTicks);
            droop = sock.renderDroop(partialTicks);
        }
        if (inflation < 0.0f) inflation = 0.0f;
        if (inflation > 1.0f) inflation = 1.0f;
        float light = brightnessAt(tileEntity, 1);

        // base + mastro (fixos)
        TextureAtlas.boxSides(0.14, 0.00, 0.14, 0.86, 0.07, 0.86, BASE_PAD, BASE_PAD, POLE, light);
        TextureAtlas.boxSides(0.38, 0.07, 0.38, 0.62, 0.97, 0.62, POLE, POLE, POLE, light);
        TextureAtlas.boxSides(0.34, 0.88, 0.34, 0.66, 0.97, 0.66, CAP_RING, CAP_RING, POLE, light);

        // manga
        GL11.glPushMatrix();
        GL11.glTranslated(0.5, 0.0, 0.5);
        GL11.glRotatef(yaw, 0.0f, 1.0f, 0.0f);
        GL11.glTranslated(-0.5, 0.0, -0.5);

        // A secao transversal encolhe quando o vento esta fraco: e a leitura
        // visual de "manga murcha" sem precisar de modelo deformavel.
        float section = 0.40f + 0.60f * inflation;

        segment(0.34, 0.56, 1.00f, section, droop * 0.20f, light, FRINGE, BAND_ORANGE);
        segment(0.56, 0.76, 0.90f, section, droop * 0.45f, light, SEAM, BAND_WHITE);
        segment(0.76, 0.94, 0.76f, section, droop * 0.75f, light, SEAM, BAND_ORANGE_2);
        segment(0.94, 1.06, 0.58f, section, droop, light, SEAM, BAND_DARK);

        GL11.glPopMatrix();
    }

    /**
     * Um anel de tecido. {@code zStart..zEnd} e o trecho do eixo, {@code width} a
     * largura relativa da banda e {@code section} o fator de inflacao.
     *
     * <p>A face -Z leva a boca/costura (o lado preso ao mastro) e a +Z a banda de
     * cor seguinte, de modo que o conjunto vira uma sequencia de aneis.</p>
     */
    private void segment(float zStart, float zEnd, float width, float section,
                         float drop, float light, int mouthCell, int bandCell) {
        float halfWidth = 0.24f * width * section;
        float halfHeight = 0.20f * width * section;
        float centerY = AXIS_Y - drop;
        TextureAtlas.boxUV(0.5f - halfWidth, centerY - halfHeight, zStart,
                           0.5f + halfWidth, centerY + halfHeight, zEnd,
                           bandCell, bandCell, bandCell, bandCell, bandCell, mouthCell,
                           light);
    }
}
