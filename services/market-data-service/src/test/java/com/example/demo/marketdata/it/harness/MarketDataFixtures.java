package com.example.demo.marketdata.it.harness;

import java.math.BigDecimal;

import com.example.demo.marketdata.match.OrderSubmittedEvent;

/**
 * Named values and event factories shared by the market-data-service ITs: the {@code orders.submitted}
 * events order-service publishes and the matcher's {@code orders.resting} event, as records.
 */
public final class MarketDataFixtures {

    /** The symbol the WireMock exchange feed prices (see {@code ExchangeFeedStubs}). */
    public static final String FEED_SYMBOL = "AAPL";
    /** A symbol the feed never prices, so the cache has nothing for it. */
    public static final String UNCACHED_SYMBOL = "ZZZZ";

    public static final String ORDERS_SUBMITTED = "orders.submitted";

    public static final String BUY = "BUY";
    /** The status order-service publishes every new order with. */
    public static final String SUBMITTED = "SUBMITTED";
    /** JWT subject when the caller's identity does not matter. */
    public static final String TEST_SUBJECT = "tester";

    private static final String PRICES = "/prices/";

    private MarketDataFixtures() {
    }

    public static String pricePath(String symbol) {
        return PRICES + symbol;
    }

    /** A MARKET order as published on {@code orders.submitted} (no type, no prices, no owner). */
    public static OrderSubmittedEvent submittedOrder(String id, String symbol, String side, BigDecimal quantity) {
        return new OrderSubmittedEvent(id, symbol, side, quantity, null, null, null, SUBMITTED, null);
    }

    /** A LIMIT/STOP order; the price the type does not use is {@code null}. */
    public static OrderSubmittedEvent submittedTypedOrder(String id, String symbol, String side, BigDecimal quantity,
                                                          String type, BigDecimal limitPrice, BigDecimal stopPrice) {
        return new OrderSubmittedEvent(id, symbol, side, quantity, type, limitPrice, stopPrice, SUBMITTED, null);
    }

    /** The matcher's {@code orders.resting} event for {@code orderId}. */
    public static OrderRestingEvent restingEvent(String orderId) {
        return new OrderRestingEvent(orderId);
    }
}
