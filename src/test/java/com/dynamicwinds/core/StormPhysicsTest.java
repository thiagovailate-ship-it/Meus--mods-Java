package com.dynamicwinds.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Propriedades fisicas do sistema de tempo severo.
 *
 * <p>Os numeros de referencia vem da meteorologia: um furacao maior tem deficit
 * central de 60 a 100 hPa, um furacao de 20 a 50, uma tempestade tropical de 5 a
 * 15, e o nucleo de um tornado pode passar de 10 hPa. Os testes travam essas
 * faixas para que uma mudanca de parametro que quebre a fisica apareca aqui, e
 * nao como um numero estranho no jogo.</p>
 *
 * <p>As consultas de vento sao sempre relativas ao centro do sistema naquele
 * instante: o sistema se desloca, entao consultar na origem do mundo mediria um
 * raio completamente diferente.</p>
 */
class StormPhysicsTest {

    private static final boolean NORTH = true;

    // ------------------------------------------------------------------
    // perfil de vento
    // ------------------------------------------------------------------

    @Test
    void perfilTangencialTemPicoEmRmax() {
        for (int kind = 0; kind < StormKind.COUNT; kind++) {
            double vmax = StormKind.vmax(kind);
            double rmax = StormKind.rmax(kind);
            double alpha = StormKind.alpha(kind);

            assertEquals(0.0, VortexBalance.tangential(0.0, vmax, rmax, alpha), 1.0e-12,
                    "vento deveria ser nulo no centro exato");
            assertEquals(vmax, VortexBalance.tangential(rmax, vmax, rmax, alpha), 1.0e-9,
                    "pico deveria ser exatamente Vmax");

            double previous = -1.0;
            for (int i = 1; i <= 40; i++) {
                double v = VortexBalance.tangential(rmax * i / 40.0, vmax, rmax, alpha);
                assertTrue(v > previous, "perfil nao cresceu dentro do nucleo");
                previous = v;
            }

            previous = Double.MAX_VALUE;
            for (int i = 1; i <= 60; i++) {
                double v = VortexBalance.tangential(rmax * (1.0 + i / 10.0), vmax, rmax, alpha);
                assertTrue(v < previous, "perfil nao decaiu fora do nucleo");
                previous = v;
            }
        }
    }

    @Test
    void perfilTangencialEContinuoEmRmax() {
        for (int kind = 0; kind < StormKind.COUNT; kind++) {
            double vmax = StormKind.vmax(kind);
            double rmax = StormKind.rmax(kind);
            double alpha = StormKind.alpha(kind);
            double before = VortexBalance.tangential(rmax - 1.0e-6, vmax, rmax, alpha);
            double after = VortexBalance.tangential(rmax + 1.0e-6, vmax, rmax, alpha);
            assertEquals(before, after, 1.0e-4, "salto de vento na transicao do nucleo");
        }
    }

    // ------------------------------------------------------------------
    // campo de pressao
    // ------------------------------------------------------------------

    @Test
    void deficitCentralFicaNaFaixaObservada() {
        double f = VortexBalance.coriolis(NORTH);

        double vortex = centralHpa(StormKind.VORTEX, f);
        assertTrue(vortex > 5.0 && vortex < 30.0,
                "deficit do vortice fora da faixa de tornado: " + vortex + " hPa");

        double ts = centralHpa(StormKind.TROPICAL_STORM, f);
        assertTrue(ts > 3.0 && ts < 15.0,
                "deficit de tempestade tropical fora da faixa: " + ts + " hPa");

        double hurricane = centralHpa(StormKind.HURRICANE, f);
        assertTrue(hurricane > 20.0 && hurricane < 50.0,
                "deficit de furacao fora da faixa: " + hurricane + " hPa");

        double major = centralHpa(StormKind.MAJOR_HURRICANE, f);
        assertTrue(major > 50.0 && major < 110.0,
                "deficit de furacao maior fora da faixa: " + major + " hPa");
    }

