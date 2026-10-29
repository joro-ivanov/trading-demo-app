package com.example.demo.portfolio.it;

import static com.example.demo.testing.support.auth.TestJwt.token;
import static com.example.demo.testing.support.fixtures.TestTimeouts.EVENT_TIMEOUT;
import static com.example.demo.testing.support.fixtures.TestUsers.TESTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.demo.portfolio.domain.FillEntity;
import com.example.demo.portfolio.domain.FillRepository;
import com.example.demo.portfolio.domain.PositionRepository;
import com.example.demo.portfolio.it.harness.PortfolioEvents;
import com.example.demo.portfolio.it.harness.PortfolioITBase;
import com.example.demo.portfolio.web.PositionResponse;

/**
 * The {@code fills} consumer ({@code PortfolioListeners}): position arithmetic (weighted average on
 * BUY; SELL keeps the average, may go short — there is no short-sell guard here, that lives upstream
 * in order-service), the recent-fills store, closed-position cleanup and cash settlement.
 */
class FillsListenerIT extends PortfolioITBase {

    // The long every average-price scenario starts from: 10 @ 190.
    private static final String LONG_QUANTITY = "10";
    private static final String LONG_AVG_PRICE = "190";

    // Weighted average across two BUYs: (10*190 + 5*205) / 15 = 2925 / 15 = 195.0000
    private static final String WEIGHTED_SYMBOL = "TSLA";
    private static final String SECOND_BUY_QUANTITY = "5";
    private static final String SECOND_BUY_PRICE = "205";
    private static final String WEIGHTED_QUANTITY = "15";
    private static final String WEIGHTED_AVG_PRICE = "195";

    // Sell to flat at a wild price, then rebuy: the average resets to the rebuy's price.
    private static final String REBUY_OWNER = "rebuy-owner";
    private static final String REBUY_SYMBOL = "REBY";
    private static final String FLATTENING_SELL_PRICE = "999";
    private static final String REBUY_QUANTITY = "5";
    private static final String REBUY_PRICE = "205";

    // The recent-fills store.
    private static final String STORED_OWNER = "fills-db";
    private static final String STORED_SYMBOL = "NVDA";
    private static final String STORED_QUANTITY = "3";
    private static final String STORED_PRICE = "125";
    private static final Instant STORED_FILLED_AT = Instant.parse("2026-07-01T10:20:30Z");
    private static final long STORED_SEQUENCE = 1L;

    // Closing a position removes it.
    private static final String CLOSING_OWNER = "closed-db";
    private static final String CLOSING_SYMBOL = "CLOS";
    private static final String CLOSING_QUANTITY = "4";
    private static final String OPEN_PRICE = "100";
    private static final String CLOSE_PRICE = "101";
    private static final Instant OPENED_AT = Instant.parse("2026-07-01T10:21:30Z");
    private static final Instant CLOSED_AT = Instant.parse("2026-07-01T10:22:30Z");
    private static final long CLOSING_SEQUENCE = 1L;

    // Cash settlement starts from this deposit.
    private static final String STARTING_CASH = "1000";

    @Autowired
    PositionRepository positionRepository;

    @Autowired
    FillRepository fillRepository;

    // A SELL never recomputes the average: reducing a long, overselling into a short (no guard), and
    // selling through zero at a wild price all keep the long's 190 average.
    @ParameterizedTest(name = "{0}: long 10 @ 190, SELL {1} @ {2} -> qty {3}, avg 190")
    @CsvSource({
        "MSFT,4,999,6",
        "GOOG,15,190,-5",
        "FLIP,15,50,-5"
    })
    void sellKeepsTheAveragePrice(String symbol, String sellQuantity, String sellPrice, BigDecimal expectedQuantity) {
        EVENTS.sendFill(TESTER, symbol, BUY, LONG_QUANTITY, LONG_AVG_PRICE);
        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                assertThat(position(symbol).quantity()).isEqualByComparingTo(LONG_QUANTITY));

