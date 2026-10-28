package com.example.demo.order.it.harness;

import java.math.BigDecimal;

/** The {@code orders.submitted} payload as published by {@code OrderEventPublisher}. */
public record OrderSubmittedMessage(String id, String symbol, String side, BigDecimal quantity, String type,
                                    BigDecimal limitPrice, BigDecimal stopPrice, String status, String owner) {
}
