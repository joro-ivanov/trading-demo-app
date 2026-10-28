package com.example.demo.order.it.harness;

import java.math.BigDecimal;

/**
 * The {@code POST /orders} body as a client sends it. {@code side} and {@code type} are text so a test
 * can also send a value the service's enums reject; an absent field is {@code null}. The production
 * {@code PlaceOrderRequest} is not used because it would also serialize its derived validation flags.
 */
public record OrderRequest(String symbol, String side, BigDecimal quantity, String type,
                           BigDecimal limitPrice, BigDecimal stopPrice) {
}
