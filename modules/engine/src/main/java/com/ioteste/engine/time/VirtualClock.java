package com.ioteste.engine.time;

import java.time.Instant;
import java.util.function.LongSupplier;

public class VirtualClock {

    private final Instant initialTime;
    private final double timeFactor;
    private final LongSupplier nanoTimeSource;
    private final long initialNanos;

    public VirtualClock(
            Instant initialTime,
            double timeFactor,
            LongSupplier nanoTimeSource) {

        this.initialTime = initialTime;
        this.timeFactor = timeFactor;
        this.nanoTimeSource = nanoTimeSource;
        this.initialNanos = nanoTimeSource.getAsLong();
    }

    public Instant now() {
        long elapsedNanos =
                nanoTimeSource.getAsLong() - initialNanos;

        long virtualElapsedNanos =
                Math.round(elapsedNanos * timeFactor);

        return initialTime.plusNanos(virtualElapsedNanos);
    }
}