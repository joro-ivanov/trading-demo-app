package com.example.demo.marketdata.it.harness;

/**
 * Named values and payload builders shared by the market-data-service ITs. The builders produce the
 * exact JSON order-service publishes on {@code orders.submitted} and the matcher on {@code orders.resting}.
 */
public final class MarketDataFixtures {

    /** The symbol the WireMock exchange feed prices (see {@code ExchangeFeedStubs}). */
    public static final String FEED_SYMBOL = "AAPL";
    /** A symbol the feed never prices, so the cache has nothing for it. */
    public static final String UNCACHED_SYMBOL = "ZZZZ";

    public static final String ORDERS_SUBMITTED = "orders.submitted";
    public static final String ORDER_ID_FIELD = "orderId";
    public static final String SYMBOL_FIELD = "symbol";

    public static final String BUY = "BUY";
    /** JWT subject when the caller's identity does not matter. */
    public static final String TEST_SUBJECT = "tester";

    private static final String PRICES = "/prices/";

    private MarketDataFixtures() {
    }

    public static String pricePath(String symbol) {
        return PRICES + symbol;
    }

    /** A MARKET order as published on {@code orders.submitted}. */
    public static String submittedOrder(String id, String symbol, String side, String quantity) {
        return "{\"id\":\"" + id + "\",\"symbol\":\"" + symbol + "\",\"side\":\"" + side
                + "\",\"quantity\":" + quantity + ",\"status\":\"SUBMITTED\"}";
    }

    /** A LIMIT/STOP order; {@code priceField} is {@code limitPrice} or {@code stopPrice}. */
    public static String submittedTypedOrder(String id, String symbol, String side, String quantity,
                                             String type, String priceField, String price) {
        return "{\"id\":\"" + id + "\",\"symbol\":\"" + symbol + "\",\"side\":\"" + side
                + "\",\"quantity\":" + quantity + ",\"type\":\"" + type + "\",\"" + priceField + "\":" + price
                + ",\"status\":\"SUBMITTED\"}";
    }

    /** The matcher's {@code orders.resting} event for {@code orderId}. */
    public static String restingEvent(String orderId) {
        return "{\"orderId\":\"" + orderId + "\"}";
    }
}
