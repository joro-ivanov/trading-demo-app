package com.example.demo.order.it.harness.stubs;

import java.math.BigDecimal;

/** The order body order-service routes to the exchange's {@code /exchange/orders}. */
public record ExchangeOrder(String symbol, String side, BigDecimal quantity) {
}
