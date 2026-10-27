package com.ioteste.engine.time;

import java.math.BigInteger;
import java.time.Instant;
import java.util.function.LongSupplier;

/** The time factor is stored in thousandths (1500 means 1.5x). */
public class VirtualClock {

    private static final BigInteger SCALE = BigInteger.valueOf(1000L);
    private final Instant initialTime;
    private final long timeFactor;
    private final LongSupplier nanoTimeSource;
    private final long initialNanos;

    public VirtualClock(Instant initialTime, long timeFactor,
                        LongSupplier nanoTimeSource) {
        if (timeFactor <= 0) {
            throw new IllegalArgumentException("timeFactor debe ser positivo");
        }
        this.initialTime = initialTime;
        this.timeFactor = timeFactor;
        this.nanoTimeSource = nanoTimeSource;
        this.initialNanos = nanoTimeSource.getAsLong();
    }

    public Instant now() {
        long elapsedNanos = nanoTimeSource.getAsLong() - initialNanos;
        long virtualElapsedNanos = BigInteger.valueOf(elapsedNanos)
                .multiply(BigInteger.valueOf(timeFactor))
                .divide(SCALE)
                .longValueExact();
        return initialTime.plusNanos(virtualElapsedNanos);
    }
}
