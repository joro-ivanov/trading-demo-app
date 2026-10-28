package com.example.demo.order.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.example.demo.order.domain.OrderStatus;
import com.example.demo.order.it.harness.OrderITBase;
import com.example.demo.order.it.harness.OrderMessages;

/**
 * {@code OrderOutcomeConsumer}: the matcher's {@code fills} and {@code orders.resting} events resolve an
 * order's status in the DB; terminal states are never overwritten by late events.
 */
class OrderOutcomeConsumerIT extends OrderITBase {

    @Test
    void orderBecomesFilledWhenAFillArrives() {
        String id = place(TESTER, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}").id();
        assertThat(statusOf(id)).isEqualTo(OrderStatus.SUBMITTED);

        KAFKA_PROBE.send(OrderMessages.FILLS, id, OrderMessages.fill(id, "AAPL", "BUY", "1", "190.40", "tester"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.FILLED));
    }

    // A resting event (the matcher found it non-marketable) resolves the order to PENDING.
    @Test
    void orderBecomesPendingWhenARestingEventArrives() {
        String id = place(TESTER, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,"
                + "\"type\":\"LIMIT\",\"limitPrice\":0.01}").id();
        assertThat(statusOf(id)).isEqualTo(OrderStatus.SUBMITTED);

        KAFKA_PROBE.send(OrderMessages.RESTING, id, OrderMessages.resting(id));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.PENDING));
    }

    // Guard: a late fill for a CANCELLED order must not flip it back to FILLED (M18 invariant).
    @Test
    void fillForACancelledOrderLeavesItCancelled() {
        String id = place(TESTER, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}").id();
        api(TESTER).delete("/orders/{id}", id).then().statusCode(200);

        KAFKA_PROBE.send(OrderMessages.FILLS, id, OrderMessages.fill(id, "AAPL", "BUY", "1", "190.40", "tester"));

        // Holds for the whole window, i.e. the consumer had time to (wrongly) apply the fill.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.CANCELLED));
    }

    // Guard: a resting event for an already-FILLED order is ignored (only SUBMITTED → PENDING).
    @Test
    void restingEventForAFilledOrderLeavesItFilled() {
        String id = place(TESTER, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}").id();
        KAFKA_PROBE.send(OrderMessages.FILLS, id, OrderMessages.fill(id, "AAPL", "BUY", "1", "190.40", "tester"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.FILLED));

        KAFKA_PROBE.send(OrderMessages.RESTING, id, OrderMessages.resting(id));

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.FILLED));
    }
}
