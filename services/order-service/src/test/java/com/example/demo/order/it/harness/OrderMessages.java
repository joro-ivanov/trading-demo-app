package com.example.demo.order.it.harness;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.demo.testing.support.contracts.FillEvent;
import com.example.demo.testing.support.contracts.OrderRestingEvent;

/** The inbound Kafka messages order-service consumes ({@code fills}, {@code orders.resting}), as market-data publishes them. */
public final class OrderMessages {

    private static final Instant FILLED_AT = Instant.parse("2024-06-12T18:00:01Z");
    private static final long SEQUENCE_ID = 1;

    private OrderMessages() {
    }

    public static FillEvent fill(String orderId, String symbol, String side, String quantity, String price,
                                 String owner) {
        return new FillEvent(orderId, symbol, side, new BigDecimal(quantity), new BigDecimal(price), owner,
                FILLED_AT, SEQUENCE_ID);
    }

    public static OrderRestingEvent resting(String orderId) {
        return new OrderRestingEvent(orderId);
    }
}
