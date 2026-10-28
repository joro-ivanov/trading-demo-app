package com.example.demo.notification.it.harness;

/** Named values shared by the notification-service ITs: Kafka topics, STOMP subjects and payload builders. */
public final class NotificationFixtures {

    public static final String PRICE_TICKS = "price.ticks";
    public static final String FILLS = "fills";

    /** JWT subjects (the STOMP principal names). */
    public static final String TESTER = "tester";
    public static final String OTHER_USER = "other";
    public static final String ALICE = "alice";
    public static final String BOB = "bob";

    private NotificationFixtures() {
    }

    /** A {@code price.ticks} payload exactly as market-data publishes it ({@code {"symbol":..,"price":..}}). */
    public static String priceTick(String symbol, String price) {
        return "{\"symbol\":\"" + symbol + "\",\"price\":" + price + "}";
    }
}
