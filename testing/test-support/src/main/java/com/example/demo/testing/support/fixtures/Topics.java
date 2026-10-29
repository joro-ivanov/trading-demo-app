package com.example.demo.testing.support.fixtures;

/** The Kafka topics between the services, as the tests produce to and read from them. */
public final class Topics {

    /** Matcher fills (market-data → order, portfolio, notification). */
    public static final String FILLS = "fills";
    /** Exchange price ticks (market-data → portfolio, notification, alert). */
    public static final String PRICE_TICKS = "price.ticks";
    /** Orders accepted by order-service (order → market-data). */
    public static final String ORDERS_SUBMITTED = "orders.submitted";
    /** Orders resting on the book (market-data → order). */
    public static final String ORDERS_RESTING = "orders.resting";

    private Topics() {
    }
}
