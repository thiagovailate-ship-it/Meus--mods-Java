package com.dynamicwinds.integration;

import com.dynamicwinds.core.Vec3;
import com.dynamicwinds.core.WindSample;
import com.dynamicwinds.integration.network.PacketWindCompare;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketWindCompareTest {
    @Test
    void sameTickParityPacketRoundTripsAllComparisonInputs() {
        WindSample sample = new WindSample(new Vec3(-3.25, 1.5, 8.125), 9.0, 1.125);
        PacketWindCompare original = PacketWindCompare.create(-1, 45678L,
                12.5, 70.0, -91.25, 0.625, sample);
        ByteBuf buffer = Unpooled.buffer();
        try {
            original.toBytes(buffer);
            PacketWindCompare decoded = new PacketWindCompare();
            decoded.fromBytes(buffer);
            assertEquals(-1, decoded.dimensionId);
            assertEquals(45678L, decoded.time);
            assertEquals(12.5, decoded.x, 0.0);
            assertEquals(70.0, decoded.y, 0.0);
            assertEquals(-91.25, decoded.z, 0.0);
            assertEquals(0.625, decoded.storm, 0.0);
            assertEquals(-3.25, decoded.serverX, 0.0);
            assertEquals(1.5, decoded.serverY, 0.0);
            assertEquals(8.125, decoded.serverZ, 0.0);
            assertEquals(1.125, decoded.serverGust, 0.0);
        } finally {
            buffer.release();
        }
    }
}
