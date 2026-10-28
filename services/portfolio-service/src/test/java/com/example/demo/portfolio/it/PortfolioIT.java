package com.example.demo.portfolio.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.example.demo.portfolio.it.harness.PortfolioITBase;

/** {@code GET /portfolio}: the caller's own positions. */
class PortfolioIT extends PortfolioITBase {

    private static final Duration WAIT = Duration.ofSeconds(20);

    // Per-user isolation: the same symbol held by two owners is two separate books (not one shared
    // total). If the portfolio were global, both would see 14.
    @Test
    void positionsAreScopedToTheOwner() {
        String aliceToken = token("alice");
        String bobToken = token("bob");

        KAFKA_PROBE.sendFill("alice", "ISOL", "BUY", "5", "100");
        KAFKA_PROBE.sendFill("bob", "ISOL", "BUY", "9", "100");

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(aliceToken, "ISOL").quantity()).isEqualByComparingTo("5"));
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position(bobToken, "ISOL").quantity()).isEqualByComparingTo("9"));
    }
}
