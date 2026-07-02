package com.example.demo.e2e.framework.model;

import java.math.BigDecimal;

/** The cash balance as returned by the funds endpoints. */
public record Balance(BigDecimal cash) {
}
