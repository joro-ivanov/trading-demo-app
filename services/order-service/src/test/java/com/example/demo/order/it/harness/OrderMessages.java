package com.example.demo.order.it.harness;

/**
 * Inbound Kafka payloads exactly as the producers send them: {@code fills} mirrors
 * market-data-service's {@code match/FillEvent}, {@code orders.resting} its resting event.
 */
public final class OrderMessages {

    public static final String FILLS = "fills";
    public static final String RESTING = "orders.resting";

    private OrderMessages() {
    }

    public static String fill(String orderId, String symbol, String side, String quantity, String price, String owner) {
        return "{\"orderId\":\"" + orderId + "\",\"symbol\":\"" + symbol + "\",\"side\":\"" + side + "\","
                + "\"quantity\":" + quantity + ",\"price\":" + price + ",\"owner\":\"" + owner + "\","
                + "\"filledAt\":\"2024-06-12T18:00:01Z\",\"sequenceId\":1}";
    }

    public static String resting(String orderId) {
        return "{\"orderId\":\"" + orderId + "\"}";
    }
}
