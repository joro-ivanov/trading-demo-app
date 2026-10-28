package com.example.demo.portfolio.domain;

import java.math.BigDecimal;

import com.example.demo.common.money.Money;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A position is held by one {@code owner} for one {@code symbol} — the two together are the key. */
@Entity
@Table(name = "positions")
@IdClass(PositionId.class)
public class PositionEntity {

    @Id
    private String owner;

    @Id
    private String symbol;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "avg_price", nullable = false)
    private Money avgPrice;

    // Owned by the price-tick path (PositionRepository.markToMarket, a targeted UPDATE). updatable = false
    // keeps a fill's save of the whole row from writing back a market price it read before a newer tick.
    @Column(name = "market_price", updatable = false)
    private Money marketPrice;

    // Optimistic locking: a concurrent read-modify-write of the same position fails loudly
    // (ObjectOptimisticLockingFailureException) instead of silently losing the other update.
    @Version
    @Column(nullable = false)
    private long version;

    protected PositionEntity() {
        // for JPA
    }

    public PositionEntity(String owner, String symbol, BigDecimal quantity, Money avgPrice, Money marketPrice) {
        this.owner = owner;
        this.symbol = symbol;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
        this.marketPrice = marketPrice;
    }

    public String getOwner() {
        return owner;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Money getAvgPrice() {
        return avgPrice;
    }

    public Money getMarketPrice() {
        return marketPrice;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setAvgPrice(Money avgPrice) {
        this.avgPrice = avgPrice;
    }

    /** Unrealized P&L at the last mark; zero until the first price tick marks the position. */
    public Money unrealizedPnl() {
        return marketPrice == null ? Money.ZERO : marketPrice.subtract(avgPrice).multiply(quantity);
    }

    /** Value at the last mark, falling back to the average price for a position not yet marked. */
    public Money marketValue() {
        return (marketPrice == null ? avgPrice : marketPrice).multiply(quantity);
    }
}
