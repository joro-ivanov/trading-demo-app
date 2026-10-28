package com.example.demo.portfolio.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.example.demo.common.json.Json;
import com.example.demo.portfolio.it.harness.PortfolioITBase;
import com.example.demo.portfolio.web.HoldingResponse;

/** {@code GET /portfolio/holdings/{symbol}}: the held quantity order-service's short-sell guard reads. */
class HoldingsIT extends PortfolioITBase {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Test
    void holdingsEndpointReportsHeldQuantity() {
        KAFKA_PROBE.sendFill(TESTER, "HOLD", "BUY", "8", "190");
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(position("HOLD").quantity()).isEqualByComparingTo("8"));

        String body = api(TOKEN)
                .get("/portfolio/holdings/HOLD")
                .then().statusCode(200)
                .extract().asString();

        HoldingResponse holding = Json.read(body, HoldingResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(holding.symbol()).isEqualTo("HOLD");
            softly.assertThat(holding.quantity()).isEqualByComparingTo("8");
        });
    }

    @Test
    void holdingsEndpointReturnsZeroForUnknownSymbol() {
        String body = api(TOKEN)
                .get("/portfolio/holdings/NOPE")
                .then().statusCode(200)
                .extract().asString();

        HoldingResponse holding = Json.read(body, HoldingResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(holding.symbol()).isEqualTo("NOPE");
            softly.assertThat(holding.quantity()).isEqualByComparingTo("0");
        });
    }
}
