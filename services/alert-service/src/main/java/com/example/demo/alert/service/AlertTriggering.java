package com.example.demo.alert.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.alert.domain.AlertEntity;
import com.example.demo.alert.domain.AlertRepository;
import com.example.demo.alert.domain.AlertStatus;
import com.example.demo.alert.events.AlertEventPublisher;

/** Applies a price tick: every ACTIVE alert on the symbol whose condition is met triggers once. */
@Service
public class AlertTriggering {

    private final AlertRepository repository;
    private final AlertEventPublisher publisher;
    private final Clock clock;

    public AlertTriggering(AlertRepository repository, AlertEventPublisher publisher, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Triggers matching alerts. Idempotent: an alert only fires on ACTIVE → TRIGGERED, and
     * {@code @Version} rejects a concurrent second trigger of the same row.
     */
    @Transactional
    public void onPrice(String symbol, BigDecimal price) {
        Instant now = clock.instant();
        for (AlertEntity alert : repository.findBySymbolAndStatus(symbol, AlertStatus.ACTIVE)) {
            if (alert.triggerIfMet(price, now)) {
                publisher.publishTriggered(alert);
            }
        }
    }
}
