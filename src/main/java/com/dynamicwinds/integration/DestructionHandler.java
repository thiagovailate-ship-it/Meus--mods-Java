package com.dynamicwinds.integration;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.content.ContentRegistry;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.StormSystem;
import com.dynamicwinds.core.StormKind;
import com.dynamicwinds.core.SurfaceRoughness;
import com.dynamicwinds.core.WindSample;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;

/**
 * Dano pesado: o vento arranca blocos, derruba arvores e machuca o que estiver
 * exposto.
 *
 * <p><b>Nada aqui usa tabela de "raio de destruicao".</b> Cada bloco candidato e
 * consultado no <i>mesmo</i> campo de vento que o resto do mod usa
 * ({@link Wind#getWindAt}), e quebra se a velocidade local passar da resistencia
 * do material. Consequencias que caem de graca:</p>
 * <ul>
 *   <li>O olho do furacao e calmo, entao nada quebra ali no meio.</li>
 *   <li>A parede do olho e a faixa mais destrutiva, e ela migra com o sistema.</li>
 *   <li>A destruicao enfraquece no landfall junto com o proprio sistema, porque
 *       a energia da superficie ja esta embutida no campo.</li>
 *   <li>Uma tempestade tropical arranca folhas e vidro, mas nao derruba
 *       estruturas; so um furacao maior chega a madeira e pedra.</li>
 * </ul>
 *
 * <p><b>Escala da resistencia:</b> {@code v_critico = 9 + 24 * dureza} m/s. Com
 * isso a folhagem (dureza 0.2) sai a ~14 m/s, terra e areia (0.5) a ~21 m/s,
 * madeira e tronco (2.0) a ~57 m/s, e obsidiana (50) praticamente nunca - o que
 * reproduz a escala de dano de um furacao real e ainda protege blocos que o
 * jogador escolheu como cofre.</p>
 *
 * <p><b>Custo:</b> o trabalho e limitado por tick
 * ({@link WindConfig#destructionBlocksPerTick}) e so ocorre perto de jogadores,
 * onde os chunks ja estao carregados.</p>
 */
public final class DestructionHandler {

    /** Blocos de tronco derrubados em cadeia quando a base cede. */
    private static final int TRUNK_FALL = 12;
    /** Distancia em blocos ao redor do jogador em que o dano e simulado. */
    private static final double BASE_RADIUS = 48.0;
    /** Blocos acima da superficie ainda atingidos (muros, telhados, arvores). */
    private static final int REACH_UP = 16;
    /** Blocos abaixo da superficie (o vento arranca o chao exposto). */
    private static final int REACH_DOWN = 3;
    /** Raio protegido ao redor do ponto de nascimento, em blocos. */
    private static final double SPAWN_SAFE_RADIUS = 64.0;

    private static final double TWO_PI = Math.PI * 2.0;

    /** Fonte de dano propria, para a mensagem de morte fazer sentido. */
    public static final DamageSource STORM_DAMAGE = new DamageSource("dynamicwinds.storm");
    private static final Map<String, Integer> BROKEN_BY_STORM = new HashMap<String, Integer>();
    private static final Map<Integer, Stats> STATS = new HashMap<Integer, Stats>();
    private static int blocksBrokenThisTick;

    private static final class Stats {
        long minute = Long.MIN_VALUE;
        int broken;
        int warned;
        final Map<String, Integer> blockTypes = new HashMap<String, Integer>();
        final Set<String> loggedWarnTypes = new HashSet<String>();

        void roll(long worldTime) {
            long currentMinute = worldTime / 1200L;
            if (minute != currentMinute) {
                minute = currentMinute;
                broken = warned = 0;
                blockTypes.clear();
                loggedWarnTypes.clear();
            }
        }
    }

    /** Velocidade minima para ferir uma entidade (m/s). */
    private static final double HURT_THRESHOLD = 26.0;
    /** Velocidade minima para ferir um jogador (m/s): so Cat 3 ou acima. */
    private static final double HURT_THRESHOLD_PLAYER = 55.0;

    private DestructionHandler() {}

    public static void tick(World world) {
        if (world.isRemote) return;
        blocksBrokenThisTick = 0;
        if (!WindConfig.enableDestruction) return;
        if (WindConfig.destructionProtectedDimension(world.provider.dimensionId)) return;

        long time = world.getTotalWorldTime();
        StormField field = Wind.stormsFor(world);
        if (field == null) return;
        SurfaceRoughness energy = Wind.surfaceEnergyFor(world);
        List<StormSystem> active = field.activeAt(time, energy);
        Stats stats = stats(world.provider.dimensionId, time);
        Set<String> activeKeys = new HashSet<String>();
        for (StormSystem system : active) {
            activeKeys.add(stormKey(world.provider.dimensionId, system.id));
        }
        java.util.Iterator<String> broken = BROKEN_BY_STORM.keySet().iterator();
        while (broken.hasNext()) {
            String key = broken.next();
            if (key.startsWith(world.provider.dimensionId + ":") && !activeKeys.contains(key)) broken.remove();
        }
        if (active.isEmpty()) return;

        List players = world.playerEntities;
        if (players.isEmpty()) return;

        int perPlayer = Math.max(1, WindConfig.destructionBlocksPerTick / players.size());
        for (int i = 0; i < players.size(); i++) {
            EntityPlayer p = (EntityPlayer) players.get(i);
            carve(world, p, perPlayer, field, time, stats);
        }
        damageEntities(world, field, time);
    }

