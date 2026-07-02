package com.example.demo.order.service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.demo.common.trading.MarketHours;

/**
 * Wraps the shared {@link MarketHours} with an on/off switch. When the gate is disabled
 * ({@code trading.market-hours.enabled=false}) the market is always open — used by the
 * cross-service flagship so it is deterministic regardless of wall-clock time.
 */
@Component
public class MarketHoursPolicy {

    private final boolean enabled;
    private final MarketHours hours;

    public MarketHoursPolicy(
            @Value("${trading.market-hours.enabled:true}") boolean enabled,
            @Value("${trading.market-hours.open:09:30}") String open,
            @Value("${trading.market-hours.close:16:00}") String close,
            @Value("${trading.market-hours.zone:America/New_York}") String zone) {
        this.enabled = enabled;
        this.hours = MarketHours.of(LocalTime.parse(open), LocalTime.parse(close), ZoneId.of(zone));
    }

    public boolean isOpen(Instant instant) {
        return !enabled || hours.isOpen(instant);
    }
}
