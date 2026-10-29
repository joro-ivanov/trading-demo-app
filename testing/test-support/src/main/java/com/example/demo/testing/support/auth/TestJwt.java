package com.example.demo.testing.support.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.example.demo.common.security.Jwt;

/** The JWTs tests send: signed with the services' dev secret (each {@code application.yaml} default), forged or expired. */
public final class TestJwt {

    /** The {@code JWT_SECRET} default every service's {@code application.yaml} falls back to. */
    public static final String SECRET = "demo-secret-demo-secret-demo-secret-1234";
    /** A well-formed secret the services do not know: tokens signed with it must be rejected. */
    public static final String FORGED_SECRET = "wrong-secret-wrong-secret-wrong-secret-99";
    /** Lifetime of a valid test token. */
    public static final long TTL_SECONDS = 3600;
    /** A lifetime that makes the token expired on arrival. */
    public static final long EXPIRED_TTL_SECONDS = -60;
    public static final String BEARER = "Bearer ";

    private static final String HMAC_SHA256 = "HmacSHA256";

    private TestJwt() {
    }

    /** A valid HS256 token for {@code subject}. */
    public static String token(String subject) {
        return Jwt.issue(subject, SECRET, TTL_SECONDS);
    }

    /** A token for {@code subject} that expired before it was sent. */
    public static String expired(String subject) {
        return Jwt.issue(subject, SECRET, EXPIRED_TTL_SECONDS);
    }

    /** A token for {@code subject} signed with {@link #FORGED_SECRET}. */
    public static String forged(String subject) {
        return Jwt.issue(subject, FORGED_SECRET, TTL_SECONDS);
    }

    /** The {@code Authorization} header value for {@code token}. */
    public static String bearer(String token) {
        return BEARER + token;
    }

    /** Verifies {@code token} with {@link #SECRET} (fails on a bad signature) and returns its claims. */
    public static TokenClaims claims(String token) {
        org.springframework.security.oauth2.jwt.Jwt jwt = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), HMAC_SHA256))
                .build()
                .decode(token);
        return new TokenClaims(jwt.getSubject(), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
    }

    /** The claims a test can assert exactly: who the token is for and how long it lives. */
    public record TokenClaims(String subject, long lifetimeSeconds) {
    }
}
