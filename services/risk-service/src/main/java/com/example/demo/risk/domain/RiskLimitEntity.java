package com.example.demo.risk.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A user's own risk limits. One row per owner (the JWT subject); absent means "use the defaults". */
@Entity
@Table(name = "risk_limits")
public class RiskLimitEntity {

    @Id
    private String owner;

    @Column(name = "max_order_notional", nullable = false)
    private BigDecimal maxOrderNotional;

    @Column(name = "max_position_quantity", nullable = false)
    private BigDecimal maxPositionQuantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected RiskLimitEntity() {
        // for JPA
    }

    public RiskLimitEntity(String owner, BigDecimal maxOrderNotional, BigDecimal maxPositionQuantity,
                           Instant updatedAt) {
        this.owner = owner;
        this.maxOrderNotional = maxOrderNotional;
        this.maxPositionQuantity = maxPositionQuantity;
        this.updatedAt = updatedAt;
    }

    public void update(BigDecimal newMaxOrderNotional, BigDecimal newMaxPositionQuantity, Instant now) {
        this.maxOrderNotional = newMaxOrderNotional;
        this.maxPositionQuantity = newMaxPositionQuantity;
        this.updatedAt = now;
    }

    public String getOwner() {
        return owner;
    }

    public BigDecimal getMaxOrderNotional() {
        return maxOrderNotional;
    }

    public BigDecimal getMaxPositionQuantity() {
        return maxPositionQuantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
