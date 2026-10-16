package com.dynamicwinds.client.render.tesr;

import com.dynamicwinds.content.tileentities.TileEntityAnemometer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Anemometro de conchas: poste, cubo e tres bracos com conchas que giram
 * conforme a velocidade do vento, alem de um pequeno mostrador no topo.
 *
 * <p>Esta classe nao existia - o anemometro emitia redstone mas era invisivel
 * no mundo (nao havia TESR nem icone proprios).</p>
 */
public class TESRAnemometer extends AbstractBlockTESR {

    private static final ResourceLocation TEX =
        new ResourceLocation("dynamicwinds", "textures/blocks/anemometer.png");

    private static final int POLE = AtlasCells.index(0, 0);
    private static final int BASE_PAD = AtlasCells.index(1, 0);
    private static final int HUB = AtlasCells.index(2, 0);
    private static final int CUP_OUTER = AtlasCells.index(3, 0);
    private static final int CUP_INNER = AtlasCells.index(0, 1);
    private static final int ARM = AtlasCells.index(1, 1);
    private static final int METAL_LIGHT = AtlasCells.index(2, 1);
    private static final int RUBBER = AtlasCells.index(3, 1);
    private static final int GAUGE_FACE = AtlasCells.index(2, 2);

    private static final int ARM_COUNT = 3;

    /** Centro do rotor, em coordenadas de bloco. */
    private static final double ROTOR_X = 0.5;
    private static final double ROTOR_Z = 0.5;

    @Override
    protected ResourceLocation modelTexture() {
        return TEX;
    }

    @Override
    protected void renderModel(TileEntity tileEntity, float partialTicks) {
        float rotation = 0.0f;
        float direction = 0.0f;
        if (tileEntity instanceof TileEntityAnemometer) {
            TileEntityAnemometer anemometer = (TileEntityAnemometer) tileEntity;
            rotation = anemometer.renderRotation(partialTicks);
            direction = anemometer.renderDirection(partialTicks);
        }
        float light = brightnessAt(tileEntity, 1);

        // base e poste
        TextureAtlas.boxSides(0.10, 0.00, 0.10, 0.90, 0.09, 0.90, BASE_PAD, BASE_PAD, POLE, light);
        TextureAtlas.boxSides(0.37, 0.09, 0.37, 0.63, 0.80, 0.63, POLE, POLE, POLE, light);

        // anilha de vedacao e cubo
        TextureAtlas.boxSides(0.34, 0.78, 0.34, 0.66, 0.86, 0.66, RUBBER, RUBBER, POLE, light);
        TextureAtlas.boxSingle(0.34, 0.86, 0.34, 0.66, 1.00, 0.66, HUB, light);

        // mostrador no topo do cubo
        TextureAtlas.boxUV(0.41, 1.00, 0.41, 0.59, 1.05, 0.59,
                           METAL_LIGHT, METAL_LIGHT, GAUGE_FACE, METAL_LIGHT,
                           METAL_LIGHT, METAL_LIGHT, light);

        // rotor: tres bracos com conchas
        GL11.glPushMatrix();
        GL11.glTranslated(ROTOR_X, 0.0, ROTOR_Z);
        GL11.glRotatef(rotation, 0.0f, 1.0f, 0.0f);
        for (int i = 0; i < ARM_COUNT; i++) {
            GL11.glPushMatrix();
            GL11.glRotatef(120.0f * i, 0.0f, 1.0f, 0.0f);
            GL11.glTranslated(-ROTOR_X, 0.0, -ROTOR_Z);

            // braco horizontal
            TextureAtlas.boxUV(0.56, 0.885, 0.465, 0.92, 0.925, 0.535,
                               ARM, ARM, METAL_LIGHT, ARM, ARM, ARM, light);
            // concha: face externa (+Z) e interna (-Z)
            TextureAtlas.boxUV(0.90, 0.79, 0.44, 1.06, 1.02, 0.56,
                               CUP_OUTER, CUP_OUTER, CUP_OUTER, CUP_OUTER,
                               CUP_OUTER, CUP_INNER, light);
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();

        // bandeirola de direcao: placa pequena que aponta para o vento
        GL11.glPushMatrix();
        GL11.glTranslated(0.5, 0.0, 0.5);
        GL11.glRotatef(direction - 90.0f, 0.0f, 1.0f, 0.0f);
        GL11.glTranslated(-0.5, 0.0, -0.5);
        TextureAtlas.boxUV(0.46, 0.86, 0.68, 0.54, 0.94, 0.94,
                           METAL_LIGHT, METAL_LIGHT, METAL_LIGHT, METAL_LIGHT,
                           ARM, ARM, light);
        GL11.glPopMatrix();
    }
}
