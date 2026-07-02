package com.example.demo.portfolio.web;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.demo.portfolio.domain.FillEntity;

/** A recent fill shown in the dashboard. */
public record FillResponse(String orderId, String symbol, String side, BigDecimal quantity,
                           BigDecimal price, Instant filledAt, Long sequenceId) {

    public static FillResponse from(FillEntity fill) {
        return new FillResponse(
                fill.getOrderId(),
                fill.getSymbol(),
                fill.getSide(),
                fill.getQuantity(),
                fill.getPrice(),
                fill.getFilledAt(),
                fill.getSequenceId());
    }
}