    @Test
    void deficitCresceComAIntensidadeDoTipo() {
        double f = VortexBalance.coriolis(NORTH);
        double ts = centralHpa(StormKind.TROPICAL_STORM, f);
        double hurricane = centralHpa(StormKind.HURRICANE, f);
        double major = centralHpa(StormKind.MAJOR_HURRICANE, f);
        assertTrue(hurricane > ts, "furacao deveria ter deficit maior que tempestade tropical");
        assertTrue(major > hurricane, "furacao maior deveria ter deficit maior que furacao");
    }

    @Test
    void deficitDiminuiComADistanciaEZeraNaBorda() {
        double f = VortexBalance.coriolis(NORTH);
        double vmax = StormKind.vmax(StormKind.MAJOR_HURRICANE);
        double rmax = StormKind.rmax(StormKind.MAJOR_HURRICANE);
        double alpha = StormKind.alpha(StormKind.MAJOR_HURRICANE);
        double rOut = StormKind.outerRadius(StormKind.MAJOR_HURRICANE);

        double previous = Double.MAX_VALUE;
        for (int i = 0; i <= 100; i++) {
            double r = rOut * i / 100.0;
            double d = VortexBalance.deficitPa(r, vmax, rmax, alpha, f, rOut);
            assertTrue(d >= 0.0, "deficit negativo em r=" + r);
            assertTrue(d < previous + 1.0e-6, "deficit nao caiu ao se afastar do centro em r=" + r);
            previous = d;
        }
        assertEquals(0.0, VortexBalance.deficitPa(rOut, vmax, rmax, alpha, f, rOut), 1.0e-9,
                "deficit deveria ser nulo na borda externa");
        assertEquals(0.0, VortexBalance.deficitPa(rOut * 2.0, vmax, rmax, alpha, f, rOut), 1.0e-9,
                "deficit deveria ser nulo fora do sistema");
    }

    @Test
    void deficitDoPerfilBateComOIntegralNumerico() {
        // o deficit e integrado analiticamente; aqui conferimos contra uma soma
        // numerica de dp/dr = rho*(v^2/r + f*v), que e a definicao fisica
        double f = VortexBalance.coriolis(NORTH);
        double vmax = StormKind.vmax(StormKind.HURRICANE);
        double rmax = StormKind.rmax(StormKind.HURRICANE);
        double alpha = StormKind.alpha(StormKind.HURRICANE);
        double rOut = StormKind.outerRadius(StormKind.HURRICANE);

        double r0 = rmax * 0.25;
        int steps = 200000;
        double dr = (rOut - r0) / steps;
        double sum = 0.0;
        for (int i = 0; i < steps; i++) {
            double r = r0 + (i + 0.5) * dr;
            double v = VortexBalance.tangential(r, vmax, rmax, alpha);
            sum += VortexBalance.RHO * (v * v / r + f * v) * dr;
        }
        double analytic = VortexBalance.deficitPa(r0, vmax, rmax, alpha, f, rOut);
        assertEquals(sum, analytic, Math.max(2.0, analytic * 2.0e-4),
                "integral analitico nao bate com a integracao numerica");
    }

    // ------------------------------------------------------------------
    // ciclo de vida e trajetoria
    // ------------------------------------------------------------------

    @Test
    void cicloDeVidaEContinuoEZeraForaDaJanela() {
        StormSystem s = new StormSystem(StormKind.HURRICANE, 1L, 1000L, 0.0, 0.0,
                0.0, 0.0, NORTH, 1.0, false);
        long duration = s.durationTicks();

        assertEquals(0.0, s.intensityAt(999L), 1.0e-12, "nao deveria existir antes de nascer");
        assertEquals(0.0, s.intensityAt(1000L + duration), 1.0e-12, "nao deveria existir depois de morrer");
        assertTrue(!s.isAlive(1000L + duration), "sistema deveria estar morto");

        double previous = s.intensityAt(1000L);
        double maxStep = 0.0;
        double peak = 0.0;
        for (int i = 1; i <= 20000; i++) {
            long t = 1000L + (long) i * duration / 20000L;
            double v = s.intensityAt(t);
            maxStep = Math.max(maxStep, Math.abs(v - previous));
            peak = Math.max(peak, v);
            previous = v;
        }
        assertTrue(maxStep < 0.01, "ciclo de vida com degrau grande: " + maxStep);
        assertTrue(peak > 0.99, "intensidade de pico baixa demais: " + peak);
    }

