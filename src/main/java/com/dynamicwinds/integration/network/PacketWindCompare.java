package com.dynamicwinds.integration.network;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Server sample returned to the requesting client for same-tick parity comparison. */
public class PacketWindCompare implements IMessage {
    public int dimensionId;
    public long time;
    public double x, y, z, storm;
    public double serverX, serverY, serverZ, serverGust;

    private static final Queue<PacketWindCompare> PENDING =
            new ConcurrentLinkedQueue<PacketWindCompare>();

    public PacketWindCompare() {}

    public static PacketWindCompare create(int dimensionId, long time, double x, double y,
                                           double z, double storm, WindSample server) {
        PacketWindCompare packet = new PacketWindCompare();
        packet.dimensionId = dimensionId;
        packet.time = time;
        packet.x = x;
        packet.y = y;
        packet.z = z;
        packet.storm = storm;
        packet.serverX = server.vector.x;
        packet.serverY = server.vector.y;
        packet.serverZ = server.vector.z;
        packet.serverGust = server.gust;
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dimensionId = buf.readInt();
        time = buf.readLong();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        storm = buf.readDouble();
        serverX = buf.readDouble();
        serverY = buf.readDouble();
        serverZ = buf.readDouble();
        serverGust = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimensionId);
        buf.writeLong(time);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(storm);
        buf.writeDouble(serverX);
        buf.writeDouble(serverY);
        buf.writeDouble(serverZ);
        buf.writeDouble(serverGust);
    }

    private static void enqueue(PacketWindCompare packet) {
        if (packet != null) PENDING.add(packet);
    }

    public static void drainClient(World world) {
        if (world == null) return;
        PacketWindCompare packet;
        while ((packet = PENDING.poll()) != null) {
            if (packet.dimensionId != world.provider.dimensionId) continue;
            WindSample client = Wind.getWindAtTime(world, packet.x, packet.y, packet.z,
                    packet.time, packet.storm);
            double dx = client.vector.x - packet.serverX;
            double dy = client.vector.y - packet.serverY;
            double dz = client.vector.z - packet.serverZ;
            double maxDelta = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
            boolean roundedMatch = Math.round(packet.serverX * 10.0) == Math.round(client.vector.x * 10.0)
                    && Math.round(packet.serverY * 10.0) == Math.round(client.vector.y * 10.0)
                    && Math.round(packet.serverZ * 10.0) == Math.round(client.vector.z * 10.0);
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(String.format(
                        java.util.Locale.ROOT,
                        "Wind parity t=%d server=(%.3f,%.3f,%.3f) client=(%.3f,%.3f,%.3f) "
                                + "deltaMax=%.6f rounded@0.1=%s gust=(%.3f/%.3f)", packet.time,
                        packet.serverX, packet.serverY, packet.serverZ,
                        client.vector.x, client.vector.y, client.vector.z, maxDelta,
                        roundedMatch ? "MATCH" : "DIFF", packet.serverGust, client.gust)));\n            }
        }
    }

    public static void reset() { PENDING.clear(); }

    public static class Handler implements IMessageHandler<PacketWindCompare, IMessage> {
        @Override
        public IMessage onMessage(PacketWindCompare message, MessageContext context) {
            enqueue(message);
            return null;
        }
    }
}