    // ------------------------------------------------------------------
    // blocos
    // ------------------------------------------------------------------

    private static void carve(World world, EntityPlayer p, int budget, StormField field, long time, Stats stats) {
        Random rand = world.rand;
        double radius = BASE_RADIUS * WindConfig.destructionRadiusScale;
        int pbx = (int) Math.floor(p.posX);
        int pbz = (int) Math.floor(p.posZ);

        for (int i = 0; i < budget; i++) {
            double angle = rand.nextDouble() * TWO_PI;
            double r = Math.sqrt(rand.nextDouble()) * radius; // uniforme em area
            int x = pbx + (int) Math.round(Math.cos(angle) * r);
            int z = pbz + (int) Math.round(Math.sin(angle) * r);
            if (!world.blockExists(x, 64, z)) continue;
            if (WindConfig.destructionProtectSpawn && nearSpawn(world, x, z)) continue;

            // a mesma altura que o modelo de vento usa (celula de 8x8, vinda do
            // cache do servidor em vez de uma leitura de mundo por bloco candidato)
            int surface = Wind.surfaceHeightAt(world, x, z);
            int y = surface - REACH_DOWN + rand.nextInt(REACH_DOWN + REACH_UP);
            tryBreak(world, x, y, z, rand, field, time, stats);
        }
    }

