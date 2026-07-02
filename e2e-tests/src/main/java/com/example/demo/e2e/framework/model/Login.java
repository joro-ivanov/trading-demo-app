package com.example.demo.e2e.framework.model;

/** Response from {@code POST /auth/login}. */
public record Login(String token, String username) {
}
