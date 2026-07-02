package com.example.demo.order.web;

import java.math.BigDecimal;

import com.example.demo.order.domain.OrderType;
import com.example.demo.order.domain.Side;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Inbound order payload. Bean Validation here is the <i>application's</i> job (rejecting bad
 * input with 400) — distinct from the e2e service framework, which never validates so that
 * negative scenarios can reach this endpoint (docs/DESIGN.md §8.2).
 *
 * <p>{@code type} defaults to MARKET when omitted. A LIMIT order requires a positive
 * {@code limitPrice}; a STOP order requires a positive {@code stopPrice} (cross-field checks below).
 */
public record PlaceOrderRequest(
        @NotBlank String symbol,
        @NotNull Side side,
        @NotNull @Positive BigDecimal quantity,
        OrderType type,
        @Positive BigDecimal limitPrice,
        @Positive BigDecimal stopPrice) {

    public OrderType typeOrDefault() {
        return type == null ? OrderType.MARKET : type;
    }

    @AssertTrue(message = "limitPrice is required for LIMIT orders")
    public boolean isLimitPricePresentWhenRequired() {
        return typeOrDefault() != OrderType.LIMIT || limitPrice != null;
    }

    @AssertTrue(message = "stopPrice is required for STOP orders")
    public boolean isStopPricePresentWhenRequired() {
        return typeOrDefault() != OrderType.STOP || stopPrice != null;
    }
}
