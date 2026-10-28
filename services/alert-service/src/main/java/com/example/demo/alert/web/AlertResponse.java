package com.example.demo.alert.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.demo.alert.domain.AlertCondition;
import com.example.demo.alert.domain.AlertEntity;
import com.example.demo.alert.domain.AlertStatus;

public record AlertResponse(UUID id, String symbol, AlertCondition condition, BigDecimal threshold,
                            AlertStatus status, Instant createdAt, Instant triggeredAt,
                            BigDecimal triggeredPrice) {

    public static AlertResponse from(AlertEntity alert) {
        return new AlertResponse(alert.getId(), alert.getSymbol(), alert.getCondition(), alert.getThreshold(),
                alert.getStatus(), alert.getCreatedAt(), alert.getTriggeredAt(), alert.getTriggeredPrice());
    }
}
