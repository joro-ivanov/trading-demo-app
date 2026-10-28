package com.example.demo.order.it.harness;

import java.math.BigDecimal;

/**
 * Inbound Kafka payloads exactly as the producers send them: {@code fills} mirrors
 * market-data-service's {@code match/FillEvent}, {@code orders.resting} its resting event.
 */
public final class OrderMessages {

    public static final String FILLS = "fills";
    public static final String RESTING = "orders.resting";

    private static final String FILLED_AT = "2024-06-12T18:00:01Z";
    private static final long SEQUENCE_ID = 1;

    private OrderMessages() {
    }

    public static FillMessage fill(String orderId, String symbol, String side, String quantity, String price,
                                   String owner) {
        return new FillMessage(orderId, symbol, side, new BigDecimal(quantity), new BigDecimal(price), owner,
                FILLED_AT, SEQUENCE_ID);
    }

    public static RestingMessage resting(String orderId) {
        return new RestingMessage(orderId);
    }
}
