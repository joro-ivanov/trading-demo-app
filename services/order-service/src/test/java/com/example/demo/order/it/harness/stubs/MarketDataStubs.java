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

    /**
     * Default: every symbol has a cached price, so an order of any type can be valued (a MARKET order
     * with no price is refused) and tests not about pricing stay focused. order-service reads only
     * {@code price}, so the echoed symbol is a fixed placeholder.
     */
    public static MappingBuilder pricesEverySymbolAt(String price) {
        return get(urlPathMatching("/prices/[^/]+"))
                .willReturn(okJson("{\"symbol\":\"ANY\",\"price\":" + price + "}"));
    }

    /** market-data has no cached price for {@code symbol} (404). */
    public static MappingBuilder hasNoPriceFor(String symbol) {
        return get(urlPathEqualTo("/prices/" + symbol)).willReturn(notFound());
    }

    public static MappingBuilder hasPrice(String symbol, String price) {
        return get(urlPathEqualTo("/prices/" + symbol))
                .willReturn(okJson("{\"symbol\":\"" + symbol + "\",\"price\":" + price + "}"));
    }
}
