package com.example.demo.order.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.demo.order.domain.OrderStatus;
import com.example.demo.order.domain.Side;
import com.example.demo.order.it.harness.OrderITBase;
import com.example.demo.order.it.harness.OrderRequest;
import com.example.demo.order.it.harness.Orders;
import com.example.demo.order.it.harness.OrderMessages;

/**
 * {@code OrderOutcomeConsumer}: the matcher's {@code fills} and {@code orders.resting} events resolve an
 * order's status in the DB; terminal states are never overwritten by late events.
 */
class OrderOutcomeConsumerIT extends OrderITBase {

    private static final String SYMBOL = "AAPL";
    private static final String QUANTITY = "1";
    private static final String FILL_PRICE = "190.40";
    /** Far below any market price, so the matcher rests the order (PENDING). */
    private static final String NON_MARKETABLE_LIMIT = "0.01";

    private static final OrderRequest BUY_ORDER = Orders.market(SYMBOL, Side.BUY.name(), QUANTITY);

    @Test
    void orderBecomesFilledWhenAFillArrives() {
        String id = place(TESTER, BUY_ORDER).id();
        assertThat(statusOf(id)).isEqualTo(OrderStatus.SUBMITTED);

        KAFKA_PROBE.send(OrderMessages.FILLS, id,
                OrderMessages.fill(id, SYMBOL, Side.BUY.name(), QUANTITY, FILL_PRICE, TESTER_NAME));

        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.FILLED));
    }

    // A resting event (the matcher found it non-marketable) resolves the order to PENDING.
    @Test
    void orderBecomesPendingWhenARestingEventArrives() {
        String id = place(TESTER, Orders.limit(SYMBOL, Side.BUY.name(), QUANTITY, NON_MARKETABLE_LIMIT)).id();
        assertThat(statusOf(id)).isEqualTo(OrderStatus.SUBMITTED);

        KAFKA_PROBE.send(OrderMessages.RESTING, id, OrderMessages.resting(id));

        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.PENDING));
    }

    // Guard: a late fill for a CANCELLED order must not flip it back to FILLED (M18 invariant).
    @Test
    void fillForACancelledOrderLeavesItCancelled() {
        String id = place(TESTER, BUY_ORDER).id();
        api(TESTER).delete(ORDER_PATH, id).then().statusCode(HttpStatus.OK.value());

        KAFKA_PROBE.send(OrderMessages.FILLS, id,
                OrderMessages.fill(id, SYMBOL, Side.BUY.name(), QUANTITY, FILL_PRICE, TESTER_NAME));

        // Holds for the whole window, i.e. the consumer had time to (wrongly) apply the fill.
        await().during(STAYS_UNCHANGED_FOR).atMost(STAYS_UNCHANGED_TIMEOUT).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.CANCELLED));
    }

    // Guard: a resting event for an already-FILLED order is ignored (only SUBMITTED → PENDING).
    @Test
    void restingEventForAFilledOrderLeavesItFilled() {
        String id = place(TESTER, BUY_ORDER).id();
        KAFKA_PROBE.send(OrderMessages.FILLS, id,
                OrderMessages.fill(id, SYMBOL, Side.BUY.name(), QUANTITY, FILL_PRICE, TESTER_NAME));
        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.FILLED));

        KAFKA_PROBE.send(OrderMessages.RESTING, id, OrderMessages.resting(id));

        await().during(STAYS_UNCHANGED_FOR).atMost(STAYS_UNCHANGED_TIMEOUT).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.FILLED));
    }
}
