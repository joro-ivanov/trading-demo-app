package com.example.demo.portfolio.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A persisted user fill shown in the dashboard's recent-fills panel. */
@Entity
@Table(name = "recent_fills")
public class FillEntity {

    @Id
    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String side;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "filled_at", nullable = false)
    private Instant filledAt;

    @Column(name = "sequence_id", nullable = false)
    private Long sequenceId;

    protected FillEntity() {
        // for JPA
    }

    public FillEntity(String orderId, String owner, String symbol, String side,
                      BigDecimal quantity, BigDecimal price, Instant filledAt, Long sequenceId) {
        this.orderId = orderId;
        this.owner = owner;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.filledAt = filledAt;
        this.sequenceId = sequenceId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getOwner() {
        return owner;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getFilledAt() {
        return filledAt;
    }

    public Long getSequenceId() {
        return sequenceId;
    }
}
