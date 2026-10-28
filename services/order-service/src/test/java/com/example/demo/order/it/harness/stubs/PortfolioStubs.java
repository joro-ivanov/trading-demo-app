package com.example.demo.order.it.harness.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import java.math.BigDecimal;

import com.example.demo.common.json.Json;
import com.github.tomakehurst.wiremock.client.MappingBuilder;

/**
 * portfolio-service as seen by order-service's {@code PortfolioClient}: the short-sell guard's
 * {@code GET /portfolio/holdings/{symbol}} ({@code HoldingResponse(symbol, quantity)}) and the funds
 * guard's {@code GET /funds/balance} ({@code FundsDtos.BalanceResponse(cash)}).
 */
public final class PortfolioStubs {

    private static final String ANY_SYMBOL = "ANY";
    private static final BigDecimal PLENTY = new BigDecimal("1000");

    private PortfolioStubs() {
    }

    /** Default: plenty of every symbol, so SELL orders that are not about the guard pass it. */
    public static MappingBuilder holdsPlentyOfEverything() {
        return get(urlPathMatching("/portfolio/holdings/[^/]+"))
                .willReturn(okJson(Json.write(new HoldingResponse(ANY_SYMBOL, PLENTY))));
    }

    public static MappingBuilder holds(String symbol, String quantity) {
        return get(urlPathEqualTo("/portfolio/holdings/" + symbol))
                .willReturn(okJson(Json.write(new HoldingResponse(symbol, new BigDecimal(quantity)))));
    }

    public static MappingBuilder hasCash(String cash) {
        return get(urlPathEqualTo("/funds/balance"))
                .willReturn(okJson(Json.write(new BalanceResponse(new BigDecimal(cash)))));
    }
}
