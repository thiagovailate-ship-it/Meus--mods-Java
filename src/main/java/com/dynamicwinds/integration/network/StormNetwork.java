package com.dynamicwinds.integration.network;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.StormSystem;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.world.World;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Canal de rede dos sistemas invocados.
 *
 * <p>O handler do pacote <b>nao</b> toca no mundo: ele apenas enfileira. A fila e
 * drenada no tick do cliente (thread de render), porque aplicar a mudanca direto
 * da thread do netty daria corrida com o VFX, que esta lendo a mesma lista de
 * sistemas ao mesmo tempo.</p>
 */
public final class StormNetwork {

    private static final String CHANNEL = "dynamicwinds";

    private static SimpleNetworkWrapper channel;
    private static final Queue<PacketStormSync> PENDING = new ConcurrentLinkedQueue<PacketStormSync>();

    private StormNetwork() {}

    public static void register() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(Handler.class, PacketStormSync.class, 0, Side.CLIENT);
    }

    public static boolean ready() {
        return channel != null;
    }

    /** Anuncia um sistema invocado para todos os clientes. */
    public static void broadcastSpawn(StormSystem s) {
        if (channel == null || s == null) return;
        channel.sendToAll(PacketStormSync.of(s));
    }

    /** Anuncia a remocao de um sistema pelo id. */
    public static void broadcastRemove(long id) {
        if (channel == null) return;
        channel.sendToAll(PacketStormSync.remove(id));
    }

    /** Anuncia a limpeza de todos os sistemas invocados. */
    public static void broadcastClear() {
        if (channel == null) return;
        channel.sendToAll(PacketStormSync.clear());
    }

    static void enqueue(PacketStormSync packet) {
        if (packet != null) PENDING.add(packet);
    }

    /**
     * Aplica os pacotes recebidos. Chamar somente no cliente, no tick.
     *
     * @param world mundo do cliente (pode ser nulo durante o carregamento)
     */
    public static void drainClient(World world) {
        if (PENDING.isEmpty()) return;
        if (world == null) {
            PENDING.clear();
            return;
        }
        StormField field = Wind.stormsFor(world);
        if (field == null) {
            PENDING.clear();
            return;
        }
        PacketStormSync p;
        while ((p = PENDING.poll()) != null) {
            switch (p.action) {
                case PacketStormSync.CLEAR:
                    field.clearDynamic();
                    break;
                case PacketStormSync.REMOVE:
                    field.removeDynamic(p.id);
                    break;
                case PacketStormSync.ADD:
                    field.addDynamic(new StormSystem(p.kind, p.id, p.spawnTick, p.x, p.z,
                            p.heading, p.turnRate, p.north, p.intensityMul, true));
                    break;
                default:
                    break;
            }
        }
    }

    /** Descarta pacotes pendentes (troca de mundo ou de servidor). */
    public static void reset() {
        PENDING.clear();
    }

    /** Handler do FML: roda na thread do netty, por isso so enfileira. */
    public static class Handler implements IMessageHandler<PacketStormSync, IMessage> {
        @Override
        public IMessage onMessage(PacketStormSync message, MessageContext ctx) {
            enqueue(message);
            return null;
        }
    }
}
