package com.example.demo.order.it;

import static com.example.demo.testing.support.auth.TestJwt.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.demo.common.json.Json;
import com.example.demo.order.domain.Side;
import com.example.demo.order.it.harness.OrderITBase;
import com.example.demo.order.it.harness.Orders;
import com.example.demo.order.web.OrderResponse;

/** {@code GET /orders/history}: the caller's own orders only. */
class OrderHistoryIT extends OrderITBase {

    private static final String SINGLE_OWNER = "history-single";
    private static final String ALICE_OWNER = "history-alice";
    private static final String BOB_OWNER = "history-bob";
    private static final String ALICE_SYMBOL = "NFLX";
    private static final String BOB_SYMBOL = "AMZN";
    // The single-order history test uses 2, so the response scale (2) differs from the DB scale (2.0000).
    private static final String SINGLE_QUANTITY = "2";
    private static final String ALICE_QUANTITY = "1";
    private static final String BOB_QUANTITY = "3";

    @Test
    void orderHistoryIncludesAPlacedOrder() {
        String token = token(SINGLE_OWNER);
        OrderResponse placed = place(token, Orders.market(ALICE_SYMBOL, Side.BUY.name(), SINGLE_QUANTITY));

        String body = api(token).get(HISTORY_PATH)
                .then().statusCode(HttpStatus.OK.value())
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
        String alice = token(ALICE_OWNER);
        String bob = token(BOB_OWNER);
        OrderResponse aliceOrder = place(alice, Orders.market(ALICE_SYMBOL, Side.BUY.name(), ALICE_QUANTITY));
        OrderResponse bobOrder = place(bob, Orders.market(BOB_SYMBOL, Side.BUY.name(), BOB_QUANTITY));

        List<OrderResponse> aliceHistory = Json.readList(
                api(alice).get(HISTORY_PATH).then().statusCode(HttpStatus.OK.value()).extract().asString(), OrderResponse.class);
        List<OrderResponse> bobHistory = Json.readList(
                api(bob).get(HISTORY_PATH).then().statusCode(HttpStatus.OK.value()).extract().asString(), OrderResponse.class);

        assertSoftly(softly -> {
            softly.assertThat(aliceHistory).extracting(OrderResponse::id, OrderResponse::owner)
                    .containsExactly(tuple(aliceOrder.id(), ALICE_OWNER));
            softly.assertThat(bobHistory).extracting(OrderResponse::id, OrderResponse::owner)
                    .containsExactly(tuple(bobOrder.id(), BOB_OWNER));
        });
    }
}
