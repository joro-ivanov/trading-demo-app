package com.example.demo.portfolio.domain;

import java.math.BigDecimal;

import com.example.demo.common.money.Money;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

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

    @Column(name = "market_price")
    private Money marketPrice;

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

    public void setMarketPrice(Money marketPrice) {
        this.marketPrice = marketPrice;
    }
}
