package com.example.demo.portfolio.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.demo.portfolio.domain.FillEntity;
import com.example.demo.portfolio.domain.FillRepository;
import com.example.demo.portfolio.domain.PositionRepository;
import com.example.demo.portfolio.it.harness.KafkaProbe;
import com.example.demo.portfolio.it.harness.PortfolioITBase;
import com.example.demo.portfolio.web.PositionResponse;

/**
 * The {@code fills} consumer ({@code PortfolioListeners}): position arithmetic (weighted average on
 * BUY; SELL keeps the average, may go short — there is no short-sell guard here, that lives upstream
 * in order-service), the recent-fills store, closed-position cleanup and cash settlement.
 */
class FillsListenerIT extends PortfolioITBase {

    private static final Duration WAIT = Duration.ofSeconds(20);

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
        KAFKA_PROBE.sendFill(TESTER, symbol, "BUY", "10", "190");
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(symbol).quantity()).isEqualByComparingTo("10"));

        KAFKA_PROBE.sendFill(TESTER, symbol, "SELL", sellQuantity, sellPrice);

        await().atMost(WAIT).untilAsserted(() -> {
            PositionResponse position = position(symbol);
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo(expectedQuantity);
                softly.assertThat(position.avgPrice()).isEqualByComparingTo("190");
            });
        });
    }

    @Test
    void weightedAveragePriceAcrossBuysWithRounding() {
        KAFKA_PROBE.sendFill(TESTER, "TSLA", "BUY", "10", "190");
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position("TSLA").quantity()).isEqualByComparingTo("10"));

        // (10*190 + 5*205) / 15 = 2925 / 15 = 195.0000
        KAFKA_PROBE.sendFill(TESTER, "TSLA", "BUY", "5", "205");

        await().atMost(WAIT).untilAsserted(() -> {
            PositionResponse position = position("TSLA");
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo("15");
                softly.assertThat(position.avgPrice()).isEqualByComparingTo("195");
            });
        });
    }

    // Selling to flat then buying again resets the average to the new buy (the rebuy weighted-averages
    // against quantity zero, so the stale 190 is weighted out).
    @Test
    void sellToFlatThenRebuyResetsTheAverage() {
        // A dedicated owner, so the book holds exactly this test's positions.
        String token = token("rebuy-owner");
        KAFKA_PROBE.sendFill("rebuy-owner", "REBY", "BUY", "10", "190");
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(token, "REBY").quantity()).isEqualByComparingTo("10"));

        KAFKA_PROBE.sendFill("rebuy-owner", "REBY", "SELL", "10", "999");
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(positions(token)).isEmpty());

        KAFKA_PROBE.sendFill("rebuy-owner", "REBY", "BUY", "5", "205");

        await().atMost(WAIT).untilAsserted(() -> {
            PositionResponse position = position(token, "REBY");
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo("5");
                softly.assertThat(position.avgPrice()).isEqualByComparingTo("205");
            });
        });
    }

    @Test
    void fillsAreStoredInTheDatabase() {
        KAFKA_PROBE.sendFill("fills-db", "NVDA", "BUY", "3", "125", "2026-07-01T10:20:30Z", 1L);

        await().atMost(WAIT).untilAsserted(() -> {
            FillEntity fill = fillRepository.findById(KafkaProbe.orderId("NVDA", "fills-db", "BUY"))
                    .orElseThrow(() -> new AssertionError("recent fill was not persisted"));
            assertSoftly(softly -> {
                softly.assertThat(fill.getOwner()).isEqualTo("fills-db");
                softly.assertThat(fill.getSymbol()).isEqualTo("NVDA");
                softly.assertThat(fill.getSide()).isEqualTo("BUY");
                softly.assertThat(fill.getQuantity()).isEqualByComparingTo("3");
                softly.assertThat(fill.getPrice()).isEqualByComparingTo("125");
                softly.assertThat(fill.getFilledAt()).isEqualTo(Instant.parse("2026-07-01T10:20:30Z"));
                softly.assertThat(fill.getSequenceId()).isEqualTo(1L);
            });
        });
    }

    @Test
    void closedPositionsAreRemovedFromTheDatabaseAndNotReturned() {
        String token = token("closed-db");
        KAFKA_PROBE.sendFill("closed-db", "CLOS", "BUY", "4", "100", "2026-07-01T10:21:30Z", 1L);
        await().atMost(WAIT).untilAsserted(() -> {
            PositionResponse response = position(token, "CLOS");
            assertSoftly(softly -> {
                softly.assertThat(response.quantity()).isEqualByComparingTo("4");
                softly.assertThat(positionRepository.findByOwnerAndSymbol("closed-db", "CLOS"))
                        .isPresent()
                        .get()
                        .extracting(position -> position.getQuantity().stripTrailingZeros())
                        .isEqualTo(new BigDecimal("4").stripTrailingZeros());
            });
        });

        KAFKA_PROBE.sendFill("closed-db", "CLOS", "SELL", "4", "101", "2026-07-01T10:22:30Z", 1L);

        await().atMost(WAIT).untilAsserted(() -> {
            List<PositionResponse> positions = positions(token);
            assertSoftly(softly -> {
                softly.assertThat(positions).isEmpty();
                softly.assertThat(positionRepository.findByOwnerAndSymbol("closed-db", "CLOS")).isEmpty();
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
        deposit(token, "1000");

        KAFKA_PROBE.sendFill(owner, symbol, side, quantity, price);

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(balanceCash(token)).isEqualByComparingTo(expectedCash));
    }
}
