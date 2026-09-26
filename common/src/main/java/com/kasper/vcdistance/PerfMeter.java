package com.kasper.vcdistance;

import java.util.concurrent.atomic.AtomicLong;

/**
 * How long the addon's own work takes per game tick (wall rays, ways round, the room's echo, the
 * server's wall filter), as a smoothed average in milliseconds. Work may be added from any thread;
 * {@link #endTick} is called by the thread that owns the tick.
 */
public final class PerfMeter {

    /** Above this many milliseconds per tick the client spaces its work out. */
    public static final double BUSY_MS = 2.0;
    /** Weight of the newest tick in the average (about the last two seconds count). */
    private static final double SMOOTHING = 0.05;

    private final AtomicLong tickNanos = new AtomicLong();
    private volatile double averageMs;

    /** Adds work done during the current tick. */
    public void add(long nanos) {
        if (nanos > 0) {
            tickNanos.addAndGet(nanos);
        }
    }

    /** Closes the current tick. */
    public void endTick() {
        averageMs += (tickNanos.getAndSet(0) / 1_000_000.0 - averageMs) * SMOOTHING;
    }

    public double averageMs() {
        return averageMs;
    }

    public boolean isBusy() {
        return averageMs > BUSY_MS;
    }

    public void reset() {
        tickNanos.set(0);
        averageMs = 0.0;
    }
}
