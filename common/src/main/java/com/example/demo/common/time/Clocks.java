package com.example.demo.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Shared clock factory for services that need an optional fixed instant during verification. */
public final class Clocks {

    private Clocks() {
    }

    /**
     * Returns a fixed UTC clock when {@code fixedInstant} is set, otherwise the real system UTC
     * clock. Intended for deterministic verification runs without changing the default runtime path.
     */
    public static Clock systemUtcOrFixed(String fixedInstant) {
        if (fixedInstant == null || fixedInstant.isBlank()) {
            return Clock.systemUTC();
        }
        return Clock.fixed(Instant.parse(fixedInstant), ZoneOffset.UTC);
    }
}
