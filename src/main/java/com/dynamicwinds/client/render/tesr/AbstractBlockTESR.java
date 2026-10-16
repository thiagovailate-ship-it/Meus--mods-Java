package com.dynamicwinds.client.render.tesr;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * Base comum dos TESRs dos instrumentos de vento.
 *
 * <p>Cuida do que todos precisam e e facil esquecer: empilhar a matriz, deslocar
 * a origem para o centro do bloco, preservar o estado do OpenGL (o TESR roda no
 * meio do passe de terreno, entao deixar textura/blend/cor alterados quebra o
 * que e desenhado depois), ligar blending e aplicar a luz do ambiente.</p>
 *
 * <p>O metodo concreto {@code renderTileEntityAt} mantem a assinatura exigida
 * pelo 1.7.10 deste ambiente (sem {@code destroyStage}). As subclasses apenas
 * descrevem a geometria em {@link #renderModel} usando coordenadas de bloco
 * (0..1).</p>
 */
public abstract class AbstractBlockTESR extends TileEntitySpecialRenderer {

    /** @return textura do atlas do bloco renderizado por esta subclasse */
    protected abstract ResourceLocation modelTexture();

    /**
     * Desenha o modelo em coordenadas locais do bloco (0..1).
     *
     * @param tileEntity   tile entity do instrumento (a subclasse faz o cast)
     * @param partialTicks fracao do tick atual, para interpolar o movimento
     */
    protected abstract void renderModel(TileEntity tileEntity, float partialTicks);

    /**
     * Deslocamento vertical extra aplicado ao modelo inteiro.
     *
     * @param tileEntity   tile entity do instrumento
     * @param partialTicks fracao do tick atual
     * @return deslocamento em blocos (0 = nenhum)
     */
    protected float bobOffset(TileEntity tileEntity, float partialTicks) {
        return 0.0f;
    }

    @Override
    public void renderTileEntityAt(TileEntity tileEntity, double x, double y, double z,
                                   float partialTicks) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glTranslated(x, y, z);
            float bob = bobOffset(tileEntity, partialTicks);
            if (bob != 0.0f) {
                GL11.glTranslated(0.0, bob, 0.0);
            }

            bindTexture(modelTexture());

            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glShadeModel(GL11.GL_FLAT);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.02f);
            // 1.7.10 mantem uma color de "draw mode" no estado da textura; em
            // brilho alto um glColor4f > 1 estoura para branco sem isso.
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

            renderModel(tileEntity, partialTicks);
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    /**
     * Brilho do bloco (0.35..1.0) a partir do nivel de luz combinado.
     * Sem isso os instrumentos ficam com brilho de ceu aberto dentro de cavernas.
     *
     * @param tileEntity tile entity do instrumento (pode ser null)
     * @param dy         deslocamento em Y usado para escolher o bloco de luz
     * @return fator de brilho entre 0.35 e 1.0
     */
    protected static float brightnessAt(TileEntity tileEntity, int dy) {
        if (tileEntity == null) {
            return 1.0f;
        }
        World world = tileEntity.getWorldObj();
        if (world == null) {
            return 1.0f;
        }
        int level = world.getBlockLightValue(tileEntity.xCoord, tileEntity.yCoord + dy,
                                             tileEntity.zCoord);
        if (level < 0) {
            level = 0;
        } else if (level > 15) {
            level = 15;
        }
        float f = level / 15.0f;
        return 0.35f + 0.65f * (f * f);
    }
}
