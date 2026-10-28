package com.example.demo.auth.web;

import com.example.demo.auth.domain.UserEntity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request/response payloads for auth-service. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, String username) {
    }

    /** Self-service registration. {@code confirmPassword} is a client-side concern and is NOT here. */
    public record RegisterRequest(
            @NotBlank
            @Size(min = 3, max = 25)
            @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "username must be alphanumeric or underscore")
            String username,

            @NotBlank
            @Email
            String email,

            @NotBlank
            @Size(min = 8)
            String password) {
    }

    /** The authenticated caller's own profile ({@code GET /auth/me}); unlike the user list it includes the email. */
    public record MeResponse(String id, String username, String email, String displayName) {
        public static MeResponse from(UserEntity user) {
            return new MeResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName());
        }
    }

    public record UserResponse(String id, String username, String displayName) {
        public static UserResponse from(UserEntity user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName());
        }
    }
}
