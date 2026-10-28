package com.example.demo.marketdata.it;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;

import com.example.demo.common.json.Json;
import com.example.demo.marketdata.it.harness.MarketDataITBase;
import com.example.demo.marketdata.it.harness.stubs.ExchangeFeedStubs;
import com.example.demo.marketdata.web.PriceResponse;

/** {@code GET /prices/{symbol}}: the latest feed price, served from the Redis cache. */
class PriceIT extends MarketDataITBase {

    @Test
    void servesTheLatestFeedPrice() {
        awaitCachedPrice("AAPL", ExchangeFeedStubs.AAPL_LAST_PRICE);

        String body = api(validToken("tester"))
                .get("/prices/AAPL")
                .then().statusCode(200)
                .extract().asString();

        PriceResponse response = Json.read(body, PriceResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.symbol()).isEqualTo("AAPL");
            softly.assertThat(response.price()).isEqualByComparingTo(ExchangeFeedStubs.AAPL_LAST_PRICE);
        });
    }

    @Test
    void unknownSymbolPriceReturns404() {
        api(validToken("tester"))
                .get("/prices/NOPE")
                .then().statusCode(404);
    }
}
