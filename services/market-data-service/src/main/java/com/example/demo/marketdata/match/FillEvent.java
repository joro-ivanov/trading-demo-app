package com.example.demo.marketdata.match;

import java.math.BigDecimal;
import java.time.Instant;

/** A fill emitted to the {@code fills} topic after matching an order at the latest price. */
public record FillEvent(String orderId, String symbol, String side, BigDecimal quantity, BigDecimal price,
                        String owner, Instant filledAt, Long sequenceId) {
}
