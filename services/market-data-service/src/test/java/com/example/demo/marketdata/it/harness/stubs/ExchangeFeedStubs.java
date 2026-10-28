package com.example.demo.marketdata.it.harness.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.message;
import static com.github.tomakehurst.wiremock.client.WireMock.messageStubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.sendMessage;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Behaviour-named stubs for the exchange's raw WebSocket price feed (docs/testing-cookbook.md §2).
 * The service sends {@code subscribe:<symbol>} on connect; the stub replies on the same channel with
 * frames shaped like {@code com.example.demo.marketdata.model.PriceTick} ({@code symbol}, {@code price}).
 */
public final class ExchangeFeedStubs {

    /** First and last price of the AAPL burst; the cache ends on the last one. */
    public static final String AAPL_FIRST_PRICE = "190.10";
    public static final String AAPL_LAST_PRICE = "190.40";

    private ExchangeFeedStubs() {
    }

    /** {@code subscribe:AAPL} → two AAPL frames: 190.10 then 190.40. */
    public static void aaplPriceBurstOnSubscribe(WireMockServer feed) {
        configureFor("localhost", feed.port());
        messageStubFor(message()
                .withName("AAPL price burst")
                .withBody(equalTo("subscribe:AAPL"))
                .willTriggerActions(
                        sendMessage(tick("AAPL", AAPL_FIRST_PRICE)).onOriginatingChannel(),
                        sendMessage(tick("AAPL", AAPL_LAST_PRICE)).onOriginatingChannel()));
    }

    private static String tick(String symbol, String price) {
        return "{\"symbol\":\"" + symbol + "\",\"price\":" + price + "}";
    }
}