    @Test
    void intensidadeNaoDiminuiDuranteAIntensificacao() {
        StormSystem s = new StormSystem(StormKind.MAJOR_HURRICANE, 1L, 0L, 0.0, 0.0,
                0.0, 0.0, NORTH, 1.0, false);
        long duration = s.durationTicks();
        double previous = -1.0;
        for (int i = 0; i <= 90; i++) {
            long t = (long) (i / 100.0 * 0.45 * duration);
            double v = s.intensityAt(t);
            assertTrue(v >= previous - 1.0e-9,
                    "intensidade oscilou durante a intensificacao em t=" + t);
            previous = v;
        }
    }

    @Test
    void campoDeVentoSeExpandeAoEnfraquecer() {
        StormSystem s = new StormSystem(StormKind.HURRICANE, 1L, 0L, 0.0, 0.0,
                0.0, 0.0, NORTH, 1.0, false);
        long duration = s.durationTicks();
        double matureRmax = s.rmaxAt((long) (0.60 * duration));
        double decayRmax = s.rmaxAt((long) (0.95 * duration));
        assertTrue(decayRmax > matureRmax,
                "raio do vento maximo deveria crescer na dissipacao: "
                        + matureRmax + " -> " + decayRmax);
    }

    @Test
    void trajetoriaEContinuaEComVelocidadeConstante() {
        StormSystem s = new StormSystem(StormKind.HURRICANE, 1L, 0L, 0.0, 0.0,
                0.7, 4.0e-6, NORTH, 1.0, false);
        double expected = StormKind.translationSpeed(StormKind.HURRICANE);

        double px = s.centerX(0L);
        double pz = s.centerZ(0L);
        for (long t = 1; t <= 20000; t++) {
            double x = s.centerX(t);
            double z = s.centerZ(t);
            assertEquals(expected, Math.hypot(x - px, z - pz), 1.0e-6,
                    "passo de trajetoria fora do esperado em t=" + t);
            px = x;
            pz = z;
        }
    }

    @Test
    void ventoDoOlhoEMaisCalmoQueAParedeDoOlho() {
        StormSystem s = new StormSystem(StormKind.MAJOR_HURRICANE, 1L, 0L, 0.0, 0.0,
                0.0, 0.0, NORTH, 1.0, false);
        long time = (long) (0.60 * s.durationTicks());
        double cx = s.centerX(time);
        double cz = s.centerZ(time);
        double rmax = s.rmaxAt(time);
        int surfaceY = 64;

        Vec3 eye = s.wind(time, cx + 0.05, surfaceY + 2, cz + 0.05, surfaceY, 1.0);
        Vec3 wall = s.wind(time, cx + rmax, surfaceY + 2, cz, surfaceY, 1.0);
        double eyeHorizontal = Math.hypot(eye.x, eye.z);
        double wallHorizontal = Math.hypot(wall.x, wall.z);

        assertTrue(eyeHorizontal < wallHorizontal * 0.25,
                "olho deveria ser muito mais calmo que a parede: "
                        + eyeHorizontal + " vs " + wallHorizontal);
        assertTrue(wallHorizontal > s.vmaxAt(time) * 0.5,
                "parede do olho fraca demais: " + wallHorizontal);
    }

    @Test
    void olhoTemSubsidenciaEParedeTemAscendencia() {
        StormSystem s = new StormSystem(StormKind.MAJOR_HURRICANE, 1L, 0L, 0.0, 0.0,
                0.0, 0.0, NORTH, 1.0, false);
        long time = (long) (0.60 * s.durationTicks());
        double cx = s.centerX(time);
        double cz = s.centerZ(time);
        double rmax = s.rmaxAt(time);
        int surfaceY = 64;

        Vec3 wall = s.wind(time, cx + rmax, surfaceY + 5, cz, surfaceY, 1.0);
        Vec3 eye = s.wind(time, cx + 0.1, surfaceY + 5, cz + 0.1, surfaceY, 1.0);

        assertTrue(wall.y > 0.0, "parede do olho deveria ter corrente ascendente: " + wall.y);
        assertTrue(eye.y < 0.0, "olho deveria ter subsidencia (ar descendo): " + eye.y);
    }

