package com.example.demo.portfolio.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;

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

    // The long every P&L scenario starts from: 10 @ 190.
    private static final String LONG_QUANTITY = "10";
    private static final String AVG_PRICE = "190";

    // Fill then mark: (195 - 190) * 10 = 50.
    private static final String MARKED_SYMBOL = "AAPL";
    private static final String MARK_PRICE = "195";
    private static final String MARKED_PNL = "50";

    // Overselling the long by 15 at the same price leaves a short of -5 with the 190 average.
    private static final String OVERSELL_QUANTITY = "15";
    private static final String SHORT_QUANTITY = "-5";

    // One tick marks every owner of the symbol.
    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String SHARED_SYMBOL = "MULTI";
    private static final String SHARED_FILL_QUANTITY = "10";
    private static final String SHARED_FILL_PRICE = "100";
    private static final String SHARED_MARK = "150";

    @Test
    void buildsPositionAndPnlFromFillThenPrice() {
        KAFKA_PROBE.sendFill(TESTER, MARKED_SYMBOL, BUY, LONG_QUANTITY, AVG_PRICE);
        await().atMost(WAIT).untilAsserted(() -> {
            PositionResponse position = position(MARKED_SYMBOL);
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo(LONG_QUANTITY);
                softly.assertThat(position.avgPrice()).isEqualByComparingTo(AVG_PRICE);
            });
        });

        KAFKA_PROBE.sendTick(MARKED_SYMBOL, MARK_PRICE);

        await().atMost(WAIT).untilAsserted(() -> {
            PositionResponse position = position(MARKED_SYMBOL);
            assertSoftly(softly -> {
                softly.assertThat(position.marketPrice()).isEqualByComparingTo(MARK_PRICE);
                softly.assertThat(position.unrealizedPnl()).isEqualByComparingTo(MARKED_PNL);
            });
        });
    }

    @ParameterizedTest(name = "{0}: mark {1} on avg 190 -> P&L {2}")
    @CsvSource({"NFLX,195,50", "AMZN,180,-100", "META,190,0"})
    void unrealizedPnlReflectsMarkVersusAverage(String symbol, BigDecimal mark, BigDecimal expectedPnl) {
        KAFKA_PROBE.sendFill(TESTER, symbol, BUY, LONG_QUANTITY, AVG_PRICE);
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(symbol).quantity()).isEqualByComparingTo(LONG_QUANTITY));

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
        KAFKA_PROBE.sendFill(TESTER, symbol, BUY, LONG_QUANTITY, AVG_PRICE);
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(symbol).quantity()).isEqualByComparingTo(LONG_QUANTITY));
        KAFKA_PROBE.sendFill(TESTER, symbol, SELL, OVERSELL_QUANTITY, AVG_PRICE);
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(symbol).quantity()).isEqualByComparingTo(SHORT_QUANTITY));

        KAFKA_PROBE.sendTick(symbol, mark.toPlainString());

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(symbol).unrealizedPnl()).isEqualByComparingTo(expectedPnl));
    }

    // A single price tick marks the symbol for EVERY owner holding it (the per-user fan-out).
    @Test
    void priceTickMarksAllOwnersOfSymbol() {
        String aliceToken = token(ALICE);
        String bobToken = token(BOB);
        KAFKA_PROBE.sendFill(ALICE, SHARED_SYMBOL, BUY, SHARED_FILL_QUANTITY, SHARED_FILL_PRICE);
        KAFKA_PROBE.sendFill(BOB, SHARED_SYMBOL, BUY, SHARED_FILL_QUANTITY, SHARED_FILL_PRICE);
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(aliceToken, SHARED_SYMBOL).quantity()).isEqualByComparingTo(SHARED_FILL_QUANTITY));
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(bobToken, SHARED_SYMBOL).quantity()).isEqualByComparingTo(SHARED_FILL_QUANTITY));

        KAFKA_PROBE.sendTick(SHARED_SYMBOL, SHARED_MARK);

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(aliceToken, SHARED_SYMBOL).marketPrice()).isEqualByComparingTo(SHARED_MARK));
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(bobToken, SHARED_SYMBOL).marketPrice()).isEqualByComparingTo(SHARED_MARK));
    }
}
