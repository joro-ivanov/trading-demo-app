package com.example.demo.e2e.framework.support;

import java.math.BigDecimal;

/** Error messages the services return, shared by several suites (expected values in tests). */
public final class ApiErrorMessages {

    /** Any request body / parameter that fails validation (common ApiErrorHandler). */
    public static final String INVALID_REQUEST_CONTENT = "Invalid request content.";
    /** Wrong password or unknown user on login. */
    public static final String INVALID_CREDENTIALS = "Invalid credentials";
    /** Prefix of the 401 message when a bearer token cannot be decoded; the reason follows. */
    public static final String JWT_DECODE_ERROR = "An error occurred while attempting to decode the Jwt: ";

    private static final String INSUFFICIENT_FUNDS_TO_BUY = "Insufficient funds to buy ";
    private static final String SPACE = " ";
    private static final String COST = " (cost ";
    private static final String AVAILABLE = ", available ";
    private static final String CLOSING_PARENTHESIS = ")";

    private ApiErrorMessages() {
    }

    /** The 422 the order-service funds guard returns for an unaffordable BUY (cost = market price × quantity). */
    public static String insufficientFundsToBuy(BigDecimal quantity, String symbol, BigDecimal cost, BigDecimal available) {
        return INSUFFICIENT_FUNDS_TO_BUY + quantity + SPACE + symbol + COST + cost + AVAILABLE + available
                + CLOSING_PARENTHESIS;
    }
}
