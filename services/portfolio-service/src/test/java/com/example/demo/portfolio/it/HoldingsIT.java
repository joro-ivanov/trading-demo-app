package com.example.demo.portfolio.it;

import static com.example.demo.testing.support.fixtures.TestTimeouts.EVENT_TIMEOUT;
import static com.example.demo.testing.support.fixtures.TestUsers.TESTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.demo.portfolio.it.harness.PortfolioITBase;
import com.example.demo.portfolio.web.HoldingResponse;

/** {@code GET /portfolio/holdings/{symbol}}: the held quantity order-service's short-sell guard reads. */
class HoldingsIT extends PortfolioITBase {

    private static final String HELD_SYMBOL = "HOLD";
    private static final String HELD_QUANTITY = "8";
    private static final String FILL_PRICE = "190";
    private static final String UNKNOWN_SYMBOL = "NOPE";

    @Test
    void holdingsEndpointReportsHeldQuantity() {
        EVENTS.sendFill(TESTER, HELD_SYMBOL, BUY, HELD_QUANTITY, FILL_PRICE);
        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                assertThat(position(HELD_SYMBOL).quantity()).isEqualByComparingTo(HELD_QUANTITY));

        HoldingResponse holding = api(TOKEN)
                .get(HOLDINGS_PATH, HELD_SYMBOL)
                .then().statusCode(HttpStatus.OK.value())
                .extract().as(HoldingResponse.class);

        assertSoftly(softly -> {
            softly.assertThat(holding.symbol()).isEqualTo(HELD_SYMBOL);
            softly.assertThat(holding.quantity()).isEqualByComparingTo(HELD_QUANTITY);
        });
    }

    @Test
    void holdingsEndpointReturnsZeroForUnknownSymbol() {
        HoldingResponse holding = api(TOKEN)
                .get(HOLDINGS_PATH, UNKNOWN_SYMBOL)
                .then().statusCode(HttpStatus.OK.value())
                .extract().as(HoldingResponse.class);

        assertSoftly(softly -> {
            softly.assertThat(holding.symbol()).isEqualTo(UNKNOWN_SYMBOL);
            softly.assertThat(holding.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }
}
