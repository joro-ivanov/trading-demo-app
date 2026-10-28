package com.example.demo.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class JwtTest {

    private static final String SECRET = "0123456789012345678901234567890123456789";
    private static final String OTHER_SECRET = "abcdefghabcdefghabcdefghabcdefghabcdefgh";
    private static final String DEMO_SUBJECT = "demo";
    private static final String ALICE_SUBJECT = "alice";
    private static final String ISSUER = "auth-service";
    private static final long SHORT_TTL_SECONDS = 60;
    private static final long TTL_SECONDS = 300;
    private static final String SEGMENT_SEPARATOR = "\\.";
    private static final int JWS_SEGMENTS = 3;

    @Test
    void issuesASignedThreePartToken() throws Exception {
        String token = Jwt.issue(DEMO_SUBJECT, SECRET, SHORT_TTL_SECONDS);
        JWSHeader header = SignedJWT.parse(token).getHeader();

        assertSoftly(softly -> {
            softly.assertThat(header.getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
            softly.assertThat(token.split(SEGMENT_SEPARATOR)).hasSize(JWS_SEGMENTS);
        });
    }

    @Test
    void issuedTokenVerifiesAndCarriesItsClaims() throws Exception {
        SignedJWT jwt = SignedJWT.parse(Jwt.issue(ALICE_SUBJECT, SECRET, TTL_SECONDS));

        assertThat(jwt.verify(new MACVerifier(SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertSoftly(softly -> {
            softly.assertThat(claims.getSubject()).isEqualTo(ALICE_SUBJECT);
            softly.assertThat(claims.getIssuer()).isEqualTo(ISSUER);
            softly.assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
                    .isEqualTo(Duration.ofSeconds(TTL_SECONDS));
        });
    }

    @Test
    void aTokenDoesNotVerifyAgainstADifferentSecret() throws Exception {
        SignedJWT jwt = SignedJWT.parse(Jwt.issue(ALICE_SUBJECT, SECRET, TTL_SECONDS));
        assertThat(jwt.verify(new MACVerifier(OTHER_SECRET.getBytes(StandardCharsets.UTF_8)))).isFalse();
    }
}
