package com.example.demo.order.it.harness;

import java.math.BigDecimal;

/** A {@code fills} record exactly as market-data-service's {@code match/FillEvent} is published. */
public record FillMessage(String orderId, String symbol, String side, BigDecimal quantity, BigDecimal price,
                          String owner, String filledAt, long sequenceId) {
}
