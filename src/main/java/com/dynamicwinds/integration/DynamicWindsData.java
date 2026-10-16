package com.dynamicwinds.integration;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.StormSystem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

import java.util.ArrayList;
import java.util.List;

/** Persists the immutable birth parameters of storms for this dimension. */
public final class DynamicWindsData extends WorldSavedData {
    private static final String DATA_KEY = "dynamicwinds_storms_v1";
    private static final String SYSTEMS_KEY = "systems";
    private final List<StormSystem> systems = new ArrayList<StormSystem>();

    public DynamicWindsData(String name) {
        super(name);
    }

    public static DynamicWindsData forWorld(World world) {
        MapStorage storage = world.perWorldStorage;
        DynamicWindsData data = (DynamicWindsData) storage.loadData(
                DynamicWindsData.class, DATA_KEY);
        if (data == null) {
            data = new DynamicWindsData(DATA_KEY);
            storage.setData(DATA_KEY, data);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        systems.clear();
        NBTTagList list = tag.getTagList(SYSTEMS_KEY, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound item = list.getCompoundTagAt(i);
            systems.add(new StormSystem(item.getInteger("kind"), item.getLong("id"),
                    item.getLong("spawnTick"), item.getDouble("spawnX"),
                    item.getDouble("spawnZ"), item.getDouble("heading"),
                    item.getDouble("turnRate"), item.getBoolean("north"),
                    item.getDouble("intensity"), item.getBoolean("playerSpawned")));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (StormSystem system : systems) {
            NBTTagCompound item = new NBTTagCompound();
            item.setInteger("kind", system.kind);
            item.setLong("id", system.id);
            item.setLong("spawnTick", system.spawnTick);
            item.setDouble("spawnX", system.spawnX);
            item.setDouble("spawnZ", system.spawnZ);
            item.setDouble("heading", system.heading0);
            item.setDouble("turnRate", system.turnRate);
            item.setBoolean("north", system.north);
            item.setDouble("intensity", system.intensityMul);
            item.setBoolean("playerSpawned", system.playerSpawned);
            list.appendTag(item);
        }
        tag.setTag(SYSTEMS_KEY, list);
    }

    /** Restore a save snapshot into the server's field, deduplicating natural IDs. */
    public void restore(StormField field) {
        for (StormSystem system : systems) field.addDynamic(system);
    }

    /** Snapshot only living systems; derived state is recalculated from world time. */
    public void capture(World world, StormField field) {
        systems.clear();
        long time = world.getTotalWorldTime();
        for (StormSystem system : field.activeAt(time, Wind.surfaceEnergyFor(world))) {
            systems.add(system);
        }
        markDirty();
    }
}
