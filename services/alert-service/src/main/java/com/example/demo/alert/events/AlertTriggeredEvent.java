package com.example.demo.alert.events;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.demo.alert.domain.AlertEntity;

/** Payload of {@code alerts.triggered}; notification-service forwards it verbatim to the owner. */
public record AlertTriggeredEvent(String alertId, String owner, String symbol, String condition,
                                  BigDecimal threshold, BigDecimal price, Instant triggeredAt) {

    public static AlertTriggeredEvent from(AlertEntity alert) {
        return new AlertTriggeredEvent(alert.getId().toString(), alert.getOwner(), alert.getSymbol(),
                alert.getCondition().name(), alert.getThreshold(), alert.getTriggeredPrice(),
                alert.getTriggeredAt());
    }
}
