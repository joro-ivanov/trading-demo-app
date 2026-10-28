package com.example.demo.order.it.harness.stubs;

import java.math.BigDecimal;

/** The body order-service's {@code RiskClient} posts to risk-service's {@code /risk/check}. */
public record RiskCheckRequest(String symbol, String side, BigDecimal quantity, BigDecimal price) {
}
