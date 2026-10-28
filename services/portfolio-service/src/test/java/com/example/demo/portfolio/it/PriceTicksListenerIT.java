package com.example.demo.portfolio.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.demo.portfolio.it.harness.PortfolioITBase;
import com.example.demo.portfolio.web.PositionResponse;

/**
 * The {@code price.ticks} consumer ({@code PortfolioListeners}): marks positions to market and derives
 * unrealized P&L = (mark - avg) * qty, for long and short books and for every owner of the symbol.
 */
class PriceTicksListenerIT extends PortfolioITBase {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Test
    void buildsPositionAndPnlFromFillThenPrice() {
        KAFKA_PROBE.sendFill(TESTER, "AAPL", "BUY", "10", "190");
        await().atMost(WAIT).untilAsserted(() -> {
            PositionResponse position = position("AAPL");
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo("10");
                softly.assertThat(position.avgPrice()).isEqualByComparingTo("190");
            });
        });

        KAFKA_PROBE.sendTick("AAPL", "195");

        await().atMost(WAIT).untilAsserted(() -> {
            PositionResponse position = position("AAPL");
            assertSoftly(softly -> {
                softly.assertThat(position.marketPrice()).isEqualByComparingTo("195");
                softly.assertThat(position.unrealizedPnl()).isEqualByComparingTo("50");
            });
        });
    }

    @ParameterizedTest(name = "{0}: mark {1} on avg 190 -> P&L {2}")
    @CsvSource({"NFLX,195,50", "AMZN,180,-100", "META,190,0"})
    void unrealizedPnlReflectsMarkVersusAverage(String symbol, BigDecimal mark, BigDecimal expectedPnl) {
        KAFKA_PROBE.sendFill(TESTER, symbol, "BUY", "10", "190");
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(symbol).quantity()).isEqualByComparingTo("10"));

        KAFKA_PROBE.sendTick(symbol, mark.toPlainString());

        await().atMost(WAIT).untilAsserted(() -> {
            PositionResponse position = position(symbol);
            assertSoftly(softly -> {
                softly.assertThat(position.marketPrice()).isEqualByComparingTo(mark);
                softly.assertThat(position.unrealizedPnl()).isEqualByComparingTo(expectedPnl);
            });
        });
    }

    // A SHORT position's P&L is the mirror of a long: with avg 190 and qty -5, a mark ABOVE 190 is a
    // loss and a mark BELOW is a gain. This is the only path through (mark-avg)*qty with negative qty.
    @ParameterizedTest(name = "{0}: short avg 190, mark {1} -> P&L {2}")
    @CsvSource({"SHRTA,200,-50", "SHRTB,180,50", "SHRTC,190,0"})
    void shortPositionPnlReflectsMarkVersusAverage(String symbol, BigDecimal mark, BigDecimal expectedPnl) {
        KAFKA_PROBE.sendFill(TESTER, symbol, "BUY", "10", "190");
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(symbol).quantity()).isEqualByComparingTo("10"));
        KAFKA_PROBE.sendFill(TESTER, symbol, "SELL", "15", "190");
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(symbol).quantity()).isEqualByComparingTo("-5"));

        KAFKA_PROBE.sendTick(symbol, mark.toPlainString());

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(symbol).unrealizedPnl()).isEqualByComparingTo(expectedPnl));
    }

    // A single price tick marks the symbol for EVERY owner holding it (the per-user fan-out).
    @Test
    void priceTickMarksAllOwnersOfSymbol() {
        String aliceToken = token("alice");
        String bobToken = token("bob");
        KAFKA_PROBE.sendFill("alice", "MULTI", "BUY", "10", "100");
        KAFKA_PROBE.sendFill("bob", "MULTI", "BUY", "10", "100");
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(aliceToken, "MULTI").quantity()).isEqualByComparingTo("10"));
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(bobToken, "MULTI").quantity()).isEqualByComparingTo("10"));

        KAFKA_PROBE.sendTick("MULTI", "150");

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(aliceToken, "MULTI").marketPrice()).isEqualByComparingTo("150"));
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(bobToken, "MULTI").marketPrice()).isEqualByComparingTo("150"));
    }
}
