package com.example.demo.order.it.harness;

/** The JSON error body written by {@code common}'s {@code ApiErrorHandler}. */
public record ApiError(String timestamp, int status, String error, String message, String path) {
}
