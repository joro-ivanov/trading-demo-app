package com.example.demo.testing.support.fixtures;

import java.time.Instant;

/** Fixed instants for tests whose outcome depends on the trading session (common {@code MarketHours}). */
public final class TradingInstants {

    /** Wednesday 2024-06-12, 14:00 America/New_York (18:00Z): inside the 09:30-16:00 session. */
    public static final Instant DURING_HOURS = Instant.parse("2024-06-12T18:00:00Z");

    private TradingInstants() {
    }
}
