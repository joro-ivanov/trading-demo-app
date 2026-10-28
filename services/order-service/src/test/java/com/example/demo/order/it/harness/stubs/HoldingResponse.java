package com.example.demo.order.it.harness.stubs;

import java.math.BigDecimal;

/** portfolio-service's {@code GET /portfolio/holdings/{symbol}} answer ({@code portfolio/web/HoldingResponse}). */
public record HoldingResponse(String symbol, BigDecimal quantity) {
}
