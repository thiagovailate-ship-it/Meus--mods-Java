package com.dynamicwinds.integration.network;

import com.dynamicwinds.core.StormSystem;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * Replicates immutable storm birth parameters from the server to clients, scoped
 * by dimension. Systems may be naturally generated or player-invoked.
 *
 * <p>So trafega os parametros de nascimento (tipo, id, tick, posicao, rumo,
 * curvatura, intensidade e hemisferio). A evolucao do sistema - intensidade,
 * expansao do raio, centro, campo de vento - e uma funcao pura desses parametros
 * mais o relogio do mundo, entao o cliente recalcula tudo sozinho. Isso evita
 * pacotes por tick e mantem o VFX exatamente igual a fisica do servidor.</p>
 *
 * <p>Natural systems are included in periodic per-dimension snapshots. Clients
 * do not generate their own natural storms, so the server's field is the source
 * of truth even if client configuration or world seed differs.</p>
 */
public class PacketStormSync implements IMessage {

    /** Cria/atualiza um sistema. */
    public static final byte ADD = 0;
    /** Remove um sistema pelo id. */
    public static final byte REMOVE = 1;
    /** Remove todos os sistemas invocados (troca de mundo / limpeza). */
    public static final byte CLEAR = 2;
    /** Replace the client's complete view of this dimension before following ADDs. */
    public static final byte REPLACE = 3;

    public byte action;
    public int dimensionId;
    public int kind;
    public long id;
    public long spawnTick;
    public double x;
    public double z;
    public double heading;
    public double turnRate;
    public double intensityMul;
    public boolean north;
    public boolean playerSpawned;

    public PacketStormSync() {}

    /** Pacote de criacao, montado a partir do proprio sistema. */
    public static PacketStormSync of(StormSystem s, int dimensionId) {
        PacketStormSync p = new PacketStormSync();
        p.action = ADD;
        p.dimensionId = dimensionId;
        p.kind = s.kind;
        p.id = s.id;
        p.spawnTick = s.spawnTick;
        p.x = s.spawnX;
        p.z = s.spawnZ;
        p.heading = s.heading0;
        p.turnRate = s.turnRate;
        p.intensityMul = s.intensityMul;
        p.north = s.north;
        p.playerSpawned = s.playerSpawned;
        return p;
    }

    public static PacketStormSync remove(long id, int dimensionId) {
        PacketStormSync p = new PacketStormSync();
        p.action = REMOVE;
        p.dimensionId = dimensionId;
        p.id = id;
        return p;
    }

    public static PacketStormSync clear(int dimensionId) {
        PacketStormSync p = new PacketStormSync();
        p.action = CLEAR;
        p.dimensionId = dimensionId;
        return p;
    }

    public static PacketStormSync replace(int dimensionId) {
        PacketStormSync p = new PacketStormSync();
        p.action = REPLACE;
        p.dimensionId = dimensionId;
        return p;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        dimensionId = buf.readInt();
        if (action == CLEAR || action == REPLACE) return;
        id = buf.readLong();
        if (action == REMOVE) return;
        kind = buf.readByte();
        spawnTick = buf.readLong();
        x = buf.readDouble();
        z = buf.readDouble();
        heading = buf.readDouble();
        turnRate = buf.readDouble();
        intensityMul = buf.readDouble();
        north = buf.readBoolean();
        playerSpawned = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        buf.writeInt(dimensionId);
        if (action == CLEAR || action == REPLACE) return;
        buf.writeLong(id);
        if (action == REMOVE) return;
        buf.writeByte(kind);
        buf.writeLong(spawnTick);
        buf.writeDouble(x);
        buf.writeDouble(z);
        buf.writeDouble(heading);
        buf.writeDouble(turnRate);
        buf.writeDouble(intensityMul);
        buf.writeBoolean(north);
        buf.writeBoolean(playerSpawned);
    }
}
