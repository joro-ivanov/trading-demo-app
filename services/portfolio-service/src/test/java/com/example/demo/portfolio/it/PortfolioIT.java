package com.example.demo.portfolio.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;

import com.example.demo.portfolio.it.harness.PortfolioITBase;

/** {@code GET /portfolio}: the caller's own positions. */
class PortfolioIT extends PortfolioITBase {

    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String SHARED_SYMBOL = "ISOL";
    private static final String ALICE_QUANTITY = "5";
    private static final String BOB_QUANTITY = "9";
    private static final String FILL_PRICE = "100";

    // Per-user isolation: the same symbol held by two owners is two separate books (not one shared
    // total). If the portfolio were global, both would see 14.
    @Test
    void positionsAreScopedToTheOwner() {
        String aliceToken = token(ALICE);
        String bobToken = token(BOB);

        KAFKA_PROBE.sendFill(ALICE, SHARED_SYMBOL, BUY, ALICE_QUANTITY, FILL_PRICE);
        KAFKA_PROBE.sendFill(BOB, SHARED_SYMBOL, BUY, BOB_QUANTITY, FILL_PRICE);

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(aliceToken, SHARED_SYMBOL).quantity()).isEqualByComparingTo(ALICE_QUANTITY));
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(bobToken, SHARED_SYMBOL).quantity()).isEqualByComparingTo(BOB_QUANTITY));
    }
}
