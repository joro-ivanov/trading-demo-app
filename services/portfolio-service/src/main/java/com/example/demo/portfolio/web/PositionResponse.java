package com.example.demo.portfolio.web;

import java.math.BigDecimal;

import com.example.demo.common.money.Money;
import com.example.demo.portfolio.domain.PositionEntity;

/** A portfolio position with computed unrealized P&L. Money is exposed as BigDecimal at the edge. */
public record PositionResponse(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal marketPrice,
        BigDecimal unrealizedPnl) {

    public static PositionResponse from(PositionEntity position) {
        Money market = position.getMarketPrice();
        return new PositionResponse(
                position.getSymbol(),
                position.getQuantity(),
                position.getAvgPrice().amount(),
                market == null ? null : market.amount(),
                position.unrealizedPnl().amount());
    }
}
