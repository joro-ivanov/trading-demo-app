package com.example.demo.risk.web;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import com.example.demo.risk.domain.Side;

/** Request/response payloads for the risk endpoints. */
public final class RiskDtos {

    private RiskDtos() {
    }

    /** Whether the limits are the configured defaults or the user's own. */
    public enum LimitSource {
        DEFAULT, CUSTOM
    }

    /** {@code updatedAt} is null for {@link LimitSource#DEFAULT} limits (never stored). */
    public record RiskLimitsResponse(BigDecimal maxOrderNotional,
                                     BigDecimal maxPositionQuantity,
                                     LimitSource source,
                                     Instant updatedAt) {
    }

    public record UpdateRiskLimitsRequest(
            @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal maxOrderNotional,
            @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal maxPositionQuantity) {
    }

    /** {@code price} is optional: without it the notional rule cannot be evaluated and is skipped. */
    public record RiskCheckRequest(
            @NotBlank @Pattern(regexp = "^[A-Z]{1,10}$") String symbol,
            @NotNull Side side,
            @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal quantity,
            @Positive @Digits(integer = 15, fraction = 4) BigDecimal price) {
    }

    public record RiskCheckResponse(boolean allowed, String reason) {

        public static RiskCheckResponse approved() {
            return new RiskCheckResponse(true, null);
        }

        public static RiskCheckResponse rejected(String reason) {
            return new RiskCheckResponse(false, reason);
        }
    }
}
