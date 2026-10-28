package com.example.demo.e2e.framework.http;

import static com.example.demo.e2e.framework.support.HttpStatusCodes.OK;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.demo.e2e.framework.model.Price;
import com.example.demo.e2e.framework.model.TradingSymbols;

class ApiResponseTest {

    private static final String AAPL_PRICE = "190.12";
    private static final String SINGLE_PRICE_JSON =
            "{\"symbol\":\"" + TradingSymbols.AAPL + "\",\"price\":" + AAPL_PRICE + "}";
    private static final String SECOND_PRICE = "2.50";
    private static final String PRICE_LIST_JSON = "[{\"symbol\":\"" + TradingSymbols.AAPL + "\",\"price\":1.50},"
            + "{\"symbol\":\"" + TradingSymbols.MSFT + "\",\"price\":" + SECOND_PRICE + "}]";
    private static final int PRICE_COUNT = 2;
    private static final long RESPONSE_TIME_MS = 5;

    @Test
    void asDeserializesSingleObject() {
        ApiResponse response = new ApiResponse(OK, SINGLE_PRICE_JSON, Map.of(), Map.of(), RESPONSE_TIME_MS);

        Price price = response.as(Price.class);

        assertSoftly(softly -> {
            softly.assertThat(price.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(price.price()).isEqualByComparingTo(AAPL_PRICE);
        });
    }

    @Test
    void asListDeserializesArray() {
        ApiResponse response = new ApiResponse(OK, PRICE_LIST_JSON, Map.of(), Map.of(), RESPONSE_TIME_MS);

        List<Price> prices = response.asList(Price.class);

        assertSoftly(softly -> {
            softly.assertThat(prices).hasSize(PRICE_COUNT);
            softly.assertThat(prices.get(0).symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(prices.get(1).price()).isEqualByComparingTo(SECOND_PRICE);
        });
    }
}
