package com.example.demo.common.trading;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Regular trading session: a daily [open, close) window on a set of trading days, in a fixed zone.
 * Shared so the order-service gate and the e2e {@code @RunDuringMarketHours} condition agree on
 * "is the market open right now".
 */
public record MarketHours(LocalTime open, LocalTime close, ZoneId zone, Set<DayOfWeek> tradingDays) {

    private static final Set<DayOfWeek> WEEKDAYS = Set.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    /** NYSE-like regular session: 09:30–16:00 America/New_York, Monday–Friday. */
    public static MarketHours defaults() {
        return new MarketHours(LocalTime.of(9, 30), LocalTime.of(16, 0),
                ZoneId.of("America/New_York"), WEEKDAYS);
    }

    /** A Monday–Friday session with the given window and zone. */
    public static MarketHours of(LocalTime open, LocalTime close, ZoneId zone) {
        return new MarketHours(open, close, zone, WEEKDAYS);
    }

    public boolean isOpen(Instant instant) {
        ZonedDateTime local = instant.atZone(zone);
        LocalTime time = local.toLocalTime();
        return tradingDays.contains(local.getDayOfWeek())
                && !time.isBefore(open)
                && time.isBefore(close);
    }
}
