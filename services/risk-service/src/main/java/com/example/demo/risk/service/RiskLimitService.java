package com.example.demo.risk.service;

import java.math.BigDecimal;
import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.risk.config.RiskProperties;
import com.example.demo.risk.config.RiskProperties.Limits;
import com.example.demo.risk.domain.RiskLimitEntity;
import com.example.demo.risk.domain.RiskLimitRepository;
import com.example.demo.risk.web.RiskDtos.LimitSource;
import com.example.demo.risk.web.RiskDtos.RiskLimitsResponse;
import com.example.demo.risk.web.RiskDtos.UpdateRiskLimitsRequest;

/** Reads and updates a user's risk limits, falling back to the configured defaults. */
@Service
public class RiskLimitService {

    private final RiskLimitRepository repository;
    private final RiskProperties properties;
    private final Clock clock;

    public RiskLimitService(RiskLimitRepository repository, RiskProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RiskLimitsResponse limitsFor(String owner) {
        return repository.findById(owner)
                .map(RiskLimitService::custom)
                .orElseGet(this::defaults);
    }

    @Transactional
    public RiskLimitsResponse update(String owner, UpdateRiskLimitsRequest request) {
        Limits maxAllowed = properties.maxAllowed();
        requireAtMost("maxOrderNotional", request.maxOrderNotional(), maxAllowed.maxOrderNotional());
        requireAtMost("maxPositionQuantity", request.maxPositionQuantity(), maxAllowed.maxPositionQuantity());

        RiskLimitEntity limits = repository.findById(owner)
                .orElseGet(() -> new RiskLimitEntity(owner, request.maxOrderNotional(),
                        request.maxPositionQuantity(), clock.instant()));
        limits.update(request.maxOrderNotional(), request.maxPositionQuantity(), clock.instant());
        return custom(repository.save(limits));
    }

    private RiskLimitsResponse defaults() {
        Limits defaults = properties.defaults();
        return new RiskLimitsResponse(defaults.maxOrderNotional(), defaults.maxPositionQuantity(),
                LimitSource.DEFAULT, null);
    }

    private static RiskLimitsResponse custom(RiskLimitEntity limits) {
        return new RiskLimitsResponse(limits.getMaxOrderNotional(), limits.getMaxPositionQuantity(),
                LimitSource.CUSTOM, limits.getUpdatedAt());
    }

    private static void requireAtMost(String field, BigDecimal value, BigDecimal max) {
        if (value.compareTo(max) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " must not exceed " + max.toPlainString());
        }
    }
}
