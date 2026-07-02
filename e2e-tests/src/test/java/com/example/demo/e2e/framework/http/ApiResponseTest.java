package com.example.demo.e2e.framework.http;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.demo.e2e.framework.model.Price;

class ApiResponseTest {

    @Test
    void asDeserializesSingleObject() {
        ApiResponse response = new ApiResponse(
                200, "{\"symbol\":\"AAPL\",\"price\":190.12}", Map.of(), Map.of(), 5);

        Price price = response.as(Price.class);

        assertSoftly(softly -> {
            softly.assertThat(price.symbol()).isEqualTo("AAPL");
            softly.assertThat(price.price()).isEqualByComparingTo("190.12");
        });
    }

    @Test
    void asListDeserializesArray() {
        ApiResponse response = new ApiResponse(
                200,
                "[{\"symbol\":\"AAPL\",\"price\":1.50},{\"symbol\":\"MSFT\",\"price\":2.50}]",
                Map.of(), Map.of(), 5);

        List<Price> prices = response.asList(Price.class);

        assertSoftly(softly -> {
            softly.assertThat(prices).hasSize(2);
            softly.assertThat(prices.get(0).symbol()).isEqualTo("AAPL");
            softly.assertThat(prices.get(1).price()).isEqualByComparingTo("2.50");
        });
    }
}
