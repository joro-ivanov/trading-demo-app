package com.example.demo.marketdata.it;

import static com.example.demo.marketdata.it.harness.MarketDataFixtures.FEED_SYMBOL;
import static com.example.demo.marketdata.it.harness.MarketDataFixtures.pricePath;
import static com.example.demo.testing.support.auth.TestJwt.token;
import static com.example.demo.testing.support.fixtures.TestUsers.TESTER;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.demo.common.json.Json;
import com.example.demo.marketdata.it.harness.MarketDataITBase;
import com.example.demo.marketdata.it.harness.stubs.ExchangeFeedStubs;
import com.example.demo.marketdata.web.PriceResponse;

/** {@code GET /prices/{symbol}}: the latest feed price, served from the Redis cache. */
class PriceIT extends MarketDataITBase {

    private static final String UNKNOWN_SYMBOL = "NOPE";

    @Test
    void servesTheLatestFeedPrice() {
        awaitCachedPrice(FEED_SYMBOL, ExchangeFeedStubs.AAPL_LAST_PRICE);

        String body = api(token(TESTER))
                .get(pricePath(FEED_SYMBOL))
                .then().statusCode(HttpStatus.OK.value())
                .extract().asString();

        PriceResponse response = Json.read(body, PriceResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.symbol()).isEqualTo(FEED_SYMBOL);
            softly.assertThat(response.price()).isEqualByComparingTo(ExchangeFeedStubs.AAPL_LAST_PRICE);
        });
    }

    @Test
    void unknownSymbolPriceReturns404() {
        api(token(TESTER))
                .get(pricePath(UNKNOWN_SYMBOL))
                .then().statusCode(HttpStatus.NOT_FOUND.value());
    }
}
