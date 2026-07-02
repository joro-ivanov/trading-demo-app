package com.example.demo.e2e.framework.model;

/** Registration payload for {@code POST /auth/register} (confirm-password is a client-side concern). */
public record RegisterRequest(String username, String email, String password) {
}
