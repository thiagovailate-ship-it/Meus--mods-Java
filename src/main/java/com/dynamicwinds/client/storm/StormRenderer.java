package com.dynamicwinds.client.storm;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.StormKind;
import com.dynamicwinds.core.StormSystem;
import com.dynamicwinds.integration.WindConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Desenha as tempestades no mundo: teto de nuvem, parede do olho (ou funil, no
 * caso do vortice) e bandas espirais de chuva.
 *
 * <p><b>Por que desenhar a mao em vez de mudar as nuvens do Minecraft.</b> As
 * nuvens do vanilla sao um plano unico e global, sem relacao com nada; para
 * deslocar, curvar ou abrir um olho nelas seria preciso interceptar aquele
 * render. Aqui as nuvens do sistema sao geometria propria, o que permite que a
 * forma seja <i>derivada da fisica</i>: o raio do olho e o raio do vento maximo,
 * a altura do teto e o topo da coluna do tipo, e a rotacao e a mesma do campo de
 * vento. Quando o sistema se expande ao enfraquecer, a nuvem se expande junto.</p>
 *
 * <p>Tudo e desenhado em coordenadas de mundo dentro do
 * {@code RenderWorldLastEvent}, com desenho de tras para frente e escrita de
 * profundidade desligada, que e o que permite as camadas translucidas se
 * comporem sem brigar por z-buffer.</p>
 */
public final class StormRenderer {

    /** Teto de nuvem (celulas, com estrutura de nuvem real). */
    private static final ResourceLocation CLOUD_TEX =
            new ResourceLocation("dynamicwinds:textures/environment/storm_clouds.png");
    /** Parede do olho e cortina de chuva. */
    private static final ResourceLocation WALL_TEX =
            new ResourceLocation("dynamicwinds:textures/environment/storm_wall.png");

    private static final int DECK_SEGMENTS = 72;
    private static final int WALL_SEGMENTS = 56;
    private static final int BAND_ARMS = 3;
    private static final int BAND_STEPS = 22;
    private static final double TWO_PI = Math.PI * 2.0;

    private StormRenderer() {}

    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (!WindConfig.enableParticles) return;

        StormField field = Wind.stormsFor(mc.theWorld);
        if (field == null) return;

        long time = mc.theWorld.getTotalWorldTime();
        List<StormSystem> active = field.activeAt(time);
        if (active.isEmpty()) return;

        EntityPlayer p = mc.thePlayer;
        double px = p.prevPosX + (p.posX - p.prevPosX) * partialTicks;
        double py = p.prevPosY + (p.posY - p.prevPosY) * partialTicks;
        double pz = p.prevPosZ + (p.posZ - p.prevPosZ) * partialTicks;
        double now = time + partialTicks;

