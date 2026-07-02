package com.example.demo.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.common.security.Jwt;

/** Issues JWTs for authenticated users using the shared HMAC secret. */
@Service
public class TokenIssuer {

    private final String secret;
    private final long ttlSeconds;

    public TokenIssuer(@Value("${security.jwt.secret}") String secret,
                       @Value("${security.jwt.ttl-seconds}") long ttlSeconds) {
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(String username) {
        return Jwt.issue(username, secret, ttlSeconds);
    }
}
