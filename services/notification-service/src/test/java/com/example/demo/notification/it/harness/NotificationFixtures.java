package com.example.demo.notification.it.harness;

import java.math.BigDecimal;

/** Named values shared by the notification-service ITs: Kafka topics, STOMP subjects and event factories. */
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

    /** A {@code price.ticks} message for {@code symbol} at {@code price}. */
    public static PriceTick priceTick(String symbol, BigDecimal price) {
        return new PriceTick(symbol, price);
    }
}
