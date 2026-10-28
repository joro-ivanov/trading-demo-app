package com.example.demo.alert.web;

import java.math.BigDecimal;

import com.example.demo.alert.domain.AlertCondition;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** {@code POST /alerts}. The owner is the JWT subject, never part of the body. */
public record CreateAlertRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{1,10}$", message = "symbol must be 1-10 uppercase letters")
        String symbol,
        @NotNull
        AlertCondition condition,
        @NotNull @Positive @Digits(integer = 15, fraction = 4)
        BigDecimal threshold) {
}
