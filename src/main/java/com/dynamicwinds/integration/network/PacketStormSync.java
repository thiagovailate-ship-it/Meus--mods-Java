package com.dynamicwinds.integration.network;

import com.dynamicwinds.core.StormSystem;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * Pacote que sincroniza sistemas de tempo severo <b>invocados</b> (Cajado da
 * Catastrofe / comando) com os clientes.
 *
 * <p>So trafega os parametros de nascimento (tipo, id, tick, posicao, rumo,
 * curvatura, intensidade e hemisferio). A evolucao do sistema - intensidade,
 * expansao do raio, centro, campo de vento - e uma funcao pura desses parametros
 * mais o relogio do mundo, entao o cliente recalcula tudo sozinho. Isso evita
 * pacotes por tick e mantem o VFX exatamente igual a fisica do servidor.</p>
 *
 * <p>Sistemas naturais nao aparecem aqui: eles sao derivados da seed e do
 * relogio, e cliente e servidor chegam ao mesmo resultado sem trocar nada.</p>
 */
public class PacketStormSync implements IMessage {

    /** Cria/atualiza um sistema. */
    public static final byte ADD = 0;
    /** Remove um sistema pelo id. */
    public static final byte REMOVE = 1;
    /** Remove todos os sistemas invocados (troca de mundo / limpeza). */
    public static final byte CLEAR = 2;

    public byte action;
    public int kind;
    public long id;
    public long spawnTick;
    public double x;
    public double z;
    public double heading;
    public double turnRate;
    public double intensityMul;
    public boolean north;

    public PacketStormSync() {}

    /** Pacote de criacao, montado a partir do proprio sistema. */
    public static PacketStormSync of(StormSystem s) {
        PacketStormSync p = new PacketStormSync();
        p.action = ADD;
        p.kind = s.kind;
        p.id = s.id;
        p.spawnTick = s.spawnTick;
        p.x = s.spawnX;
        p.z = s.spawnZ;
        p.heading = s.heading0;
        p.turnRate = s.turnRate;
        p.intensityMul = s.intensityMul;
        p.north = s.north;
        return p;
    }

    public static PacketStormSync remove(long id) {
        PacketStormSync p = new PacketStormSync();
        p.action = REMOVE;
        p.id = id;
        return p;
    }

    public static PacketStormSync clear() {
        PacketStormSync p = new PacketStormSync();
        p.action = CLEAR;
        return p;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        if (action == CLEAR) return;
        id = buf.readLong();
        if (action == REMOVE) return;
        kind = buf.readByte();
        spawnTick = buf.readLong();
        x = buf.readDouble();
        z = buf.readDouble();
        heading = buf.readDouble();
        turnRate = buf.readDouble();
        intensityMul = buf.readFloat();
        north = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        if (action == CLEAR) return;
        buf.writeLong(id);
        if (action == REMOVE) return;
        buf.writeByte(kind);
        buf.writeLong(spawnTick);
        buf.writeDouble(x);
        buf.writeDouble(z);
        buf.writeDouble(heading);
        buf.writeDouble(turnRate);
        buf.writeFloat((float) intensityMul);
        buf.writeBoolean(north);
    }
}
