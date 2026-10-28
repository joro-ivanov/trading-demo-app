package com.example.demo.order.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Test;

import com.example.demo.common.json.Json;
import com.example.demo.order.it.harness.OrderITBase;
import com.example.demo.order.web.OrderResponse;

/** {@code GET /orders/history}: the caller's own orders only. */
class OrderHistoryIT extends OrderITBase {

    @Test
    void orderHistoryIncludesAPlacedOrder() {
        String token = tokenFor("history-single");
        OrderResponse placed = place(token, "{\"symbol\":\"NFLX\",\"side\":\"BUY\",\"quantity\":2}");

        String body = api(token).get("/orders/history")
                .then().statusCode(200)
                .extract().asString();

        // Compare amounts by value: POST echoes the request scale (2), history returns the DB scale (2.0000).
        assertThat(Json.readList(body, OrderResponse.class))
                .usingRecursiveFieldByFieldElementComparator(RecursiveComparisonConfiguration.builder()
                        .withComparatorForType(Comparator.nullsFirst(BigDecimal::compareTo), BigDecimal.class)
                        .build())
                .containsExactly(placed);
    }

    @Test
    void orderHistoryIsScopedToTheAuthenticatedUser() {
        String alice = tokenFor("history-alice");
        String bob = tokenFor("history-bob");
        OrderResponse aliceOrder = place(alice, "{\"symbol\":\"NFLX\",\"side\":\"BUY\",\"quantity\":1}");
        OrderResponse bobOrder = place(bob, "{\"symbol\":\"AMZN\",\"side\":\"BUY\",\"quantity\":3}");

        List<OrderResponse> aliceHistory = Json.readList(
                api(alice).get("/orders/history").then().statusCode(200).extract().asString(), OrderResponse.class);
        List<OrderResponse> bobHistory = Json.readList(
                api(bob).get("/orders/history").then().statusCode(200).extract().asString(), OrderResponse.class);

        assertSoftly(softly -> {
            softly.assertThat(aliceHistory).extracting(OrderResponse::id, OrderResponse::owner)
                    .containsExactly(tuple(aliceOrder.id(), "history-alice"));
            softly.assertThat(bobHistory).extracting(OrderResponse::id, OrderResponse::owner)
                    .containsExactly(tuple(bobOrder.id(), "history-bob"));
        });
    }
}
