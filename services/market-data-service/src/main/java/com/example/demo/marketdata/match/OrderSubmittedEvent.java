package com.example.demo.marketdata.match;

import java.math.BigDecimal;

/** Mirror of the {@code orders.submitted} event published by order-service. {@code type} is null for legacy/MARKET. */
public record OrderSubmittedEvent(String id, String symbol, String side, BigDecimal quantity,
                                  String type, BigDecimal limitPrice, BigDecimal stopPrice, String status,
                                  String owner) {
}
