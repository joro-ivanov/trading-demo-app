package com.example.demo.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type;

    @Column(name = "limit_price")
    private BigDecimal limitPrice;

    @Column(name = "stop_price")
    private BigDecimal stopPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // The authenticated user who placed the order (the JWT subject). Set server-side, never from the
    // request body — so a caller cannot place an order "as" someone else.
    @Column(nullable = false)
    private String owner;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderEntity() {
        // for JPA
    }

    public OrderEntity(UUID id, String symbol, Side side, BigDecimal quantity, OrderType type,
                       BigDecimal limitPrice, BigDecimal stopPrice, String owner, OrderStatus status,
                       Instant createdAt) {
        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.type = type;
        this.limitPrice = limitPrice;
        this.stopPrice = stopPrice;
        this.owner = owner;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getSymbol() {
        return symbol;
    }

    public Side getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public OrderType getType() {
        return type;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public BigDecimal getStopPrice() {
        return stopPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
