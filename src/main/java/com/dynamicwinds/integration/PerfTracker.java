package com.dynamicwinds.integration;

import java.util.concurrent.atomic.AtomicLong;

/** Lightweight rolling performance counters; never used to alter the physics. */
public final class PerfTracker {
    private static final int WINDOW = 200;
    private static final long[] SERVER_NS = new long[WINDOW];
    private static final long[] CLIENT_NS = new long[WINDOW];
    private static int serverCursor, serverCount, clientCursor, clientCount;
    private static long serverTotal, serverPeak, clientTotal, clientPeak;
    private static final AtomicLong WIND_SAMPLES = new AtomicLong();
    private static final AtomicLong HEIGHT_HITS = new AtomicLong();
    private static final AtomicLong HEIGHT_MISSES = new AtomicLong();
    private static final AtomicLong PARTICLES_ACCEPTED = new AtomicLong();

    private PerfTracker() {}

    public static synchronized void recordServerTick(long nanos) {
        long old = serverCount == WINDOW ? SERVER_NS[serverCursor] : 0L;
        SERVER_NS[serverCursor] = nanos;
        serverTotal += nanos - old;
        if (serverCount < WINDOW) serverCount++;
        serverCursor = (serverCursor + 1) % WINDOW;
        serverPeak = Math.max(serverPeak, nanos);
        if (serverCount == WINDOW) {
            long peak = 0L;
            for (long sample : SERVER_NS) peak = Math.max(peak, sample);
            serverPeak = peak;
        }
    }

    public static synchronized void recordClientFrame(long nanos) {
        long old = clientCount == WINDOW ? CLIENT_NS[clientCursor] : 0L;
        CLIENT_NS[clientCursor] = nanos;
        clientTotal += nanos - old;
        if (clientCount < WINDOW) clientCount++;
        clientCursor = (clientCursor + 1) % WINDOW;
        clientPeak = Math.max(clientPeak, nanos);
        if (clientCount == WINDOW) {
            long peak = 0L;
            for (long sample : CLIENT_NS) peak = Math.max(peak, sample);
            clientPeak = peak;
        }
    }

    public static void recordWindSample() { WIND_SAMPLES.incrementAndGet(); }
    public static void recordHeightHit() { HEIGHT_HITS.incrementAndGet(); }
    public static void recordHeightMiss() { HEIGHT_MISSES.incrementAndGet(); }
    public static void recordParticle() { PARTICLES_ACCEPTED.incrementAndGet(); }

    public static synchronized String summary() {
        double serverAvg = serverCount == 0 ? 0.0 : serverTotal / (double) serverCount / 1.0e6;
        double clientAvg = clientCount == 0 ? 0.0 : clientTotal / (double) clientCount / 1.0e6;
        return String.format("server tick avg=%.3fms peak=%.3fms (%d samples); sky render avg=%.3fms peak=%.3fms (%d samples); wind samples=%d; height cache hits=%d misses=%d; particles accepted=%d",
                serverAvg, serverPeak / 1.0e6, serverCount, clientAvg, clientPeak / 1.0e6,
                clientCount, WIND_SAMPLES.get(), HEIGHT_HITS.get(), HEIGHT_MISSES.get(),
                PARTICLES_ACCEPTED.get());
    }
}
