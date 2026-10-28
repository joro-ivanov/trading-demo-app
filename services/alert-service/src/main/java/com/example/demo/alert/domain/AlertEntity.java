package com.example.demo.alert.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A user's price alert. Money values are held at the column scale (4) so the API response, the
 * persisted row and the {@code alerts.triggered} event always carry the same representation.
 */
@Entity
@Table(name = "alerts")
public class AlertEntity {

    public static final int SCALE = 4;

    @Id
    private UUID id;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertCondition condition;

    @Column(nullable = false)
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "triggered_price")
    private BigDecimal triggeredPrice;

    // Wrapper type: null marks a new entity, so save() persists instead of merging; also guards
    // against a concurrent second trigger of the same row (optimistic locking).
    @Version
    private Long version;

    protected AlertEntity() {
        // JPA
    }

    private AlertEntity(UUID id, String owner, String symbol, AlertCondition condition, BigDecimal threshold,
                        Instant createdAt) {
        this.id = id;
        this.owner = owner;
        this.symbol = symbol;
        this.condition = condition;
        this.threshold = threshold.setScale(SCALE, RoundingMode.UNNECESSARY);
        this.status = AlertStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    /** A new ACTIVE alert. {@code threshold} must already have at most {@value #SCALE} decimals. */
    public static AlertEntity create(String owner, String symbol, AlertCondition condition, BigDecimal threshold,
                                     Instant now) {
        return new AlertEntity(UUID.randomUUID(), owner, symbol, condition, threshold, now);
    }

    /**
     * Triggers this alert if it is still ACTIVE and {@code price} meets its condition.
     *
     * @return {@code true} only on the transition ACTIVE → TRIGGERED (so a trigger happens once)
     */
    public boolean triggerIfMet(BigDecimal price, Instant now) {
        if (status != AlertStatus.ACTIVE || !condition.isMetBy(price, threshold)) {
            return false;
        }
        status = AlertStatus.TRIGGERED;
        triggeredAt = now;
        triggeredPrice = price.setScale(SCALE, RoundingMode.HALF_EVEN);
        return true;
    }

    public boolean isTriggered() {
        return status == AlertStatus.TRIGGERED;
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

    public AlertCondition getCondition() {
        return condition;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public BigDecimal getTriggeredPrice() {
        return triggeredPrice;
    }
}