    @Test
    void sentidoDeRotacaoInverteComOHemisferio() {
        long time = 5000L;
        double offset = 50.0;
        StormSystem north = new StormSystem(StormKind.HURRICANE, 1L, 0L, 0.0, 0.0,
                0.0, 0.0, true, 1.0, false);
        StormSystem south = new StormSystem(StormKind.HURRICANE, 2L, 0L, 0.0, 0.0,
                0.0, 0.0, false, 1.0, false);

        Vec3 a = north.wind(time, north.centerX(time) + offset, 66.0, north.centerZ(time), 64, 1.0);
        Vec3 b = south.wind(time, south.centerX(time) + offset, 66.0, south.centerZ(time), 64, 1.0);

        assertTrue(a.z * b.z < 0.0,
                "componente tangencial deveria inverter com o hemisferio: " + a.z + " vs " + b.z);
        assertTrue(Math.abs(a.z) > 0.1, "vento tangencial forte demais fraco: " + a.z);
    }

    @Test
    void sobreTerraOVentoDiminui() {
        StormSystem s = new StormSystem(StormKind.HURRICANE, 1L, 0L, 0.0, 0.0,
                0.0, 0.0, NORTH, 1.0, false);
        long time = (long) (0.60 * s.durationTicks());
        double cx = s.centerX(time);
        double cz = s.centerZ(time);
        double rmax = s.rmaxAt(time);

        Vec3 water = s.wind(time, cx + rmax, 66.0, cz, 64, 1.0);
        Vec3 land = s.wind(time, cx + rmax, 66.0, cz, 64, 0.45);

        assertTrue(Math.hypot(land.x, land.z) < Math.hypot(water.x, water.z),
                "landfall deveria reduzir o vento");
        assertTrue(Math.hypot(land.x, land.z) > 0.0, "landfall nao deveria zerar o vento");
    }

