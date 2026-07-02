package com.example.demo.e2e.framework.model;

/** Credentials payload for {@code POST /auth/login}. */
public record LoginRequest(String username, String password) {
}
