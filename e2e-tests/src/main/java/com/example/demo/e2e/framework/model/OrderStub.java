package com.example.demo.e2e.framework.model;

import java.math.BigDecimal;

/**
 * Order-service response shape for UI route stubs. Unlike {@link OrderResponse} it keeps {@code null}
 * prices in the JSON, as the real service does and as the dashboard's {@code Order} type expects.
 */
public record OrderStub(String id, String symbol, Side side, int quantity, OrderType type,
                        BigDecimal limitPrice, BigDecimal stopPrice, OrderStatus status) {
}
