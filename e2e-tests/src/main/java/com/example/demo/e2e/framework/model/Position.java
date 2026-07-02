package com.example.demo.e2e.framework.model;

import java.math.BigDecimal;

/** A portfolio position as returned by portfolio-service (used with {@code ApiResponse.asList}). */
public record Position(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal marketPrice,
        BigDecimal unrealizedPnl) {
}
