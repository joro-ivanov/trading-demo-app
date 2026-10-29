package com.example.demo.e2e.harness;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.example.demo.common.time.Clocks;
import com.example.demo.common.trading.MarketHours;
import com.example.demo.e2e.framework.config.Config;

/**
 * Enables a test only when the market is currently open (the same {@link MarketHours} the
 * order-service gate uses), so order-placing e2e tests don't fail against a closed market. When
 * closed the test is disabled with a descriptive reason that Allure records as the skip message.
 *
 * <p>CI/verification escape hatch: set {@code market.always-open=true} (via {@code -D} or env) to
 * force the gate open — pair it with the server gate off ({@code TRADING_MARKET_HOURS_ENABLED=false})
 * to run order e2e regardless of wall-clock time.
 */
public class MarketHoursCondition implements ExecutionCondition {

    private static final MarketHours HOURS = MarketHours.defaults();
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm z");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (Boolean.parseBoolean(Config.get().get("market.always-open", "false"))) {
            return ConditionEvaluationResult.enabled("Market-hours gate overridden (market.always-open=true)");
        }
        Instant now = effectiveNow();
        if (HOURS.isOpen(now)) {
            return ConditionEvaluationResult.enabled("Market is open (" + formatCurrentTimes(now) + ")");
        }
        return ConditionEvaluationResult.disabled("Skipped — market closed at " + formatCurrentTimes(now)
                + " (regular session " + formatLocalSessionWindow(now) + ", Mon–Fri)");
    }

    private static Instant effectiveNow() {
        // The same app.clock.fixed-instant the services read (common Clocks), so gate and server agree.
        return Clocks.systemUtcOrFixed(Config.get().get("app.clock.fixed-instant")).instant();
    }

    private static String formatCurrentTimes(Instant now) {
        ZonedDateTime localNow = now.atZone(ZoneId.systemDefault());
        ZonedDateTime marketNow = now.atZone(HOURS.zone());
        return "local time: " + DATE_TIME.format(localNow) + "; market time: " + DATE_TIME.format(marketNow);
    }

    private static String formatLocalSessionWindow(Instant now) {
        ZonedDateTime marketNow = now.atZone(HOURS.zone());
        ZonedDateTime marketOpen = marketNow.with(HOURS.open());
        ZonedDateTime marketClose = marketNow.with(HOURS.close());
        ZonedDateTime localOpen = marketOpen.withZoneSameInstant(ZoneId.systemDefault());
        ZonedDateTime localClose = marketClose.withZoneSameInstant(ZoneId.systemDefault());
        return TIME.format(marketOpen) + "–" + TIME.format(marketClose)
                + " / local " + TIME.format(localOpen) + "–" + TIME.format(localClose);
    }
}
