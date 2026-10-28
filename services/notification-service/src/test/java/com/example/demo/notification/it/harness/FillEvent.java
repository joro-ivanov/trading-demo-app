package com.example.demo.notification.it.harness;

import java.math.BigDecimal;
import java.time.Instant;

/** A {@code fills} message exactly as the matcher publishes it (market-data {@code match/FillEvent}). */
public record FillEvent(String orderId, String symbol, String side, BigDecimal quantity, BigDecimal price,
                        String owner, Instant filledAt, Long sequenceId) {
}
