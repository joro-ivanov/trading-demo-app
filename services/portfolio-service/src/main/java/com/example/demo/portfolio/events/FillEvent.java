package com.example.demo.portfolio.events;

import java.math.BigDecimal;
import java.time.Instant;

/** A fill consumed from the {@code fills} topic. {@code side} is "BUY" or "SELL"; {@code owner} scopes the position. */
public record FillEvent(String orderId, String symbol, String side, BigDecimal quantity, BigDecimal price,
                        String owner, Instant filledAt, Long sequenceId) {
}
