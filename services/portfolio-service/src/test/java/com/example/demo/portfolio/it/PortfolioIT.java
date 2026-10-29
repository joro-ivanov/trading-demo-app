package com.example.demo.portfolio.it;

import static com.example.demo.testing.support.auth.TestJwt.token;
import static com.example.demo.testing.support.fixtures.TestTimeouts.EVENT_TIMEOUT;
import static com.example.demo.testing.support.fixtures.TestUsers.ALICE;
import static com.example.demo.testing.support.fixtures.TestUsers.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;

import com.example.demo.portfolio.it.harness.PortfolioITBase;

/** {@code GET /portfolio}: the caller's own positions. */
class PortfolioIT extends PortfolioITBase {

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

        EVENTS.sendFill(ALICE, SHARED_SYMBOL, BUY, ALICE_QUANTITY, FILL_PRICE);
        EVENTS.sendFill(BOB, SHARED_SYMBOL, BUY, BOB_QUANTITY, FILL_PRICE);

        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                assertThat(position(aliceToken, SHARED_SYMBOL).quantity()).isEqualByComparingTo(ALICE_QUANTITY));
        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                assertThat(position(bobToken, SHARED_SYMBOL).quantity()).isEqualByComparingTo(BOB_QUANTITY));
    }
}
