package com.example.demo.e2e.framework.model;

/** Spring error response body returned for API failures. */
public record ErrorResponse(String error, int status, String message, String path) {
}
