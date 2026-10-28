package com.example.demo.portfolio.it;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.portfolio.it.harness.PortfolioITBase;
import com.example.demo.portfolio.web.FillResponse;

/** {@code GET /portfolio/fills}: the caller's recent fills, newest first. */
class FillsIT extends PortfolioITBase {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Test
    void recentFillsArePersistedAndReturnedNewestFirst() {
        String token = token("fills-view");
        KAFKA_PROBE.sendFill("fills-view", "AAPL", "BUY", "2", "100", "2026-07-01T10:15:30Z", 1L);
        KAFKA_PROBE.sendFill("fills-view", "MSFT", "SELL", "1", "200", "2026-07-01T10:16:30Z", 2L);

        await().atMost(WAIT).untilAsserted(() -> {
            List<FillResponse> fills = recentFills(token);
            assertSoftly(softly -> {
                softly.assertThat(fills).hasSize(2);
                softly.assertThat(fills.get(0).orderId()).isEqualTo("o-MSFT-fills-view-SELL");
                softly.assertThat(fills.get(0).filledAt()).isEqualTo(Instant.parse("2026-07-01T10:16:30Z"));
                softly.assertThat(fills.get(0).sequenceId()).isEqualTo(2L);
                softly.assertThat(fills.get(1).orderId()).isEqualTo("o-AAPL-fills-view-BUY");
                softly.assertThat(fills.get(1).filledAt()).isEqualTo(Instant.parse("2026-07-01T10:15:30Z"));
                softly.assertThat(fills.get(1).sequenceId()).isEqualTo(1L);
            });
        });
    }

    @Test
    void recentFillsWithTheSameTimestampAreReturnedInInsertionOrderDescending() {
        String token = token("fills-tie");
        KAFKA_PROBE.sendFill("fills-tie", "AAPL", "BUY", "1", "100", "2026-07-01T10:15:30Z", 1L);
        KAFKA_PROBE.sendFill("fills-tie", "AAPL", "SELL", "1", "101", "2026-07-01T10:15:30Z", 2L);

        await().atMost(WAIT).untilAsserted(() -> {
            List<FillResponse> fills = recentFills(token);
            assertSoftly(softly -> {
                softly.assertThat(fills).hasSize(2);
                softly.assertThat(fills.get(0).orderId()).isEqualTo("o-AAPL-fills-tie-SELL");
                softly.assertThat(fills.get(0).filledAt()).isEqualTo(Instant.parse("2026-07-01T10:15:30Z"));
                softly.assertThat(fills.get(0).sequenceId()).isEqualTo(2L);
                softly.assertThat(fills.get(1).orderId()).isEqualTo("o-AAPL-fills-tie-BUY");
                softly.assertThat(fills.get(1).filledAt()).isEqualTo(Instant.parse("2026-07-01T10:15:30Z"));
                softly.assertThat(fills.get(1).sequenceId()).isEqualTo(1L);
            });
        });
    }
}
