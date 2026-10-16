package com.dynamicwinds.integration;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StormPersistenceTest {
    @Test
    void missingLegacyDataLoadsAsAnEmptySystemList() {
        DynamicWindsData data = new DynamicWindsData("legacy");
        data.readFromNBT(new NBTTagCompound());
        NBTTagCompound output = new NBTTagCompound();
        data.writeToNBT(output);
        assertEquals(0, output.getTagList("systems", 10).tagCount());
    }

    @Test
    void emptyListRoundTripsWithoutCreatingPhantomSystems() {
        DynamicWindsData data = new DynamicWindsData("empty");
        data.readFromNBT(new NBTTagCompound());
        NBTTagCompound first = new NBTTagCompound();
        data.writeToNBT(first);
        DynamicWindsData loaded = new DynamicWindsData("empty");
        loaded.readFromNBT(first);
        NBTTagCompound second = new NBTTagCompound();
        loaded.writeToNBT(second);
        assertEquals(0, second.getTagList("systems", 10).tagCount());
    }

    @Test
    void birthRecordNbtRoundTripsEveryStormSystemField() {
        NBTTagCompound input = new NBTTagCompound();
        NBTTagCompound record = new NBTTagCompound();
        record.setInteger("kind", 3);
        record.setLong("id", -77L);
        record.setLong("spawnTick", 123456L);
        record.setDouble("spawnX", -321.25);
        record.setDouble("spawnZ", 987.5);
        record.setDouble("heading", 1.23456789);
        record.setDouble("turnRate", -0.0000012);
        record.setBoolean("north", false);
        record.setDouble("intensity", 1.73);
        record.setBoolean("playerSpawned", true);
        NBTTagList systems = new NBTTagList();
        systems.appendTag(record);
        input.setTag("systems", systems);

        DynamicWindsData data = new DynamicWindsData("test");
        data.readFromNBT(input);
        NBTTagCompound output = new NBTTagCompound();
        data.writeToNBT(output);
        NBTTagCompound restored = output.getTagList("systems", 10).getCompoundTagAt(0);
        assertEquals(1, output.getTagList("systems", 10).tagCount());
        assertEquals(3, restored.getInteger("kind"));
        assertEquals(-77L, restored.getLong("id"));
        assertEquals(123456L, restored.getLong("spawnTick"));
        assertEquals(-321.25, restored.getDouble("spawnX"), 0.0);
        assertEquals(987.5, restored.getDouble("spawnZ"), 0.0);
        assertEquals(1.23456789, restored.getDouble("heading"), 0.0);
        assertEquals(-0.0000012, restored.getDouble("turnRate"), 0.0);
        assertTrue(!restored.getBoolean("north"));
        assertEquals(1.73, restored.getDouble("intensity"), 0.0);
        assertTrue(restored.getBoolean("playerSpawned"));
    }
}
