package com.example.demo.order.it.harness.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.github.tomakehurst.wiremock.client.MappingBuilder;

/**
 * market-data-service as seen by order-service's {@code MarketPriceClient}:
 * {@code GET /prices/{symbol}} → {@code PriceResponse(symbol, price)}, 404 when no price is cached.
 */
public final class MarketDataStubs {

    private MarketDataStubs() {
    }

    /** Default: no cached price for any symbol, so BUY tests that are not about funds stay focused. */
    public static MappingBuilder hasNoPrices() {
        return get(urlPathMatching("/prices/[^/]+")).willReturn(notFound());
    }

    public static MappingBuilder hasPrice(String symbol, String price) {
        return get(urlPathEqualTo("/prices/" + symbol))
                .willReturn(okJson("{\"symbol\":\"" + symbol + "\",\"price\":" + price + "}"));
    }
}
