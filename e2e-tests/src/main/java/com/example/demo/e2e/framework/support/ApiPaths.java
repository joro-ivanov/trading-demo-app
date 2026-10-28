package com.example.demo.e2e.framework.support;

/** Request paths the services echo in an error body's {@code path} field (expected values in tests). */
public final class ApiPaths {

    public static final String ORDERS = "/orders";
    /** Prefix of a single order's path; append the order id. */
    public static final String ORDER = "/orders/";
    public static final String AUTH_LOGIN = "/auth/login";
    public static final String AUTH_REGISTER = "/auth/register";
    public static final String PORTFOLIO = "/portfolio";
    public static final String FUNDS_DEPOSIT = "/funds/deposit";
    public static final String FUNDS_WITHDRAW = "/funds/withdraw";

    private ApiPaths() {
    }
}