        EVENTS.sendFill(TESTER, symbol, SELL, sellQuantity, sellPrice);

        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> {
            PositionResponse position = position(symbol);
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo(expectedQuantity);
                softly.assertThat(position.avgPrice()).isEqualByComparingTo(LONG_AVG_PRICE);
            });
        });
    }

    @Test
    void weightedAveragePriceAcrossBuysWithRounding() {
        EVENTS.sendFill(TESTER, WEIGHTED_SYMBOL, BUY, LONG_QUANTITY, LONG_AVG_PRICE);
        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                assertThat(position(WEIGHTED_SYMBOL).quantity()).isEqualByComparingTo(LONG_QUANTITY));

        EVENTS.sendFill(TESTER, WEIGHTED_SYMBOL, BUY, SECOND_BUY_QUANTITY, SECOND_BUY_PRICE);

        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> {
            PositionResponse position = position(WEIGHTED_SYMBOL);
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo(WEIGHTED_QUANTITY);
                softly.assertThat(position.avgPrice()).isEqualByComparingTo(WEIGHTED_AVG_PRICE);
            });
        });
    }

    // Selling to flat then buying again resets the average to the new buy (the rebuy weighted-averages
    // against quantity zero, so the stale 190 is weighted out).
    @Test
    void sellToFlatThenRebuyResetsTheAverage() {
        // A dedicated owner, so the book holds exactly this test's positions.
        String token = token(REBUY_OWNER);
        EVENTS.sendFill(REBUY_OWNER, REBUY_SYMBOL, BUY, LONG_QUANTITY, LONG_AVG_PRICE);
        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                assertThat(position(token, REBUY_SYMBOL).quantity()).isEqualByComparingTo(LONG_QUANTITY));

        EVENTS.sendFill(REBUY_OWNER, REBUY_SYMBOL, SELL, LONG_QUANTITY, FLATTENING_SELL_PRICE);
        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                assertThat(positions(token)).isEmpty());

        EVENTS.sendFill(REBUY_OWNER, REBUY_SYMBOL, BUY, REBUY_QUANTITY, REBUY_PRICE);

        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> {
            PositionResponse position = position(token, REBUY_SYMBOL);
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo(REBUY_QUANTITY);
                softly.assertThat(position.avgPrice()).isEqualByComparingTo(REBUY_PRICE);
            });
        });
    }

    @Test
    void fillsAreStoredInTheDatabase() {
        EVENTS.sendFill(STORED_OWNER, STORED_SYMBOL, BUY, STORED_QUANTITY, STORED_PRICE,
                STORED_FILLED_AT.toString(), STORED_SEQUENCE);

        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> {
            FillEntity fill = fillRepository.findById(PortfolioEvents.orderId(STORED_SYMBOL, STORED_OWNER, BUY))
                    .orElseThrow(() -> new AssertionError("recent fill was not persisted"));
            assertSoftly(softly -> {
                softly.assertThat(fill.getOwner()).isEqualTo(STORED_OWNER);
                softly.assertThat(fill.getSymbol()).isEqualTo(STORED_SYMBOL);
                softly.assertThat(fill.getSide()).isEqualTo(BUY);
                softly.assertThat(fill.getQuantity()).isEqualByComparingTo(STORED_QUANTITY);
                softly.assertThat(fill.getPrice()).isEqualByComparingTo(STORED_PRICE);
                softly.assertThat(fill.getFilledAt()).isEqualTo(STORED_FILLED_AT);
                softly.assertThat(fill.getSequenceId()).isEqualTo(STORED_SEQUENCE);
            });
        });
    }

    @Test
    void closedPositionsAreRemovedFromTheDatabaseAndNotReturned() {
        String token = token(CLOSING_OWNER);
        EVENTS.sendFill(CLOSING_OWNER, CLOSING_SYMBOL, BUY, CLOSING_QUANTITY, OPEN_PRICE,
                OPENED_AT.toString(), CLOSING_SEQUENCE);
        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> {
            PositionResponse response = position(token, CLOSING_SYMBOL);
            assertSoftly(softly -> {
                softly.assertThat(response.quantity()).isEqualByComparingTo(CLOSING_QUANTITY);
                softly.assertThat(positionRepository.findByOwnerAndSymbol(CLOSING_OWNER, CLOSING_SYMBOL))
                        .isPresent()
                        .get()
                        .extracting(position -> position.getQuantity().stripTrailingZeros())
                        .isEqualTo(new BigDecimal(CLOSING_QUANTITY).stripTrailingZeros());
            });
        });

        EVENTS.sendFill(CLOSING_OWNER, CLOSING_SYMBOL, SELL, CLOSING_QUANTITY, CLOSE_PRICE,
                CLOSED_AT.toString(), CLOSING_SEQUENCE);

        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> {
            List<PositionResponse> positions = positions(token);
            assertSoftly(softly -> {
                softly.assertThat(positions).isEmpty();
                softly.assertThat(positionRepository.findByOwnerAndSymbol(CLOSING_OWNER, CLOSING_SYMBOL)).isEmpty();
            });
        });
    }

    // A BUY fill spends quantity*price of cash; a SELL fill adds it.
    @ParameterizedTest(name = "{2} {3} @ {4} on 1000 cash -> {5}")
    @CsvSource({
        "fillbuy,FBUY,BUY,2,100,800",
        "fillsell,FSELL,SELL,3,50,1150"
    })
    void aFillSettlesCash(String owner, String symbol, String side, String quantity, String price,
                          BigDecimal expectedCash) {
        String token = token(owner);
        deposit(token, STARTING_CASH);

        EVENTS.sendFill(owner, symbol, side, quantity, price);

        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                assertThat(balanceCash(token)).isEqualByComparingTo(expectedCash));
    }
}
