package com.example.demo.e2e.framework.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/** Order representation returned by the order-service (used with {@code ApiResponse.as/asList}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(String id, String symbol, Side side, BigDecimal quantity, OrderType type,
                            BigDecimal limitPrice, BigDecimal stopPrice, OrderStatus status) {
}
