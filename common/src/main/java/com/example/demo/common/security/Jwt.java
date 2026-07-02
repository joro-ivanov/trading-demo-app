package com.example.demo.common.security;

import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Issues HMAC-signed (HS256) JWTs. Used by auth-service to mint tokens and by tests to mint a valid
 * token for the secured services (which validate the same shared secret via Spring Security). The
 * secret must be at least 32 bytes for HS256.
 */
public final class Jwt {

    private Jwt() {
    }

    public static String issue(String subject, String secret, long ttlSeconds) {
        Instant now = Instant.now();
        return issue(subject, secret, now, now.plusSeconds(ttlSeconds));
    }

    public static String issue(String subject, String secret, Instant issuedAt, Instant expiresAt) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("auth-service")
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
        return jwt.serialize();
    }
}
