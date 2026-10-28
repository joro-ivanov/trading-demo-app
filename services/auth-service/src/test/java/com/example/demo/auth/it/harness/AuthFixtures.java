package com.example.demo.auth.it.harness;

import com.example.demo.auth.web.AuthDtos.LoginRequest;
import com.example.demo.auth.web.AuthDtos.RegisterRequest;
import com.example.demo.auth.web.AuthDtos.UserResponse;

/**
 * Named values and request bodies shared by the auth-service ITs: endpoints, the Flyway-seeded users
 * (V1__users.sql, all with password {@code password}), the token lifetime and request DTO factories.
 */
public final class AuthFixtures {

    public static final String LOGIN = "/auth/login";
    public static final String REGISTER = "/auth/register";
    public static final String USERS = "/auth/users";

    public static final UserResponse ALICE = new UserResponse("u1", "alice", "Alice Trader");
    public static final UserResponse BOB = new UserResponse("u2", "bob", "Bob Trader");
    public static final UserResponse DEMO = new UserResponse("u3", "demo", "Demo User");
    public static final String SEEDED_PASSWORD = "password";

    /** {@code security.jwt.ttl-seconds} default (application.yaml). */
    public static final long TOKEN_TTL_SECONDS = 3600;

    /** Subject of the tokens tests use when the caller's identity does not matter. */
    public static final String TEST_SUBJECT = "tester";

    private static final String EMAIL_DOMAIN = "@demo.example.com";

    private AuthFixtures() {
    }

    public static LoginRequest loginRequest(String username, String password) {
        return new LoginRequest(username, password);
    }

    public static RegisterRequest registerRequest(String username, String email, String password) {
        return new RegisterRequest(username, email, password);
    }

    /** The demo e-mail address for {@code localPart} ({@code <localPart>@demo.example.com}). */
    public static String emailOf(String localPart) {
        return localPart + EMAIL_DOMAIN;
    }

    public static String userPath(String id) {
        return USERS + "/" + id;
    }
}
