package com.example.demo.e2e.framework.model;

import java.math.BigDecimal;
import java.time.Instant;

/** A recent fill as returned by {@code GET /portfolio/fills}. */
public record Fill(
        String orderId,
        String symbol,
        Side side,
        BigDecimal quantity,
        BigDecimal price,
        Instant filledAt,
        Long sequenceId) {
}
