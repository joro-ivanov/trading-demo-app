package com.example.demo.portfolio.web;

import java.math.BigDecimal;

import com.example.demo.portfolio.service.PortfolioSummaryService.PortfolioSummary;

/** {@code GET /portfolio/summary}: amounts at scale 4. */
public record PortfolioSummaryResponse(
        BigDecimal cash,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        int positions) {

    public static PortfolioSummaryResponse from(PortfolioSummary summary) {
        return new PortfolioSummaryResponse(
                summary.cash().amount(),
                summary.marketValue().amount(),
                summary.unrealizedPnl().amount(),
                summary.positions());
    }
}
