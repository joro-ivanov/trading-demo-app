package com.example.demo.alert.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.alert.client.MarketDataClient;
import com.example.demo.alert.config.AlertProperties;
import com.example.demo.alert.domain.AlertCondition;
import com.example.demo.alert.domain.AlertEntity;
import com.example.demo.alert.domain.AlertRepository;
import com.example.demo.alert.domain.AlertStatus;

/** Owner-scoped alert use cases. Another owner's alert is indistinguishable from a missing one (404). */
@Service
public class AlertService {

    private final AlertRepository repository;
    private final MarketDataClient marketData;
    private final AlertProperties properties;
    private final Clock clock;

    public AlertService(AlertRepository repository, MarketDataClient marketData, AlertProperties properties,
                        Clock clock) {
        this.repository = repository;
        this.marketData = marketData;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Creates an ACTIVE alert. Deliberately not one transaction: the remote symbol check must not
     * hold a database transaction open. The limit is checked first (cheap, local).
     */
    public AlertEntity create(String owner, String symbol, AlertCondition condition, BigDecimal threshold) {
        long active = repository.countByOwnerAndStatus(owner, AlertStatus.ACTIVE);
        if (active >= properties.maxActivePerOwner()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Active alert limit reached: " + properties.maxActivePerOwner());
        }
        marketData.requireKnownSymbol(owner, symbol);
        return repository.save(AlertEntity.create(owner, symbol, condition, threshold, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<AlertEntity> list(String owner, Optional<AlertStatus> status) {
        return status
                .map(wanted -> repository.findByOwnerAndStatusOrderByCreatedAtAscIdAsc(owner, wanted))
                .orElseGet(() -> repository.findByOwnerOrderByCreatedAtAscIdAsc(owner));
    }

    @Transactional(readOnly = true)
    public AlertEntity get(String owner, UUID id) {
        return find(owner, id);
    }

    @Transactional
    public void delete(String owner, UUID id) {
        AlertEntity alert = find(owner, id);
        if (alert.isTriggered()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Triggered alerts cannot be deleted: " + id);
        }
        repository.delete(alert);
    }

    private AlertEntity find(String owner, UUID id) {
        return repository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found: " + id));
    }
}
