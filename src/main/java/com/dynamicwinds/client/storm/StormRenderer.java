package com.dynamicwinds.client.storm;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.StormKind;
import com.dynamicwinds.core.StormSystem;
import com.dynamicwinds.integration.WindConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Desenha as tempestades no mundo: teto de nuvem, rodape da parede do olho,
 * parede (ou funil, no vortice) e bandas espirais de chuva.
 *
 * <p><b>Por que desenhar a mao em vez de mudar as nuvens do Minecraft.</b> As
 * nuvens do vanilla sao um plano unico e global, sem relacao com nada; para
 * deslocar, curvar ou abrir um olho nelas seria preciso interceptar aquele
 * render. Aqui as nuvens do sistema sao geometria propria, o que permite que a
 * forma seja <i>derivada da fisica</i>: o raio do olho e o raio do vento maximo,
 * a altura do teto e o topo da coluna do tipo, e a rotacao e a mesma do campo de
 * vento. Quando o sistema se expande ao enfraquecer, a nuvem se expande junto.</p>
 *
 * <p><b>Duas regras do Tessellator do 1.7.10 que custam caro se ignoradas.</b>
 * A primeira: {@code startDrawing} <i>lanca excecao</i> se ja houver um lote
 * aberto, e o lote fica aberto enquanto nao houver {@code draw()}. Por isso
 * nenhum metodo daqui retorna entre abrir e fechar o lote - toda condicao de
 * degeneracao e checada <b>antes</b> do {@code startDrawingQuads}. A segunda: o
 * formato do vertice e fixado por flags de estado ({@code setNormal} vale do
 * ponto em que e chamado ate o fim do lote), entao normal e cor sao definidos
 * antes do primeiro vertice, nunca no meio.</p>
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
    /** Parede do olho, rodape da nuvem e cortina de chuva. */
    private static final ResourceLocation WALL_TEX =
            new ResourceLocation("dynamicwinds:textures/environment/storm_wall.png");

    private static final int BAND_ARMS = 3;
    private static final int BAND_STEPS = 22;
    private static final double TWO_PI = Math.PI * 2.0;
    private static final float[] LIT_COLOUR = new float[3];
    private static final java.util.Map<String, TerrainCache> TERRAIN_CACHE =
            new java.util.HashMap<String, TerrainCache>();

    private static final class TerrainCache {
        long tick;
        double x, z, radius, height;
    }

    /** Segmentos da malha conforme a distancia da camera (LOD). */
    private static final int SEG_NEAR = 72;
    private static final int SEG_MID = 48;
    private static final int SEG_FAR = 24;
    private static final double LOD_NEAR = 160.0;
    private static final double LOD_MID = 420.0;

    /** Quanto o teto desce da parede do olho ate a borda externa, em blocos. */
    private static final double DECK_DROP = 18.0;
    /** Altura do rodape de nuvem pendurado na parede do olho. */
    private static final double SKIRT_DROP = 24.0;
    /** Teto pratico do mundo: a nuvem nunca sobe acima disso. */
    private static final double SKY_LIMIT = 214.0;
    /** Teto de nuvem como fracao do topo da coluna do tipo. */
    private static final double DECK_FRACTION = 0.55;

    private StormRenderer() {}

    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (!WindConfig.stormRenderSky) return;

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
            drawSystem(mc, visible.get(i), now, time, px, pz);
        }

        endTranslucent();
        GL11.glPopMatrix();
    }

    // ------------------------------------------------------------------

    private static void drawSystem(Minecraft mc, StormSystem s, double now, long time,
                                   double px, double pz) {
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

        double camDist = Math.sqrt((cx - px) * (cx - px) + (cz - pz) * (cz - pz));
        int segs = camDist < LOD_NEAR ? SEG_NEAR : (camDist < LOD_MID ? SEG_MID : SEG_FAR);

        // o relevo sob o anel, e nao so sob o centro: uma tempestade sobre um
        // morro enterraria parte da parede
        double terrainRadius = narrow ? rmax * 2.0 : rmax * 1.45;
        double groundY = terrainTop(mc, storm, cx, cz, terrainRadius, time);
        double top = StormKind.columnTop(s.kind);
        double deckY = Math.min(groundY + top * DECK_FRACTION, SKY_LIMIT);
        double baseY = narrow ? groundY - 1.0 : groundY + 2.0;

        // perto, desenha inteiro; longe, desaparece sem corte seco
        double viewFade = 1.0 - max0(camDist - outer * 0.6) / Math.max(1.0, outer * 1.6);
        double fade = Math.max(0.0, Math.min(1.0, viewFade));

        // o clarao do relampago acende a nuvem por dentro. Vem do mesmo disparo
        // que agenda o trovao: a luz chega na hora, o som viaja a 343 m/s
        float flash = StormClientState.flashStrength();

        if (deckY <= baseY + 2.0) {
            // terreno alto demais para caber nuvem: ainda desenha o que da
            deckY = baseY + 2.0;
        }

        // 1) cortina de chuva / parede do olho
        if (narrow) {
            // o funil se inclina para tras em relacao ao deslocamento, como um
            // vortice que arrasta a base atras do topo
            double tx = s.translationX(time);
            double tz = s.translationZ(time);
            double tl = Math.sqrt(tx * tx + tz * tz);
            double leanX = tl > 1.0e-9 ? -tx / tl : 0.0;
            double leanZ = tl > 1.0e-9 ? -tz / tl : 0.0;
            drawFunnel(mc, cx, cz, groundY, deckY, rmax, intensity, phase, fade, flash,
                    leanX, leanZ, segs);
        } else {
            drawCylinder(mc, cx, cz, baseY, deckY, rmax * 1.02, intensity, phase, fade, flash,
                    0.60, segs);
            drawCylinder(mc, cx, cz, baseY, deckY * 0.99, rmax * 1.45, intensity, phase, fade, flash,
                    0.26, segs);
        }

        // 2) bandas espirais de chuva, girando com o sistema
        drawBands(mc, cx, cz, groundY, deckY, rmax, outer, intensity, phase, fade, flash,
                spin, narrow, segs);

        // 3) rodape de nuvem pendurado na parede do olho: e ele que da volume ao
        //    teto visto de baixo, em vez de um disco plano
        drawSkirt(mc, cx, cz, deckY, rmax * (narrow ? 1.35 : 0.9), intensity, phase, fade,
                flash, segs);

        // 4) teto de nuvem, deixando o olho aberto
        drawDeck(mc, cx, cz, deckY, rmax * (narrow ? 1.35 : 0.9), outer * 1.04,
                intensity, phase, fade, flash, segs);

        // 5) camada alta e mais clara, so quando o sistema e forte: da espessura
        //    ao conjunto sem custo quando esta longe
        if (intensity > 0.35 && camDist < LOD_MID) {
            drawShell(mc, cx, cz, deckY, rmax, outer, top, intensity, phase, fade, flash, segs);
        }
    }

    /**
     * Altura do relevo sob o sistema, olhando oito pontos do anel.
     */
    private static double terrainTop(Minecraft mc, StormSystem storm, double cx, double cz,
                                     double radius, long tick) {
        String key = System.identityHashCode(mc.theWorld) + ":"
                + mc.theWorld.provider.dimensionId + ":" + storm.id;
        TerrainCache cached = TERRAIN_CACHE.get(key);
        double dx = cached == null ? Double.MAX_VALUE : cx - cached.x;
        double dz = cached == null ? Double.MAX_VALUE : cz - cached.z;
        if (cached != null && tick >= cached.tick && tick - cached.tick < 20L
                && dx * dx + dz * dz < 256.0
                && Math.abs(radius - cached.radius) < 16.0) return cached.height;

        double top = mc.theWorld.getHeightValue((int) Math.floor(cx), (int) Math.floor(cz));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int x = (int) Math.floor(cx + Math.cos(a) * radius);
            int z = (int) Math.floor(cz + Math.sin(a) * radius);
            double h = mc.theWorld.getHeightValue(x, z);
            if (h > top) top = h;
        }
        if (TERRAIN_CACHE.size() > 128) {
            java.util.Iterator<java.util.Map.Entry<String, TerrainCache>> it = TERRAIN_CACHE.entrySet().iterator();
            while (it.hasNext()) if (tick - it.next().getValue().tick > 1200L) it.remove();
        }
        TerrainCache value = cached == null ? new TerrainCache() : cached;
        value.tick = tick;
        value.x = cx;
        value.z = cz;
        value.radius = radius;
        value.height = top;
        TERRAIN_CACHE.put(key, value);
        return top;
    }

    /** Cor da nuvem ja com o clarao do relampago aplicado. */
    private static float[] litColour(double shade, double flash) {
        double s = shade + (0.92 - shade) * flash;
        if (s > 1.0) s = 1.0;
        // o clarao tambem lava o tom azulado: a nuvem acesa e quase branca
        LIT_COLOUR[0] = (float) s;
        LIT_COLOUR[1] = (float) Math.min(1.0, s * (1.02 + 0.05 * flash));
        LIT_COLOUR[2] = (float) Math.min(1.0, s * (1.12 + 0.06 * flash));
        return LIT_COLOUR;
    }

    /**
     * Teto de nuvem: anel entre a parede do olho e a borda externa.
     *
     * <p>O olho fica aberto de proposito - e a assinatura visual de um ciclone, e
     * e exatamente onde o modelo de vento diz que nao ha chuva.</p>
     */
    private static void drawDeck(Minecraft mc, double cx, double cz, double deckY,
                                 double rIn, double rOut, double intensity,
                                 double phase, double fade, float flash, int segs) {
        if (rOut <= rIn || segs < 6) return;
        mc.getTextureManager().bindTexture(CLOUD_TEX);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setNormal(0.0F, 1.0F, 0.0F);

        int rings = 5;
        double shade = 0.34 + 0.30 * intensity;
        float[] col = litColour(shade, flash);
        for (int ring = 0; ring < rings; ring++) {
            double f0 = ring / (double) rings;
            double f1 = (ring + 1) / (double) rings;
            double ra = rIn + (rOut - rIn) * f0;
            double rb = rIn + (rOut - rIn) * f1;
            // a nuvem desce um pouco para fora (o teto nao e um disco plano)
            double ya = deckY - f0 * f0 * DECK_DROP;
            double yb = deckY - f1 * f1 * DECK_DROP;
            float aa = (float) (alphaDeck(f0) * fade * (0.55 + 0.45 * intensity));
            float ab = (float) (alphaDeck(f1) * fade * (0.55 + 0.45 * intensity));
            if (aa <= 0.004F && ab <= 0.004F) continue;
            float ca = (float) (1.0 - f0 * 0.35);
            float cb = (float) (1.0 - f1 * 0.35);
            float va = (float) (f0 * 2.4);
            float vb = (float) (f1 * 2.4);
            for (int seg = 0; seg < segs; seg++) {
                double a0 = (seg / (double) segs) * TWO_PI + phase;
                double a1 = ((seg + 1) / (double) segs) * TWO_PI + phase;
                double ca0 = Math.cos(a0), sa0 = Math.sin(a0);
                double ca1 = Math.cos(a1), sa1 = Math.sin(a1);
                // U vem do indice do segmento (3 voltas na circunferencia), e nao
                // do comprimento de arco: assim os 5 aneis concentricos ficam
                // alinhados em u e a textura nao estica no raio. V repete 2,4x
                // no raio; e por isso que a imagem nao pode ter gradiente
                // assado - ele se repetiria 3x no anel e 2,4x no raio.
                double u0 = seg / (double) segs * 3.0;
                double u1 = (seg + 1) / (double) segs * 3.0;

                t.setColorRGBA_F(col[0] * ca, col[1] * ca, col[2] * ca, aa);
                t.addVertexWithUV(cx + ca0 * ra, ya, cz + sa0 * ra, u0, va);
                t.addVertexWithUV(cx + ca1 * ra, ya, cz + sa1 * ra, u1, va);
                t.setColorRGBA_F(col[0] * cb, col[1] * cb, col[2] * cb, ab);
                t.addVertexWithUV(cx + ca1 * rb, yb, cz + sa1 * rb, u1, vb);
                t.addVertexWithUV(cx + ca0 * rb, yb, cz + sa0 * rb, u0, vb);
            }
        }
        t.draw();
    }

    /**
     * Camada alta e mais clara sobre o teto.
     *
     * <p>Nuvem de tempestade tem espessura: em cima dela ha uma camada de cirrus
     * que sai da corrente de saida (o ar que subiu na parede do olho e se
     * espalha no topo). Ela e mais clara porque e gelo fino, e fica de fora do
     * olho - o buraco do olho atravessa a espessura toda.</p>
     */
    private static void drawShell(Minecraft mc, double cx, double cz, double deckY,
                                  double rmax, double outer, double top, double intensity,
                                  double phase, double fade, float flash, int segs) {
        double y = Math.min(deckY + top * 0.18, SKY_LIMIT + 26.0);
        double rIn = rmax * 1.05;
        double rOut = outer * 1.16;
        if (rOut <= rIn || segs < 6 || y <= deckY) return;
        mc.getTextureManager().bindTexture(CLOUD_TEX);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setNormal(0.0F, 1.0F, 0.0F);
        float[] col = litColour(0.62 + 0.20 * intensity, flash);
        for (int seg = 0; seg < segs; seg++) {
            double a0 = (seg / (double) segs) * TWO_PI - phase * 0.5;
            double a1 = ((seg + 1) / (double) segs) * TWO_PI - phase * 0.5;
            double ca0 = Math.cos(a0), sa0 = Math.sin(a0);
            double ca1 = Math.cos(a1), sa1 = Math.sin(a1);
            double u0 = seg / (double) segs * 2.0;
            double u1 = (seg + 1) / (double) segs * 2.0;
            float a = (float) (fade * intensity * 0.34);
            t.setColorRGBA_F(col[0], col[1], col[2], a);
            t.addVertexWithUV(cx + ca0 * rIn, y, cz + sa0 * rIn, u0, 0.0);
            t.addVertexWithUV(cx + ca1 * rIn, y, cz + sa1 * rIn, u1, 0.0);
            t.addVertexWithUV(cx + ca1 * rOut, y, cz + sa1 * rOut, u1, 1.4);
            t.addVertexWithUV(cx + ca0 * rOut, y, cz + sa0 * rOut, u0, 1.4);
        }
        t.draw();
    }

    /** Alpha do teto em funcao do raio normalizado (0 no olho, 0 na borda). */
    private static double alphaDeck(double f) {
        double in = Math.min(1.0, f / 0.12);
        double out = Math.min(1.0, (1.0 - f) / 0.35);
        return 0.92 * in * out;
    }

    /**
     * Rodape de nuvem: cortina curta que desce da borda interna do teto.
     *
     * <p>Sem ela a nuvem e um disco visto de baixo, e disco nao tem volume. Com
     * ela aparece a base da parede do olho, que e justamente o que se ve de
     * dentro de uma tempestade.</p>
     */
    private static void drawSkirt(Minecraft mc, double cx, double cz, double deckY,
                                  double radius, double intensity, double phase, double fade,
                                  float flash, int segs) {
        double bottom = deckY - SKIRT_DROP * (0.55 + 0.45 * intensity);
        if (radius <= 0.5 || deckY - bottom <= 1.0 || segs < 6) return;
        mc.getTextureManager().bindTexture(WALL_TEX);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        float[] col = litColour(0.30 + 0.26 * intensity, flash);
        double vTop = (deckY - bottom) / 40.0;
        // a cortina afina para baixo: em cima ela nasce da nuvem, embaixo ela
        // se desfaz na chuva
        double rBottom = radius * 1.04;
        for (int seg = 0; seg < segs; seg++) {
            double a0 = (seg / (double) segs) * TWO_PI + phase * 1.3;
            double a1 = ((seg + 1) / (double) segs) * TWO_PI + phase * 1.3;
            double x0t = cx + Math.cos(a0) * radius, z0t = cz + Math.sin(a0) * radius;
            double x1t = cx + Math.cos(a1) * radius, z1t = cz + Math.sin(a1) * radius;
            double x0b = cx + Math.cos(a0) * rBottom, z0b = cz + Math.sin(a0) * rBottom;
            double x1b = cx + Math.cos(a1) * rBottom, z1b = cz + Math.sin(a1) * rBottom;
            double u0 = seg / (double) segs * 4.0;
            double u1 = (seg + 1) / (double) segs * 4.0;
            float aB = (float) (fade * (0.10 + 0.30 * intensity));
            float aT = (float) (fade * (0.35 + 0.30 * intensity));
            t.setColorRGBA_F(col[0] * 0.9F, col[1] * 0.9F, col[2] * 0.9F, aB);
            t.addVertexWithUV(x0b, bottom, z0b, u0, vTop);
            t.addVertexWithUV(x1b, bottom, z1b, u1, vTop);
            t.setColorRGBA_F(col[0], col[1], col[2], aT);
            t.addVertexWithUV(x1t, deckY, z1t, u1, 0.0);
            t.addVertexWithUV(x0t, deckY, z0t, u0, 0.0);
        }
        t.draw();
    }

    /** Cilindro vertical: parede do olho ou cortina de chuva mais externa. */
    private static void drawCylinder(Minecraft mc, double cx, double cz, double baseY,
                                     double deckY, double radius, double intensity,
                                     double phase, double fade, float flash,
                                     double alpha, int segs) {
        double height = deckY - baseY;
        if (height <= 1.0 || radius <= 0.5 || segs < 6) return;
        mc.getTextureManager().bindTexture(WALL_TEX);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        float[] col = litColour(0.42 + 0.30 * intensity, flash);
        double vTop = height / 40.0;
        for (int seg = 0; seg < segs; seg++) {
            double a0 = (seg / (double) segs) * TWO_PI + phase;
            double a1 = ((seg + 1) / (double) segs) * TWO_PI + phase;
            double x0 = cx + Math.cos(a0) * radius;
            double z0 = cz + Math.sin(a0) * radius;
            double x1 = cx + Math.cos(a1) * radius;
            double z1 = cz + Math.sin(a1) * radius;
            // raio da malha: 1 unidade de UV por ~28 blocos
            double u0 = seg * radius / 28.0;
            double u1 = (seg + 1) * radius / 28.0;

            float aBot = (float) (alpha * fade * (0.75 + 0.25 * intensity));
            float aTop = (float) (alpha * fade * 0.5 * (0.6 + 0.4 * intensity));
            t.setColorRGBA_F(col[0], col[1], col[2], aBot);
            t.addVertexWithUV(x0, baseY, z0, u0, vTop);
            t.addVertexWithUV(x1, baseY, z1, u1, vTop);
            t.setColorRGBA_F(col[0] * 0.85F, col[1] * 0.88F, col[2] * 0.98F, aTop);
            t.addVertexWithUV(x1, deckY, z1, u1, 0.0);
            t.addVertexWithUV(x0, deckY, z0, u0, 0.0);
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
                                   double phase, double fade, float flash,
                                   double leanX, double leanZ, int segs) {
        double height = deckY - groundY;
        double rTop = rmax * 2.6;
        double rBottom = rmax * 0.42;
        if (height <= 2.0 || rBottom <= 0.2 || segs < 6) return;
        mc.getTextureManager().bindTexture(WALL_TEX);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        float[] col = litColour(0.40 + 0.32 * intensity, flash);
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
            float alpha1 = (float) (fade * intensity * (0.30 + 0.55 * (1.0 - f1)));
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
                t.setColorRGBA_F(col[0], col[1], col[2], alpha0);
                t.addVertexWithUV(x00, y0, z00, u0, v0);
                t.addVertexWithUV(x10, y0, z10, u1, v0);
                t.setColorRGBA_F(col[0] * 0.92F, col[1] * 0.94F, col[2] * 1.02F, alpha1);
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
     * sentido de rotacao do campo de vento, e descem da base da nuvem ate perto
     * do chao: cada banda e uma cortina vertical, que e o que um setor de chuva
     * de um ciclone e. E o que da a leitura de "furacao" em vez de "mancha
     * cinza": as bandas entram girando na direcao da parede do olho.</p>
     */
    private static void drawBands(Minecraft mc, double cx, double cz, double groundY,
                                  double deckY, double rmax, double outer, double intensity,
                                  double phase, double fade, float flash, double spin,
                                  boolean narrow, int segs) {
        double rIn = narrow ? rmax * 6.0 : rmax * 1.15;
        double rOut = outer * 1.02;
        if (rOut <= rIn || segs < 6) return;
        mc.getTextureManager().bindTexture(WALL_TEX);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        float[] col = litColour(0.36 + 0.26 * intensity, flash);
        double yBottom = groundY + 2.0;
        double yTop = Math.max(yBottom + 4.0, deckY - 30.0);
        double weight = narrow ? 0.45 : 1.0;
        int steps = narrow ? BAND_STEPS / 2 : BAND_STEPS;
        for (int arm = 0; arm < BAND_ARMS; arm++) {
            double base = (arm / (double) BAND_ARMS) * TWO_PI;
            for (int step = 0; step < steps; step++) {
                double f0 = step / (double) steps;
                double f1 = (step + 1) / (double) steps;
                double r0 = rIn + (rOut - rIn) * f0;
                double r1 = rIn + (rOut - rIn) * f1;
                // espiral: o angulo cresce com o log do raio
                double s0 = base + spin * Math.log(r0 / rIn) * 1.6 + phase * 0.6;
                double s1 = base + spin * Math.log(r1 / rIn) * 1.6 + phase * 0.6;
                double x0 = cx + Math.cos(s0) * r0;
                double z0 = cz + Math.sin(s0) * r0;
                double x1 = cx + Math.cos(s1) * r1;
                double z1 = cz + Math.sin(s1) * r1;
                // a banda desce da base da nuvem ate perto do chao; mais para
                // fora ela nasce um pouco mais alta (a nuvem tambem sobe)
                double t0 = yTop - f0 * 8.0;
                double t1 = yTop - f1 * 8.0;
                float a0 = (float) (intensity * fade * 0.26 * (1.0 - f0) * weight);
                float a1 = (float) (intensity * fade * 0.26 * (1.0 - f1) * weight);
                if (a0 <= 0.004F && a1 <= 0.004F) continue;
                double u0 = f0 * 3.0;
                double u1 = f1 * 3.0;
                float vTop0 = (float) ((t0 - yBottom) / 40.0);
                float vTop1 = (float) ((t1 - yBottom) / 40.0);

                t.setColorRGBA_F(col[0] * 0.88F, col[1] * 0.9F, col[2] * 0.98F, a0 * 0.35F);
                t.addVertexWithUV(x0, yBottom, z0, u0, 0.0);
                t.addVertexWithUV(x1, yBottom, z1, u1, 0.0);
                t.setColorRGBA_F(col[0], col[1], col[2], a0);
                t.addVertexWithUV(x1, t1, z1, u1, vTop1);
                t.addVertexWithUV(x0, t0, z0, u0, vTop0);
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
        // O bindTexture liga na unidade ATIVA, e durante o mundo a unidade ativa
        // costuma ser a do lightmap (GL_TEXTURE1): sem trocar, as nuvens sairiam
        // texturizadas com o mapa de luz. Guardamos a unidade atual, desenhamos
        // na padrao e devolvemos no fim.
        previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private static void endTranslucent() {
        if (previousUnit >= 0) {
            GL13.glActiveTexture(previousUnit);
            previousUnit = -1;
        }
        GL11.glDepthMask(true);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopAttrib();
    }

    /** Unidade de textura ativa antes do desenho, para devolver no fim. */
    private static int previousUnit = -1;

    private static double max0(double v) {
        return v < 0.0 ? 0.0 : v;
    }
}
