package com.example.demo.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class JwtTest {

    private static final String SECRET = "0123456789012345678901234567890123456789";

    @Test
    void issuesASignedThreePartToken() {
        String token = Jwt.issue("demo", SECRET, 60);

        assertSoftly(softly -> {
            softly.assertThat(token).isNotBlank();
            softly.assertThat(token.split("\\.")).hasSize(3);
        });
    }

    @Test
    void issuedTokenVerifiesAndCarriesItsClaims() throws Exception {
        SignedJWT jwt = SignedJWT.parse(Jwt.issue("alice", SECRET, 300));

        assertThat(jwt.verify(new MACVerifier(SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertSoftly(softly -> {
            softly.assertThat(claims.getSubject()).isEqualTo("alice");
            softly.assertThat(claims.getIssuer()).isEqualTo("auth-service");
            softly.assertThat(claims.getExpirationTime()).isAfter(new Date());
        });
    }

    @Test
    void aTokenDoesNotVerifyAgainstADifferentSecret() throws Exception {
        SignedJWT jwt = SignedJWT.parse(Jwt.issue("alice", SECRET, 300));
        String otherSecret = "abcdefghabcdefghabcdefghabcdefghabcdefgh";

        assertThat(jwt.verify(new MACVerifier(otherSecret.getBytes(StandardCharsets.UTF_8)))).isFalse();
    }
}
