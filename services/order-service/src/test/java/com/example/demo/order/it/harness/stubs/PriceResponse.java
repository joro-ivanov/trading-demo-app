package com.example.demo.order.it.harness.stubs;

import java.math.BigDecimal;

/** market-data-service's {@code GET /prices/{symbol}} answer ({@code marketdata/web/PriceResponse}). */
public record PriceResponse(String symbol, BigDecimal price) {
}
