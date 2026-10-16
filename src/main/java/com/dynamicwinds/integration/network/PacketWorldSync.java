package com.dynamicwinds.integration.network;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.integration.WindConfig;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.world.World;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Server-authoritative seed and wind/storm settings for one world dimension. */
public class PacketWorldSync implements IMessage {
    public int dimensionId;
    public long seed;
    public boolean north;
    public double spawnChance;
    public double baseSpeedMultiplier;
    public boolean stormsEnabled;
    public boolean forceWeather;
    public int rainRate;
    public boolean destructionEnabled;

    private static final Queue<PacketWorldSync> PENDING =
            new ConcurrentLinkedQueue<PacketWorldSync>();

    public PacketWorldSync() {}

    public static PacketWorldSync fromServer(int dimensionId, long seed) {
        PacketWorldSync packet = new PacketWorldSync();
        packet.dimensionId = dimensionId;
        packet.seed = seed;
        packet.north = WindConfig.north();
        packet.spawnChance = WindConfig.stormSpawnChance();
        packet.baseSpeedMultiplier = WindConfig.baseSpeedMultiplier;
        packet.stormsEnabled = WindConfig.stormsEnabled();
        packet.forceWeather = WindConfig.stormForceWeather;
        packet.rainRate = WindConfig.stormRainRate();
        packet.destructionEnabled = WindConfig.enableDestruction;
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dimensionId = buf.readInt();
        seed = buf.readLong();
        north = buf.readBoolean();
        spawnChance = buf.readDouble();
        baseSpeedMultiplier = buf.readDouble();
        stormsEnabled = buf.readBoolean();
        forceWeather = buf.readBoolean();
        rainRate = buf.readInt();
        destructionEnabled = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimensionId);
        buf.writeLong(seed);
        buf.writeBoolean(north);
        buf.writeDouble(spawnChance);
        buf.writeDouble(baseSpeedMultiplier);
        buf.writeBoolean(stormsEnabled);
        buf.writeBoolean(forceWeather);
        buf.writeInt(rainRate);
        buf.writeBoolean(destructionEnabled);
    }

    private static void enqueue(PacketWorldSync packet) {
        if (packet != null) PENDING.add(packet);
    }

    /** Apply queued settings on the client tick, never on Netty's thread. */
    public static void drainClient(World world) {
        PacketWorldSync packet;
        while ((packet = PENDING.poll()) != null) {
            Wind.setSyncedSeed(packet.dimensionId, packet.seed);
            WindConfig.applyServerSettings(packet.north, packet.spawnChance,
                    packet.stormsEnabled, packet.forceWeather, packet.rainRate,
                    packet.destructionEnabled, packet.baseSpeedMultiplier);
        }
    }

    public static void reset() {
        PENDING.clear();
    }

    public static class Handler implements IMessageHandler<PacketWorldSync, IMessage> {
        @Override
        public IMessage onMessage(PacketWorldSync message, MessageContext context) {
            enqueue(message);
            return null;
        }
    }
}