        // desenho de tras para frente: sem isso, duas tempestades sobrepostas
        // apareceriam com a de tras por cima
        List<StormSystem> visible = new ArrayList<StormSystem>(active.size());
        double maxDist = WindConfig.stormMaxViewDistance();
        for (int i = 0; i < active.size(); i++) {
            StormSystem s = active.get(i);
            double dx = s.centerX(time) - px;
            double dz = s.centerZ(time) - pz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > maxDist + s.outerRadius()) continue;
            visible.add(s);
        }
        if (visible.isEmpty()) return;
        final long sortTime = time;
        final double vx = px, vz = pz;
        Collections.sort(visible, new Comparator<StormSystem>() {
            @Override
            public int compare(StormSystem a, StormSystem b) {
                return Double.compare(b.distanceTo(sortTime, vx, vz),
                                      a.distanceTo(sortTime, vx, vz));
            }
        });

        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz);
        beginTranslucent();

        for (int i = 0; i < visible.size(); i++) {
            drawSystem(mc, visible.get(i), now, time, px, py, pz);
        }

        endTranslucent();
        GL11.glPopMatrix();
    }

    // ------------------------------------------------------------------

    private static void drawSystem(Minecraft mc, StormSystem s, double now, long time,
                                   double px, double py, double pz) {
        double intensity = s.intensityAt(time);
        if (intensity < 0.03) return;

        double cx = s.centerX(time);
        double cz = s.centerZ(time);
        double rmax = s.rmaxAt(time);
        double outer = s.outerRadius();
        boolean narrow = s.kind == StormKind.VORTEX;
        // sentido de rotacao igual ao do campo de vento (hemisferio)
        double spin = s.north ? 1.0 : -1.0;
        double phase = spin * now * (narrow ? 0.09 : 0.020);

        int ground = mc.theWorld.getHeightValue((int) Math.floor(cx), (int) Math.floor(cz));
        double groundY = ground;
        double top = StormKind.columnTop(s.kind);
        double deckY = Math.min(groundY + top * 0.55, 214.0);
        double baseY = narrow ? groundY - 1.0 : groundY + 2.0;

        // perto, desenha inteiro; longe, desaparece sem corte seco
        double camDist = Math.sqrt((cx - px) * (cx - px) + (cz - pz) * (cz - pz));
        double viewFade = 1.0 - max0(camDist - outer * 0.6) / Math.max(1.0, outer * 1.6);
        double fade = Math.max(0.0, Math.min(1.0, viewFade));

        // 1) cortina de chuva / parede do olho
        if (narrow) {
            // o funil se inclina para tras em relacao ao deslocamento, como um
            // vortice que arrasta a base atras do topo
            double tx = s.translationX(time);
            double tz = s.translationZ(time);
            double tl = Math.sqrt(tx * tx + tz * tz);
            double leanX = tl > 1.0e-9 ? -tx / tl : 0.0;
            double leanZ = tl > 1.0e-9 ? -tz / tl : 0.0;
            drawFunnel(mc, cx, cz, groundY, deckY, rmax, intensity, phase, fade, leanX, leanZ);
        } else {
            drawCylinder(mc, cx, cz, baseY, deckY, rmax * 1.02, intensity, phase, fade, 0.60);
            drawCylinder(mc, cx, cz, baseY, deckY * 0.99, rmax * 1.45, intensity, phase, fade, 0.26);
        }

        // 2) bandas espirais de chuva, girando com o sistema
        drawBands(mc, cx, cz, groundY, rmax, outer, intensity, phase, fade, spin);

        // 3) teto de nuvem, deixando o olho aberto
        drawDeck(mc, cx, cz, deckY, rmax * (narrow ? 1.35 : 0.9), outer * 1.04,
                intensity, phase, fade);
    }

    /**
     * Teto de nuvem: anel entre a parede do olho e a borda externa.
     *
     * <p>O olho fica aberto de proposito - e a assinatura visual de um ciclone, e
     * e exatamente onde o modelo de vento diz que nao ha chuva.</p>
     */
    private static void drawDeck(Minecraft mc, double cx, double cz, double deckY,
                                 double rIn, double rOut, double intensity,
                                 double phase, double fade) {
        if (rOut <= rIn) return;
        mc.getTextureManager().bindTexture(CLOUD_TEX);
        net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.instance;
        t.startDrawingQuads();
        t.setNormal(0.0F, 1.0F, 0.0F);

        int rings = 5;
        double shade = 0.34 + 0.30 * intensity;
        for (int ring = 0; ring < rings; ring++) {
            double f0 = ring / (double) rings;
            double f1 = (ring + 1) / (double) rings;
            double ra = rIn + (rOut - rIn) * f0;
            double rb = rIn + (rOut - rIn) * f1;
            // a nuvem desce um pouco para fora (o teto nao e um disco plano)
            double ya = deckY - f0 * f0 * 18.0;
            double yb = deckY - f1 * f1 * 18.0;
            float aa = (float) (alphaDeck(f0) * fade * (0.55 + 0.45 * intensity));
            float ab = (float) (alphaDeck(f1) * fade * (0.55 + 0.45 * intensity));
            for (int seg = 0; seg < DECK_SEGMENTS; seg++) {
                double a0 = (seg / (double) DECK_SEGMENTS) * TWO_PI + phase;
                double a1 = ((seg + 1) / (double) DECK_SEGMENTS) * TWO_PI + phase;
                double ca0 = Math.cos(a0), sa0 = Math.sin(a0);
                double ca1 = Math.cos(a1), sa1 = Math.sin(a1);

                double u0 = seg / (double) DECK_SEGMENTS * 3.0;
                double u1 = (seg + 1) / (double) DECK_SEGMENTS * 3.0;
                float v0 = (float) (f0 * 2.4);
                float v1 = (float) (f1 * 2.4);

                // cores por vertice: a nuvem escurece para fora
                float cA = (float) (shade * (1.0 - f0 * 0.35));
                float cB = (float) (shade * (1.0 - f1 * 0.35));
                t.setColorRGBA_F(cA, cA * 1.04F, cA * 1.16F, aa);
                t.addVertexWithUV(cx + ca0 * ra, ya, cz + sa0 * ra, u0, v0);
                t.addVertexWithUV(cx + ca1 * ra, ya, cz + sa1 * ra, u1, v0);
                t.setColorRGBA_F(cB, cB * 1.04F, cB * 1.16F, ab);
                t.addVertexWithUV(cx + ca1 * rb, yb, cz + sa1 * rb, u1, v1);
                t.addVertexWithUV(cx + ca0 * rb, yb, cz + sa0 * rb, u0, v1);
            }
        }
        t.draw();
    }

    /** Alpha do teto em funcao do raio normalizado (0 no olho, 0 na borda). */
    private static double alphaDeck(double f) {
        double in = Math.min(1.0, f / 0.12);
        double out = Math.min(1.0, (1.0 - f) / 0.35);
        return 0.92 * in * out;
    }

    /** Cilindro vertical: parede do olho ou cortina de chuva mais externa. */
    private static void drawCylinder(Minecraft mc, double cx, double cz, double baseY,
                                     double deckY, double radius, double intensity,
                                     double phase, double fade, double alpha) {
        mc.getTextureManager().bindTexture(WALL_TEX);
        net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.instance;
        t.startDrawingQuads();
        double shade = 0.42 + 0.30 * intensity;
        double height = deckY - baseY;
        if (height <= 1.0 || radius <= 0.5) return;
        for (int seg = 0; seg < WALL_SEGMENTS; seg++) {
            double a0 = (seg / (double) WALL_SEGMENTS) * TWO_PI + phase;
            double a1 = ((seg + 1) / (double) WALL_SEGMENTS) * TWO_PI + phase;
            double x0 = cx + Math.cos(a0) * radius;
            double z0 = cz + Math.sin(a0) * radius;
            double x1 = cx + Math.cos(a1) * radius;
            double z1 = cz + Math.sin(a1) * radius;
            // raio da malha: 1 unidade de UV por ~28 blocos
            double u0 = seg * radius / 28.0;
            double u1 = (seg + 1) * radius / 28.0;
            double vTop = height / 40.0;

            float aBot = (float) (alpha * fade * (0.75 + 0.25 * intensity));
            float aTop = (float) (alpha * fade * 0.5 * (0.6 + 0.4 * intensity));
            t.setColorRGBA_F((float) shade, (float) (shade * 1.02), (float) (shade * 1.12), aBot);
            t.addVertexWithUV(x0, baseY, z0, u0, 0.0);
            t.addVertexWithUV(x1, baseY, z1, u1, 0.0);
            t.setColorRGBA_F((float) (shade * 0.85), (float) (shade * 0.88), (float) (shade * 0.98), aTop);
            t.addVertexWithUV(x1, deckY, z1, u1, vTop);
            t.addVertexWithUV(x0, deckY, z0, u0, vTop);
        }
        t.draw();
    }

    /**
     * Funil do vortice: cone de cima para baixo.
     *
     * <p>Um tornado real estreita da base da nuvem ate o chao; o raio de baixo e
     * proporcional ao raio do vento maximo, e o de cima e a base da nuvem. A
     * inclinacao do funil acompanha a translacao do sistema, o que faz o vortice
     * "deitar" na direcao do movimento.</p>
     */
    private static void drawFunnel(Minecraft mc, double cx, double cz, double groundY,
                                   double deckY, double rmax, double intensity,
                                   double phase, double fade, double leanX, double leanZ) {
        mc.getTextureManager().bindTexture(WALL_TEX);
        net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.instance;
        t.startDrawingQuads();
        double height = deckY - groundY;
        if (height <= 2.0) return;
        double rTop = rmax * 2.6;
        double rBottom = rmax * 0.42;
        double shade = 0.40 + 0.32 * intensity;
        int segs = WALL_SEGMENTS;
        int bands = 10;
        for (int b = 0; b < bands; b++) {
            double f0 = b / (double) bands;
            double f1 = (b + 1) / (double) bands;
            double y0 = groundY + height * (1.0 - f0);
            double y1 = groundY + height * (1.0 - f1);
            // afina para baixo, com leve curva (nao e um cone reto)
            double rr0 = rBottom + (rTop - rBottom) * Math.pow(f0, 0.75);
            double rr1 = rBottom + (rTop - rBottom) * Math.pow(f1, 0.75);
            // inclinacao: a base fica para tras em relacao ao topo
            double lean0 = (1.0 - f0) * rmax * 0.55;
            double lean1 = (1.0 - f1) * rmax * 0.55;
            double lx = leanX * lean0;
            double lz = leanZ * lean0;
            double lx1 = leanX * lean1;
            double lz1 = leanZ * lean1;
            float alpha0 = (float) (fade * intensity * (0.30 + 0.55 * (1.0 - f0)));
            for (int seg = 0; seg < segs; seg++) {
                double a0 = (seg / (double) segs) * TWO_PI + phase;
                double a1 = ((seg + 1) / (double) segs) * TWO_PI + phase;
                double x00 = cx + Math.cos(a0) * rr0 + lx;
                double z00 = cz + Math.sin(a0) * rr0 + lz;
                double x10 = cx + Math.cos(a1) * rr0 + lx;
                double z10 = cz + Math.sin(a1) * rr0 + lz;
                double x01 = cx + Math.cos(a0) * rr1 + lx1;
                double z01 = cz + Math.sin(a0) * rr1 + lz1;
                double x11 = cx + Math.cos(a1) * rr1 + lx1;
                double z11 = cz + Math.sin(a1) * rr1 + lz1;
                double u0 = seg / (double) segs * 2.0;
                double u1 = (seg + 1) / (double) segs * 2.0;
                float v0 = (float) f0;
                float v1 = (float) f1;
                t.setColorRGBA_F((float) shade, (float) (shade * 1.02), (float) (shade * 1.1), alpha0);
                t.addVertexWithUV(x00, y0, z00, u0, v0);
                t.addVertexWithUV(x10, y0, z10, u1, v0);
                t.addVertexWithUV(x11, y1, z11, u1, v1);
                t.addVertexWithUV(x01, y1, z01, u0, v1);
            }
        }
        t.draw();
    }

    /**
     * Bandas espirais de chuva.
     *
     * <p>Sao espirais de verdade (raio e angulo crescem juntos), com o mesmo
     * sentido de rotacao do campo de vento. E o que da a leitura de "furacao" em
     * vez de "mancha cinza": as bandas entram girando na direcao da parede do
     * olho.</p>
     */
    private static void drawBands(Minecraft mc, double cx, double cz, double groundY,
                                  double rmax, double outer, double intensity,
                                  double phase, double fade, double spin) {
        mc.getTextureManager().bindTexture(WALL_TEX);
        net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.instance;
        double rIn = rmax * 1.15;
        double rOut = outer * 1.02;
        if (rOut <= rIn) return;
        double shade = 0.36 + 0.26 * intensity;
        t.startDrawingQuads();
        t.setNormal(0.0F, 1.0F, 0.0F);
        for (int arm = 0; arm < BAND_ARMS; arm++) {
            double base = (arm / (double) BAND_ARMS) * TWO_PI;
            for (int step = 0; step < BAND_STEPS; step++) {
                double f0 = step / (double) BAND_STEPS;
                double f1 = (step + 1) / (double) BAND_STEPS;
                double r0 = rIn + (rOut - rIn) * f0;
                double r1 = rIn + (rOut - rIn) * f1;
                // espiral: o angulo cresce com o log do raio
                double s0 = base + spin * Math.log(r0 / rIn) * 1.6 + phase * 0.6;
                double s1 = base + spin * Math.log(r1 / rIn) * 1.6 + phase * 0.6;
                double wide0 = rmax * (0.30 + 0.55 * f0);
                double wide1 = rmax * (0.30 + 0.55 * f1);
                double y = groundY + 14.0 + f0 * 26.0;
                double y1 = groundY + 14.0 + f1 * 26.0;

                double ax0 = cx + Math.cos(s0) * r0;
                double az0 = cz + Math.sin(s0) * r0;
                double ax1 = cx + Math.cos(s1) * r1;
                double az1 = cz + Math.sin(s1) * r1;
                // largura da banda aplicada perpendicularmente ao raio
                double nx0 = -Math.sin(s0), nz0 = Math.cos(s0);
                double nx1 = -Math.sin(s1), nz1 = Math.cos(s1);

                float a0 = (float) (intensity * fade * 0.30 * (1.0 - f0));
                float a1 = (float) (intensity * fade * 0.30 * (1.0 - f1));
                double u0 = f0 * 2.0, u1 = f1 * 2.0;

                t.setColorRGBA_F((float) shade, (float) (shade * 1.03), (float) (shade * 1.14), a0);
                t.addVertexWithUV(ax0 + nx0 * wide0, y, az0 + nz0 * wide0, u0, 0.0);
                t.addVertexWithUV(ax1 + nx1 * wide1, y1, az1 + nz1 * wide1, u1, 0.0);
                t.setColorRGBA_F((float) (shade * 0.9), (float) (shade * 0.93),
                        (float) (shade * 1.02), a1);
                t.addVertexWithUV(ax1 - nx1 * wide1, y1, az1 - nz1 * wide1, u1, 1.0);
                t.addVertexWithUV(ax0 - nx0 * wide0, y, az0 - nz0 * wide0, u0, 1.0);
            }
        }
        t.draw();
    }

    private static void beginTranslucent() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void endTranslucent() {
        GL11.glDepthMask(true);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopAttrib();
    }

    private static double max0(double v) {
        return v < 0.0 ? 0.0 : v;
    }
}
