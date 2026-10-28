package com.example.demo.e2e.framework.support;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.example.demo.e2e.framework.http.Json;
import com.example.demo.e2e.framework.model.TokenClaims;

/** Decodes (without verifying) the claims of a JWT, so tests assert who a token was issued to. */
public final class Tokens {

    /** The issuer auth-service writes into every token it signs. */
    public static final String AUTH_SERVICE_ISSUER = "auth-service";

    private static final String SEGMENT_SEPARATOR = "\\.";
    private static final int PAYLOAD_SEGMENT = 1;

    private Tokens() {
    }

    public static TokenClaims claims(String token) {
        byte[] payload = Base64.getUrlDecoder().decode(token.split(SEGMENT_SEPARATOR)[PAYLOAD_SEGMENT]);
        return Json.read(new String(payload, StandardCharsets.UTF_8), TokenClaims.class);
    }
}
