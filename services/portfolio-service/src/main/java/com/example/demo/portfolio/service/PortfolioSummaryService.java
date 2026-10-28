package com.example.demo.portfolio.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.money.Money;
import com.example.demo.portfolio.domain.PositionEntity;
import com.example.demo.portfolio.domain.PositionRepository;

/** Read model for {@code GET /portfolio/summary}: one owner's cash plus the aggregate of their positions. */
@Service
public class PortfolioSummaryService {

    private final PositionRepository positions;
    private final FundsService funds;

    public PortfolioSummaryService(PositionRepository positions, FundsService funds) {
        this.positions = positions;
        this.funds = funds;
    }

    /** Cash, market value and unrealized P&L read in one transaction, so the figures are consistent. */
    @Transactional(readOnly = true)
    public PortfolioSummary summarize(String owner) {
        List<PositionEntity> held = positions.findByOwner(owner);
        Money marketValue = held.stream().map(PositionEntity::marketValue).reduce(Money.ZERO, Money::add);
        Money unrealizedPnl = held.stream().map(PositionEntity::unrealizedPnl).reduce(Money.ZERO, Money::add);
        return new PortfolioSummary(funds.balance(owner), marketValue, unrealizedPnl, held.size());
    }

    /** Monetary figures at {@link Money#SCALE}; {@code positions} counts open positions (long and short). */
    public record PortfolioSummary(Money cash, Money marketValue, Money unrealizedPnl, int positions) {
    }
}
