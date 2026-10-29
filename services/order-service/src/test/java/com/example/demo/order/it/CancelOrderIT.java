package com.example.demo.order.it;

import static com.example.demo.testing.support.fixtures.TestTimeouts.EVENT_TIMEOUT;
import static com.example.demo.testing.support.fixtures.TestUsers.TESTER;
import static com.example.demo.testing.support.fixtures.Topics.FILLS;
import static com.example.demo.testing.support.fixtures.Topics.ORDERS_RESTING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.demo.common.json.Json;
import com.example.demo.order.domain.OrderStatus;
import com.example.demo.order.domain.Side;
import com.example.demo.order.it.harness.ApiError;
import com.example.demo.order.it.harness.OrderITBase;
import com.example.demo.order.it.harness.OrderRequest;
import com.example.demo.order.it.harness.Orders;
import com.example.demo.order.it.harness.OrderMessages;
import com.example.demo.order.web.OrderResponse;

/** {@code DELETE /orders/{id}}: state transitions, idempotency, ownership and unknown ids. */
class CancelOrderIT extends OrderITBase {

    private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000404";
    private static final String SYMBOL = "AAPL";
    private static final String QUANTITY = "1";
    private static final String FILL_PRICE = "190.40";
    /** Far below any market price, so the matcher rests the order (PENDING). */
    private static final String NON_MARKETABLE_LIMIT = "0.01";
    private static final String ORDER_NOT_FOUND = "Order not found: ";
    private static final String FILLED_CANNOT_BE_CANCELLED = "Filled orders cannot be cancelled: ";

    private static final OrderRequest BUY_ORDER = Orders.market(SYMBOL, Side.BUY.name(), QUANTITY);

    @Test
    void cancelLiveOrderTransitionsToCancelled() {
        String id = place(TESTER_TOKEN, BUY_ORDER).id();

        String body = api(TESTER_TOKEN).delete(ORDER_PATH, id)
                .then().statusCode(HttpStatus.OK.value())
                .extract().asString();

        OrderResponse response = Json.read(body, OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.id()).isEqualTo(id);
            softly.assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED.name());
            softly.assertThat(statusOf(id)).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    @Test
    void cancelIsIdempotentOnAnAlreadyCancelledOrder() {
        String id = place(TESTER_TOKEN, BUY_ORDER).id();
        api(TESTER_TOKEN).delete(ORDER_PATH, id).then().statusCode(HttpStatus.OK.value());

        String body = api(TESTER_TOKEN).delete(ORDER_PATH, id)
                .then().statusCode(HttpStatus.OK.value())
                .extract().asString();

        assertSoftly(softly -> {
            softly.assertThat(Json.read(body, OrderResponse.class).status()).isEqualTo(OrderStatus.CANCELLED.name());
            softly.assertThat(statusOf(id)).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    @Test
    void cancelUnknownOrderReturns404() {
        String body = api(TESTER_TOKEN).delete(ORDER_PATH, UNKNOWN_ID)
                .then().statusCode(HttpStatus.NOT_FOUND.value())
                .extract().asString();

        assertThat(Json.read(body, ApiError.class).message()).isEqualTo(ORDER_NOT_FOUND + UNKNOWN_ID);
    }

    // Bob cannot cancel Alice's order — 404 (same as unknown, so existence is not leaked); Alice still can.
    @Test
    void cannotCancelAnotherUsersOrder() {
        String aliceOrderId = place(ALICE_TOKEN, BUY_ORDER).id();

        api(BOB_TOKEN).delete(ORDER_PATH, aliceOrderId).then().statusCode(HttpStatus.NOT_FOUND.value());
        assertThat(statusOf(aliceOrderId)).isEqualTo(OrderStatus.SUBMITTED);

        String body = api(ALICE_TOKEN).delete(ORDER_PATH, aliceOrderId)
                .then().statusCode(HttpStatus.OK.value())
                .extract().asString();
        assertThat(Json.read(body, OrderResponse.class).status()).isEqualTo(OrderStatus.CANCELLED.name());
    }

    @Test
    void aPendingOrderCanBeCancelled() {
        String id = place(TESTER_TOKEN, Orders.limit(SYMBOL, Side.BUY.name(), QUANTITY, NON_MARKETABLE_LIMIT)).id();
        KAFKA_PROBE.send(ORDERS_RESTING, id, OrderMessages.resting(id));
        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.PENDING));

        String body = api(TESTER_TOKEN).delete(ORDER_PATH, id)
                .then().statusCode(HttpStatus.OK.value())
                .extract().asString();

        assertSoftly(softly -> {
            softly.assertThat(Json.read(body, OrderResponse.class).status()).isEqualTo(OrderStatus.CANCELLED.name());
            softly.assertThat(statusOf(id)).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    // Filled orders are terminal: cancelling after execution must not rewrite history.
    @Test
    void cancellingAFilledOrderIsRejected() {
        String id = place(TESTER_TOKEN, BUY_ORDER).id();
        KAFKA_PROBE.send(FILLS, id,
                OrderMessages.fill(id, SYMBOL, Side.BUY.name(), QUANTITY, FILL_PRICE, TESTER));
        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.FILLED));

        String body = api(TESTER_TOKEN).delete(ORDER_PATH, id)
                .then().statusCode(HttpStatus.CONFLICT.value())
                .extract().asString();

        assertSoftly(softly -> {
            softly.assertThat(Json.read(body, ApiError.class).message())
                    .isEqualTo(FILLED_CANNOT_BE_CANCELLED + id);
            softly.assertThat(statusOf(id)).isEqualTo(OrderStatus.FILLED);
        });
    }
}
