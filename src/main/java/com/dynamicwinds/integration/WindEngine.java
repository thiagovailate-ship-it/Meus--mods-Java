package com.dynamicwinds.integration;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.WindSample;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Server-side engine: ticks every 5 ticks, caches heightmaps in 8x8 cells.
 * Cache persists across ticks; only refreshes near the player occasionally.
 */
public final class WindEngine {
    private static final int UPDATE_RADIUS = 64; // blocks
    private static final int REFRESH_INTERVAL = 40; // ticks (~2 seconds)
    /** Quantos jogadores tem o relevo atualizado por intervalo. */
    private static final int PLAYERS_PER_REFRESH = 3;

    private final Map<Long, HeightmapCache> caches = new HashMap<>();
    private final Map<Long, Integer> lastRefresh = new HashMap<>();
    private final Set<World> restoredWorlds = new HashSet<World>();
    private int tickCounter = 0;

    /**
     * Registra o cache de relevo como fornecedor do modelo de vento: a partir
     * daqui, toda consulta de vento do servidor le altura e classe de
     * superficie da celula de 8x8 ja calculada, em vez de ir ao mundo.
     */
    public WindEngine() {
        Wind.setSurfaceProvider(new Wind.SurfaceProvider() {
            @Override
            public int heightAt(World world, int x, int z) {
                return cacheFor(world).heightAt(x, z);
            }

            @Override
            public int surfaceClassAt(World world, int x, int z) {
                return cacheFor(world).surfaceClassAt(x, z);
            }
        });
    }

    public void tick(World world) {
        if (world.isRemote) return;
        tickCounter++;
        StormField stormField = Wind.stormsFor(world);
        if (restoredWorlds.add(world)) {
            DynamicWindsData.forWorld(world).restore(stormField);
        }
        if (world.getTotalWorldTime() % 6000L == 0L) {
            DynamicWindsData.forWorld(world).capture(world, stormField);
        }
        if (world.getTotalWorldTime() % 20L == 0L) {
            com.dynamicwinds.integration.network.StormNetwork.syncWorld(world);
        }
        if (tickCounter % 5 != 0) return;

        long key = world.provider.dimensionId;
        HeightmapCache cache = caches.computeIfAbsent(key, k -> new HeightmapCache(world));

        // Atualiza o relevo em volta de quem esta jogando: cavar ou construir
        // muda a altura e a rugosidade que o vento sente. A versao anterior
        // sorteava um ponto em [0, 100) - sempre o mesmo pedaco de mundo, perto
        // da origem - e ainda limpava o cache inteiro, o que jogava fora o
        // trabalho feito em todo o resto do mundo.
        int last = lastRefresh.getOrDefault(key, 0);
        if (tickCounter - last >= REFRESH_INTERVAL) {
            lastRefresh.put(key, tickCounter);
            refreshAroundPlayers(world, cache);
        }

        EntityPushHandler.tick(world);
        StormWeather.tick(world);
        DestructionHandler.tick(world);
    }

    /**
     * Recalcula as celulas de relevo em volta dos jogadores, em rotacao: com
     * muitos jogadores online o refresh continua limitado por intervalo, e
     * ninguem fica sem atualizacao por estar no fim da lista.
     */
    private static void refreshAroundPlayers(World world, HeightmapCache cache) {
        List<?> players = world.playerEntities;
        int count = players.size();
        if (count == 0) return;
        int start = (int) (world.getTotalWorldTime() % count);
        int budget = PLAYERS_PER_REFRESH;
        for (int i = 0; i < count && budget > 0; i++) {
            EntityPlayer player = (EntityPlayer) players.get((start + i) % count);
            cache.refreshNear(player.posX, player.posZ, UPDATE_RADIUS);
            budget--;
        }
    }

    public static int cellOf(int coord) { return Wind.cellOf(coord); }

    public HeightmapCache cacheFor(World world) {
        return caches.computeIfAbsent((long) world.provider.dimensionId, k -> new HeightmapCache(world));
    }

    public WindSample sample(World world, double x, double y, double z) {
        return Wind.getWindAt(world, x, y, z);
    }

    /** Called when a world is unloaded to free resources. */
    /** Unload a world and free its cache. */
    public void unloadWorld(World world) {
        if (!world.isRemote) {
            DynamicWindsData.forWorld(world).capture(world, Wind.stormsFor(world));
        }
        restoredWorlds.remove(world);
        caches.remove((long) world.provider.dimensionId);
        lastRefresh.remove((long) world.provider.dimensionId);
        StormWeather.forget(world);
    }
}