    /**
     * Avalia um bloco. Este e o ponto onde o vento encontra a resistencia do
     * material: nada e destruido sem passar pelo campo fisico.
     */
    private static boolean tryBreak(World world, int x, int y, int z, Random rand,
                                    StormField field, long time, Stats stats) {
        Block block = world.getBlock(x, y, z);
        if (block == null) return false;
        if (world.isAirBlock(x, y, z)) return false;
        if (blocksBrokenThisTick >= WindConfig.destructionBlocksPerTick) return false;
        Object registryKey = Block.blockRegistry.getNameForObject(block);
        String blockName = registryKey == null ? block.getUnlocalizedName() : registryKey.toString();
        if (WindConfig.isProtectedBlock(blockName)) return false;

        Material mat = block.getMaterial();
        if (mat == null || mat.isLiquid()) return false;

        // blocos com inventario/estado (bau, forno, maquinas) sao preservados:
        // destruir isso apagaria dados do jogador sem aviso
        if (world.getTileEntity(x, y, z) != null) return false;
        // os proprios instrumentos do mod tambem ficam de pe
        if (block == ContentRegistry.windVane || block == ContentRegistry.windsock
                || block == ContentRegistry.anemometer) {
            return false;
        }

        float hardness = block.getBlockHardness(world, x, y, z);
        if (hardness < 0.0F) return false; // indestrutivel (bedrock, portal, etc.)

        StormSystem cause = field.strongestAt(time, x + 0.5, z + 0.5);
        if (cause == null) return false;
        String stormKey = stormKey(world.provider.dimensionId, cause.id);
        int alreadyBroken = BROKEN_BY_STORM.containsKey(stormKey)
                ? BROKEN_BY_STORM.get(stormKey).intValue() : 0;
        if (alreadyBroken >= WindConfig.destructionMaxPerStorm) return false;

        double needed = criticalSpeed(hardness);
        WindSample s = Wind.getWindAt(world, x + 0.5, y + 0.5, z + 0.5);
        if (s.speed < needed) return false;
        if (WindConfig.destructionWarnOnly) {
            stats.warned++;
            stats.blockTypes.put(blockName, Integer.valueOf(stats.blockTypes.containsKey(blockName)
                    ? stats.blockTypes.get(blockName).intValue() + 1 : 1));
            if (stats.loggedWarnTypes.add(blockName)) {
                System.out.println("DynamicWinds warn-only: would break " + blockName
                        + " at " + x + "," + y + "," + z + " (" + s.speed + " m/s)");
            }
            return false;
        }

        int meta = world.getBlockMetadata(x, y, z);
        if (WindConfig.destructionDropItems) {
            block.dropBlockAsItem(world, x, y, z, meta, 0);
        }
        world.setBlockToAir(x, y, z);
        blocksBrokenThisTick++;
        BROKEN_BY_STORM.put(stormKey, Integer.valueOf(alreadyBroken + 1));
        stats.broken++;
        stats.blockTypes.put(blockName, Integer.valueOf(stats.blockTypes.containsKey(blockName)
                ? stats.blockTypes.get(blockName).intValue() + 1 : 1));
        // efeito de impacto: poeira no ponto arrancado
        world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) + (meta << 12));

        if (isTrunk(block)) {
            int felled = 0;
            for (int dy = 1; dy <= TRUNK_FALL; dy++) {
                if (blocksBrokenThisTick >= WindConfig.destructionBlocksPerTick
                        || alreadyBroken >= WindConfig.destructionMaxPerStorm) break;
                Block up = world.getBlock(x, y + dy, z);
                if (!isTrunk(up)) break;
                Object upKey = Block.blockRegistry.getNameForObject(up);
                String upName = upKey == null ? up.getUnlocalizedName() : upKey.toString();
                if (WindConfig.isProtectedBlock(upName)) break;
                if (WindConfig.destructionDropItems) {
                    up.dropBlockAsItem(world, x, y + dy, z, world.getBlockMetadata(x, y + dy, z), 0);
                }
                world.setBlockToAir(x, y + dy, z);
                blocksBrokenThisTick++;
                alreadyBroken++;
                BROKEN_BY_STORM.put(stormKey, Integer.valueOf(alreadyBroken));
                stats.broken++;
                stats.blockTypes.put(upName, Integer.valueOf(stats.blockTypes.containsKey(upName)
                        ? stats.blockTypes.get(upName).intValue() + 1 : 1));
                if (++felled >= 3 + rand.nextInt(4)) break; // nem toda arvore cede inteira
            }
        }
        return true;
    }

    private static Stats stats(int dimensionId, long time) {
        Integer key = Integer.valueOf(dimensionId);
        Stats stats = STATS.get(key);
        if (stats == null) {
            stats = new Stats();
            STATS.put(key, stats);
        }
        stats.roll(time);
        return stats;
    }

    private static String stormKey(int dimensionId, long id) {
        return dimensionId + ":" + id;
    }

    private static String stormSlug(int kind) {
        switch (kind) {
            case StormKind.TROPICAL_STORM: return "tropical";
            case StormKind.HURRICANE: return "hurricane";
            case StormKind.MAJOR_HURRICANE: return "major";
            default: return "vortex";
        }
    }

    public static String statsSummary(int dimensionId, long time) {
        Stats stats = stats(dimensionId, time);
        return "last minute: broken=" + stats.broken + ", warnOnly=" + stats.warned
                + ", blockTypes=" + stats.blockTypes;
    }

    /** Velocidade critica de arrancamento, em m/s, a partir da dureza do bloco. */
    public static double criticalSpeed(float hardness) {
        return 9.0 + 24.0 * Math.max(0.0F, hardness);
    }

    /** Tritoncos de qualquer especie (as duas familias de madeira do jogo). */
    private static boolean isTrunk(Block b) {
        return b == Blocks.log || b == Blocks.log2;
    }

    private static boolean nearSpawn(World world, int x, int z) {
        net.minecraft.util.ChunkCoordinates spawn = world.getSpawnPoint();
        if (spawn == null) return false;
        double dx = x - spawn.posX;
        double dz = z - spawn.posZ;
        return dx * dx + dz * dz < SPAWN_SAFE_RADIUS * SPAWN_SAFE_RADIUS;
    }

    // ------------------------------------------------------------------
    // entidades
    // ------------------------------------------------------------------

    /**
     * Machuca o que esta exposto ao vento: destrocos voando, arrancados de
     * estruturas, atingem quem estiver na faixa mais violenta.
     */
    private static void damageEntities(World world, StormField field, long time) {
        List loaded = world.loadedEntityList;
        for (int i = 0; i < loaded.size(); i++) {
            Entity e = (Entity) loaded.get(i);
            if (e.isDead) continue;
            if (e.hurtResistantTime > 0) continue;

            WindSample s = Wind.getWindAt(world, e.posX, e.posY, e.posZ);
            boolean player = e instanceof EntityPlayer;
            double threshold = player ? HURT_THRESHOLD_PLAYER : HURT_THRESHOLD;
            if (s.speed < threshold) continue;

            if (player && ((EntityPlayer) e).capabilities.isCreativeMode) continue;
            // escalonamento quadratico: passar do limiar machuca, mas o pico de
            // um furacao maior e o que mata
            double over = s.speed - threshold;
            float damage = (float) (1.0 + over * over * 0.02);
            StormSystem cause = field.strongestAt(time, e.posX, e.posZ);
            String damageKey = cause == null ? "dynamicwinds.storm"
                    : "dynamicwinds.storm." + stormSlug(cause.kind);
            e.attackEntityFrom(new DamageSource(damageKey), damage);
        }
    }
}
