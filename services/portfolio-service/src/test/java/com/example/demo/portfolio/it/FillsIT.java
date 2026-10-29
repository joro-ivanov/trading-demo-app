package com.example.demo.portfolio.it;

import static com.example.demo.testing.support.auth.TestJwt.token;
import static com.example.demo.testing.support.fixtures.TestTimeouts.EVENT_TIMEOUT;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.portfolio.it.harness.PortfolioITBase;
import com.example.demo.portfolio.web.FillResponse;

/** {@code GET /portfolio/fills}: the caller's recent fills, newest first. */
class FillsIT extends PortfolioITBase {

    private static final int FILLS_SENT = 2;
    private static final long FIRST_SEQUENCE = 1L;
    private static final long SECOND_SEQUENCE = 2L;
    private static final Instant EARLIER_FILL_TIME = Instant.parse("2026-07-01T10:15:30Z");
    private static final Instant LATER_FILL_TIME = Instant.parse("2026-07-01T10:16:30Z");

    // newest-first by timestamp
    private static final String VIEW_OWNER = "fills-view";
    private static final String AAPL = "AAPL";
    private static final String MSFT = "MSFT";
    private static final String AAPL_BUY_QUANTITY = "2";
    private static final String AAPL_BUY_PRICE = "100";
    private static final String MSFT_SELL_QUANTITY = "1";
    private static final String MSFT_SELL_PRICE = "200";
    private static final String VIEW_MSFT_SELL_ORDER_ID = "o-MSFT-fills-view-SELL";
    private static final String VIEW_AAPL_BUY_ORDER_ID = "o-AAPL-fills-view-BUY";

    // same timestamp: insertion order descending
    private static final String TIE_OWNER = "fills-tie";
    private static final String TIE_QUANTITY = "1";
    private static final String TIE_BUY_PRICE = "100";
    private static final String TIE_SELL_PRICE = "101";
    private static final String TIE_SELL_ORDER_ID = "o-AAPL-fills-tie-SELL";
    private static final String TIE_BUY_ORDER_ID = "o-AAPL-fills-tie-BUY";

    @Test
    void recentFillsArePersistedAndReturnedNewestFirst() {
        String token = token(VIEW_OWNER);
        EVENTS.sendFill(VIEW_OWNER, AAPL, BUY, AAPL_BUY_QUANTITY, AAPL_BUY_PRICE,
                EARLIER_FILL_TIME.toString(), FIRST_SEQUENCE);
        EVENTS.sendFill(VIEW_OWNER, MSFT, SELL, MSFT_SELL_QUANTITY, MSFT_SELL_PRICE,
                LATER_FILL_TIME.toString(), SECOND_SEQUENCE);

        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> {
            List<FillResponse> fills = recentFills(token);
            assertSoftly(softly -> {
                softly.assertThat(fills).hasSize(FILLS_SENT);
                softly.assertThat(fills.get(0).orderId()).isEqualTo(VIEW_MSFT_SELL_ORDER_ID);
                softly.assertThat(fills.get(0).filledAt()).isEqualTo(LATER_FILL_TIME);
                softly.assertThat(fills.get(0).sequenceId()).isEqualTo(SECOND_SEQUENCE);
                softly.assertThat(fills.get(1).orderId()).isEqualTo(VIEW_AAPL_BUY_ORDER_ID);
                softly.assertThat(fills.get(1).filledAt()).isEqualTo(EARLIER_FILL_TIME);
                softly.assertThat(fills.get(1).sequenceId()).isEqualTo(FIRST_SEQUENCE);
            });
        });
    }

    @Test
    void recentFillsWithTheSameTimestampAreReturnedInInsertionOrderDescending() {
        String token = token(TIE_OWNER);
        EVENTS.sendFill(TIE_OWNER, AAPL, BUY, TIE_QUANTITY, TIE_BUY_PRICE,
                EARLIER_FILL_TIME.toString(), FIRST_SEQUENCE);
        EVENTS.sendFill(TIE_OWNER, AAPL, SELL, TIE_QUANTITY, TIE_SELL_PRICE,
                EARLIER_FILL_TIME.toString(), SECOND_SEQUENCE);

        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> {
            List<FillResponse> fills = recentFills(token);
            assertSoftly(softly -> {
                softly.assertThat(fills).hasSize(FILLS_SENT);
                softly.assertThat(fills.get(0).orderId()).isEqualTo(TIE_SELL_ORDER_ID);
                softly.assertThat(fills.get(0).filledAt()).isEqualTo(EARLIER_FILL_TIME);
                softly.assertThat(fills.get(0).sequenceId()).isEqualTo(SECOND_SEQUENCE);
                softly.assertThat(fills.get(1).orderId()).isEqualTo(TIE_BUY_ORDER_ID);
                softly.assertThat(fills.get(1).filledAt()).isEqualTo(EARLIER_FILL_TIME);
                softly.assertThat(fills.get(1).sequenceId()).isEqualTo(FIRST_SEQUENCE);
            });
        });
    }
}
