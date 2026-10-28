package com.example.demo.e2e.framework.model;

/** The claims auth-service puts in a JWT: subject (the username), issuer, issued-at and expiry (epoch seconds). */
public record TokenClaims(String sub, String iss, Long iat, Long exp) {
}
