package com.dynamicwinds.client.render.tesr;

import com.dynamicwinds.content.tileentities.TileEntityWindVane;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Cata-vento: poste fixo + conjunto (seta, cauda e cubo) que gira conforme a
 * direcao do vento.
 *
 * <p>O modelo aponta para +Z quando o angulo e zero, o mesmo eixo usado por
 * {@code TileEntityWindVane}. A rotacao e interpolada por {@code partialTicks}
 * para o movimento ficar suave entre ticks.</p>
 */
public class TESRWindVane extends AbstractBlockTESR {

    private static final ResourceLocation TEX =
        new ResourceLocation("dynamicwinds", "textures/blocks/wind_vane.png");

    // Celulas do atlas: linha 0 = poste, tampa, haste, cubo
    private static final int POST = AtlasCells.index(0, 0);
    private static final int POST_CAP = AtlasCells.index(1, 0);
    private static final int HUB = AtlasCells.index(3, 0);
    // linha 1 = aleta (cauda), ponta da seta, base de madeira, aco claro
    private static final int FIN = AtlasCells.index(0, 1);
    private static final int ARROW = AtlasCells.index(1, 1);
    private static final int BASE_PAD = AtlasCells.index(2, 1);
    private static final int STEEL_LIGHT = AtlasCells.index(3, 1);
    // linha 2 = ferrugem, parafuso, haste de bronze, tabua
    private static final int RUST = AtlasCells.index(0, 2);
    private static final int BOLT = AtlasCells.index(1, 2);
    private static final int BRASS = AtlasCells.index(2, 2);
    private static final int PLANK = AtlasCells.index(3, 2);

    @Override
    protected ResourceLocation modelTexture() {
        return TEX;
    }

    @Override
    protected void renderModel(TileEntity tileEntity, float partialTicks) {
        float yaw = 0.0f;
        if (tileEntity instanceof TileEntityWindVane) {
            yaw = ((TileEntityWindVane) tileEntity).renderAngle(partialTicks);
        }
        float light = brightnessAt(tileEntity, 1);

        // base: tabua + chapa
        TextureAtlas.boxSides(0.06, 0.00, 0.06, 0.94, 0.08, 0.94, PLANK, BASE_PAD, PLANK, light);
        TextureAtlas.boxSides(0.26, 0.08, 0.26, 0.74, 0.13, 0.74, BASE_PAD, BASE_PAD, PLANK, light);

        // poste de aco com luva de fixacao
        TextureAtlas.boxSides(0.33, 0.13, 0.33, 0.67, 0.62, 0.67, POST, POST, POST, light);
        TextureAtlas.boxSides(0.30, 0.20, 0.30, 0.70, 0.27, 0.70, RUST, RUST, POST, light);
        TextureAtlas.boxSides(0.29, 0.62, 0.29, 0.71, 0.71, 0.71, STEEL_LIGHT, POST_CAP, POST, light);

        // eixo de bronze (fixo, nao gira)
        TextureAtlas.boxSingle(0.45, 0.71, 0.45, 0.55, 0.93, 0.55, BRASS, light);

        // conjunto movel: gira em torno do eixo do poste
        GL11.glPushMatrix();
        GL11.glTranslated(0.5, 0.0, 0.5);
        GL11.glRotatef(yaw, 0.0f, 1.0f, 0.0f);
        GL11.glTranslated(-0.5, 0.0, -0.5);

        // cubo de bronze
        TextureAtlas.boxSingle(0.37, 0.90, 0.37, 0.63, 1.07, 0.63, HUB, light);
        TextureAtlas.boxSingle(0.44, 1.07, 0.44, 0.56, 1.13, 0.56, BOLT, light);

        // seta apontando para +Z (direcao do vento)
        TextureAtlas.boxSides(0.29, 0.955, 0.74, 0.71, 1.005, 1.09, ARROW, ARROW, ARROW, light);
        TextureAtlas.boxSingle(0.43, 0.95, 0.60, 0.57, 1.01, 0.80, STEEL_LIGHT, light);

        // cauda: placa e duas penas laterais
        TextureAtlas.boxSides(0.35, 0.955, 0.05, 0.65, 1.005, 0.34, FIN, FIN, FIN, light);
        TextureAtlas.boxSides(0.17, 0.960, 0.13, 0.35, 0.995, 0.36, FIN, FIN, FIN, light);
        TextureAtlas.boxSides(0.65, 0.960, 0.13, 0.83, 0.995, 0.36, FIN, FIN, FIN, light);

        GL11.glPopMatrix();
    }
}