    @Test
    void ventoDoCampoEFinitoEmTodosOsPontos() {
        StormField field = new StormField(4242L);
        double[][] points = {{0, 65, 0}, {50, 70, -50}, {-300, 80, 250}, {1200, 64, -900}};
        for (long t = 0L; t < 200000L; t += 997L) {
            for (int i = 0; i < points.length; i++) {
                double[] p = points[i];
                Vec3 v = field.sampleWind(t, p[0], p[1], p[2], 64, SurfaceRoughness.OPEN_WATER);
                assertTrue(Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z),
                        "vento nao finito em t=" + t + " ponto=" + p[0] + "," + p[2]);
            }
        }
    }

    // ------------------------------------------------------------------
    // conjunto de sistemas
    // ------------------------------------------------------------------

    @Test
    void geracaoNaturalEDeterministica() {
        StormField a = new StormField(777L);
        StormField b = new StormField(777L);
        for (long t = 0L; t < 400000L; t += 313L) {
            List<StormSystem> la = a.activeAt(t);
            List<StormSystem> lb = b.activeAt(t);
            assertEquals(la.size(), lb.size(), "quantidade de sistemas diferente em t=" + t);
            for (int i = 0; i < la.size(); i++) {
                StormSystem sa = la.get(i);
                StormSystem sb = lb.get(i);
                assertEquals(sa.kind, sb.kind, "tipo diferente em t=" + t);
                assertEquals(sa.centerX(t), sb.centerX(t), 1.0e-9, "centro X diferente");
                assertEquals(sa.centerZ(t), sb.centerZ(t), 1.0e-9, "centro Z diferente");
                assertEquals(sa.vmaxAt(t), sb.vmaxAt(t), 1.0e-9, "intensidade diferente");
            }
        }
    }

    @Test
    void geracaoNaturalProduzSistemasAoLongoDoTempo() {
        StormField field = new StormField(12345L);
        int slotsWithStorm = 0;
        for (long slot = 0L; slot < 200L; slot++) {
            long base = slot * StormField.SLOT_TICKS;
            for (int k = 0; k < 12; k++) {
                long t = base + k * (StormField.SLOT_TICKS / 12L);
                if (!field.activeAt(t).isEmpty()) {
                    slotsWithStorm++;
                    break;
                }
            }
        }
        assertTrue(slotsWithStorm > 0,
                "nenhum sistema natural encontrado em 200 janelas");
    }

    @Test
    void sistemasNascemDentroDaRegiaoConfigurada() {
        StormField field = new StormField(999L);
        int checked = 0;
        for (long slot = 0L; slot < 200L; slot++) {
            long base = slot * StormField.SLOT_TICKS;
            for (int k = 0; k < 12; k++) {
                for (StormSystem s : field.activeAt(base + k * (StormField.SLOT_TICKS / 12L))) {
                    if (s.playerSpawned) continue;
                    assertTrue(Math.hypot(s.spawnX, s.spawnZ) <= StormField.REGION_RADIUS + 1.0,
                            "sistema natural nasceu fora da regiao");
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "nenhum sistema natural para verificar");
    }

    @Test
    void sistemaInvocadoRespeitaSeuCicloDeVida() {
        StormField field = isolatedField(1L);
        StormSystem s = field.spawn(StormKind.MAJOR_HURRICANE, 1000L, 40.0, -80.0, 0.0, 1.0);
        assertTrue(s.playerSpawned, "deveria estar marcado como invocado");

        assertTrue(!contains(field.activeAt(999L), s.id), "nao deveria existir antes do nascimento");
        assertTrue(contains(field.activeAt(1000L), s.id), "deveria existir no nascimento");
        assertTrue(contains(field.activeAt(1000L + s.durationTicks() / 2), s.id),
                "deveria existir no meio da vida");
        assertTrue(!contains(field.activeAt(1000L + s.durationTicks()), s.id),
                "nao deveria existir depois da morte");

        field.removeDynamic(s.id);
        assertEquals(0, field.dynamicCount(), "sistema deveria ter sido removido");
    }

    @Test
    void sistemaInvocadoMudaOVentoDoMundo() {
        StormField field = isolatedField(1L);
        long time = 5000L;
        Vec3 without = field.sampleWind(time, 60.0, 66.0, 0.0, 64, SurfaceRoughness.OPEN_WATER);
        StormSystem s = field.spawn(StormKind.MAJOR_HURRICANE, 4000L, 0.0, 0.0, 0.0, 1.0);
        Vec3 with = field.sampleWind(time, 60.0, 66.0, 0.0, 64, SurfaceRoughness.OPEN_WATER);

        double added = with.sub(without).length();
        assertTrue(added > 0.5, "o sistema invocado deveria somar vento no ponto: " + added);
        assertTrue(s.isAlive(time), "o sistema deveria estar ativo no instante consultado");
    }

    @Test
    void multiplicadorDeIntensidadeEscalaOVento() {
        StormField plain = isolatedField(1L);
        StormSystem normal = plain.spawn(StormKind.HURRICANE, 4000L, 0.0, 0.0, 0.0, 1.0);

        StormField boosted = isolatedField(1L);
        StormSystem strong = boosted.spawn(StormKind.HURRICANE, 4000L, 0.0, 0.0, 0.0, 1.6);

        long time = 10000L;
        assertTrue(strong.centralDeficitHpa(time) > normal.centralDeficitHpa(time) * 2.0,
                "deficit deveria crescer com o quadrado do multiplicador: "
                        + normal.centralDeficitHpa(time) + " -> " + strong.centralDeficitHpa(time));
    }

    @Test
    void oSistemaMaisProximoEFortoSaoEncontrados() {
        StormField field = isolatedField(3L);
        StormSystem near = field.spawn(StormKind.HURRICANE, 0L, 0.0, 0.0, 0.0, 1.0);
        field.spawn(StormKind.MAJOR_HURRICANE, 0L, 2000.0, 0.0, 0.0, 1.0);
        long time = 9000L;

        StormSystem nearest = field.nearest(time, near.centerX(time), near.centerZ(time));
        assertNotNull(nearest, "deveria achar um sistema proximo");
        assertEquals(near.id, nearest.id, "o mais proximo deveria ser o furacao de 0 blocos");

        StormSystem strongest = field.strongestAt(time, near.centerX(time), near.centerZ(time));
        assertNotNull(strongest, "deveria achar um sistema alcancando o ponto");
        assertEquals(near.id, strongest.id,
                "o furacao maior a 2000 blocos nao deveria alcancar o ponto");
    }

    @Test
    void idsDinamicosSaoNegativosUnicosESeparadosDosNaturais() {
        StormField field = new StormField(91L, NORTH, 1.0);
        StormSystem first = field.spawn(StormKind.HURRICANE, 24000L, 0.0, 0.0, 0.0, 1.0);
        StormSystem second = field.spawn(StormKind.HURRICANE, 24000L, 10.0, 0.0, 0.0, 1.0);

        assertTrue(first.id < 0L);
        assertTrue(second.id < 0L);
        assertTrue(first.id != second.id);
        for (StormSystem system : field.activeAt(24000L)) {
            if (!system.playerSpawned) {
                assertTrue(system.id >= 0L, "IDs naturais ocupam o espaco nao-negativo");
                assertTrue(system.id != first.id && system.id != second.id,
                        "ID natural nao pode colidir com ID dinamico");
            }
        }
        assertEquals(first.id, field.dynamicSystems().get(0).id);
        assertEquals(second.id, field.dynamicSystems().get(1).id);
    }

    @Test
    void sistemaNaturalRestauradoNaoDuplicaGeracaoDeterministica() {
        StormField original = new StormField(94L, NORTH, 1.0);
        StormSystem natural = null;
        long foundAt = -1L;
        for (long time = 0L; time < 100000L && natural == null; time += 1000L) {
            for (StormSystem system : original.activeAt(time)) {
                if (!system.playerSpawned) {
                    natural = system;
                    foundAt = time;
                    break;
                }
            }
        }
        assertNotNull(natural, "seed/chance 100% deve produzir um natural ativo na busca");

        StormField restored = new StormField(94L, NORTH, 1.0);
        restored.addDynamic(natural);
        int copies = 0;
        for (StormSystem system : restored.activeAt(foundAt)) {
            if (system.id == natural.id) copies++;
        }
        assertEquals(1, copies, "registro persistido deve sobrepor o natural calculado");
    }

    @Test
    void clienteNaoGeraNaturaisEUsaSistemasRecebidosDoServidor() {
        StormField field = new StormField(93L, NORTH, 1.0);
        field.setAuthoritative(false);
        assertTrue(field.activeAt(24000L).isEmpty(),
                "campo nao autoritativo nao deve inventar tempestades naturais");

        StormSystem replicated = new StormSystem(StormKind.HURRICANE, 1L, 24000L,
                0.0, 0.0, 0.0, 0.0, NORTH, 1.0, false);
        field.addDynamic(replicated);
        assertEquals(1, field.activeAt(24000L).size(),
                "o cliente deve expor o sistema replicado do servidor");
        assertEquals(replicated.id, field.activeAt(24000L).get(0).id);
    }

    @Test
    void idDinamicoRecebidoAvancaContadorSemReutilizarId() {
        StormField field = isolatedField(92L);
        field.addDynamic(new StormSystem(StormKind.HURRICANE, -42L, 0L, 0.0, 0.0,
                0.0, 0.0, NORTH, 1.0, true));
        StormSystem spawned = field.spawn(StormKind.HURRICANE, 0L, 0.0, 0.0, 0.0, 1.0);
        assertTrue(spawned.id < -42L, "ID local deve ficar abaixo do maior ID recebido");
    }

    // ------------------------------------------------------------------

    /** Campo sem geracao natural, para testar so os sistemas invocados. */
    private static StormField isolatedField(long seed) {
        return new StormField(seed, NORTH, 0.0);
    }

    private static double centralHpa(int kind, double f) {
        return VortexBalance.centralDeficitPa(StormKind.vmax(kind), StormKind.rmax(kind),
                StormKind.alpha(kind), f, StormKind.outerRadius(kind)) / 100.0;
    }

    private static boolean contains(List<StormSystem> list, long id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) return true;
        }
        return false;
    }
}
