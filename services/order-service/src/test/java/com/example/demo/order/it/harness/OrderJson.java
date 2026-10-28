package com.example.demo.order.it.harness;

/**
 * JSON bodies for {@code POST /orders} and the {@code /risk/check} request order-service sends, built
 * from named values. Numbers are passed as text so a test controls the exact representation (scale)
 * that goes over the wire.
 */
public final class OrderJson {

    private OrderJson() {
    }

    /** A MARKET order (type omitted, as a client would send it). */
    public static String market(String symbol, String side, String quantity) {
        return "{\"symbol\":\"" + symbol + "\",\"side\":\"" + side + "\",\"quantity\":" + quantity + "}";
    }

    /** An order with an explicit {@code type} and no price fields. */
    public static String typed(String symbol, String side, String quantity, String type) {
        return withField(market(symbol, side, quantity), "\"type\":\"" + type + "\"");
    }

    public static String limit(String symbol, String side, String quantity, String limitPrice) {
        return withField(typed(symbol, side, quantity, "LIMIT"), "\"limitPrice\":" + limitPrice);
    }

    public static String stop(String symbol, String side, String quantity, String stopPrice) {
        return withField(typed(symbol, side, quantity, "STOP"), "\"stopPrice\":" + stopPrice);
    }

    /** A MARKET order that also claims an {@code owner} in the body (which the service must ignore). */
    public static String claimingOwner(String symbol, String side, String quantity, String owner) {
        return withField(market(symbol, side, quantity), "\"owner\":\"" + owner + "\"");
    }

    /** The body order-service posts to risk-service's {@code /risk/check}. */
    public static String riskCheck(String symbol, String side, String quantity, String price) {
        return withField("{\"symbol\":\"" + symbol + "\",\"side\":\"" + side + "\",\"quantity\":" + quantity + "}",
                "\"price\":" + price);
    }

    private static String withField(String json, String field) {
        return json.substring(0, json.length() - 1) + "," + field + "}";
    }
}
