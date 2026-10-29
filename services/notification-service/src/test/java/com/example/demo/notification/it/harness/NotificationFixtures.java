package com.example.demo.notification.it.harness;

import java.math.BigDecimal;

import com.example.demo.testing.support.contracts.PriceTick;

/** Named values shared by the notification-service ITs (topics and users come from test-support). */
public final class NotificationFixtures {

    /** A STOMP principal that must not receive another owner's fills. */
    public static final String OTHER_USER = "other";

    private NotificationFixtures() {
    }

    /** A {@code price.ticks} message for {@code symbol} at {@code price}. */
    public static PriceTick priceTick(String symbol, BigDecimal price) {
        return new PriceTick(symbol, price);
    }
}
