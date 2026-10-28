package com.example.demo.order.it.harness.stubs;

/** risk-service's {@code /risk/check} answer ({@code RiskDtos.RiskCheckResponse}); {@code reason} is null when allowed. */
public record RiskCheckResponse(boolean allowed, String reason) {
}
