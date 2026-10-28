package com.example.demo.order.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.example.demo.common.json.Json;
import com.example.demo.order.domain.OrderStatus;
import com.example.demo.order.it.harness.ApiError;
import com.example.demo.order.it.harness.OrderITBase;
import com.example.demo.order.it.harness.OrderMessages;
import com.example.demo.order.web.OrderResponse;

/** {@code DELETE /orders/{id}}: state transitions, idempotency, ownership and unknown ids. */
class CancelOrderIT extends OrderITBase {

    private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000404";

    @Test
    void cancelLiveOrderTransitionsToCancelled() {
        String id = place(TESTER, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}").id();

        String body = api(TESTER).delete("/orders/{id}", id)
                .then().statusCode(200)
                .extract().asString();

        OrderResponse response = Json.read(body, OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.id()).isEqualTo(id);
            softly.assertThat(response.status()).isEqualTo("CANCELLED");
            softly.assertThat(statusOf(id)).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    @Test
    void cancelIsIdempotentOnAnAlreadyCancelledOrder() {
        String id = place(TESTER, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}").id();
        api(TESTER).delete("/orders/{id}", id).then().statusCode(200);

        String body = api(TESTER).delete("/orders/{id}", id)
                .then().statusCode(200)
                .extract().asString();

        assertSoftly(softly -> {
            softly.assertThat(Json.read(body, OrderResponse.class).status()).isEqualTo("CANCELLED");
            softly.assertThat(statusOf(id)).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    @Test
    void cancelUnknownOrderReturns404() {
        String body = api(TESTER).delete("/orders/{id}", UNKNOWN_ID)
                .then().statusCode(404)
                .extract().asString();

        assertThat(Json.read(body, ApiError.class).message()).isEqualTo("Order not found: " + UNKNOWN_ID);
    }

    // Bob cannot cancel Alice's order — 404 (same as unknown, so existence is not leaked); Alice still can.
    @Test
    void cannotCancelAnotherUsersOrder() {
        String aliceOrderId = place(ALICE, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}").id();

        api(BOB).delete("/orders/{id}", aliceOrderId).then().statusCode(404);
        assertThat(statusOf(aliceOrderId)).isEqualTo(OrderStatus.SUBMITTED);

        String body = api(ALICE).delete("/orders/{id}", aliceOrderId)
                .then().statusCode(200)
                .extract().asString();
        assertThat(Json.read(body, OrderResponse.class).status()).isEqualTo("CANCELLED");
    }

    @Test
    void aPendingOrderCanBeCancelled() {
        String id = place(TESTER, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,"
                + "\"type\":\"LIMIT\",\"limitPrice\":0.01}").id();
        KAFKA_PROBE.send(OrderMessages.RESTING, id, OrderMessages.resting(id));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.PENDING));

        String body = api(TESTER).delete("/orders/{id}", id)
                .then().statusCode(200)
                .extract().asString();

        assertSoftly(softly -> {
            softly.assertThat(Json.read(body, OrderResponse.class).status()).isEqualTo("CANCELLED");
            softly.assertThat(statusOf(id)).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    // Filled orders are terminal: cancelling after execution must not rewrite history.
    @Test
    void cancellingAFilledOrderIsRejected() {
        String id = place(TESTER, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}").id();
        KAFKA_PROBE.send(OrderMessages.FILLS, id, OrderMessages.fill(id, "AAPL", "BUY", "1", "190.40", "tester"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.FILLED));

        String body = api(TESTER).delete("/orders/{id}", id)
                .then().statusCode(409)
                .extract().asString();

        assertSoftly(softly -> {
            softly.assertThat(Json.read(body, ApiError.class).message())
                    .isEqualTo("Filled orders cannot be cancelled: " + id);
            softly.assertThat(statusOf(id)).isEqualTo(OrderStatus.FILLED);
        });
    }
}
