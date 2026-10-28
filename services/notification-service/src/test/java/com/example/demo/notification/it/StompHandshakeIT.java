package com.example.demo.notification.it;

import static com.example.demo.notification.it.harness.NotificationFixtures.TESTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.messaging.simp.stomp.StompSession;

import com.example.demo.common.security.Jwt;
import com.example.demo.notification.it.harness.NotificationITBase;
import com.example.demo.notification.it.harness.StompProbe;

/**
 * STOMP CONNECT authentication (the WS security boundary): the {@code /ws} handshake is anonymous,
 * but the CONNECT frame's {@code Authorization: Bearer <jwt>} is validated by a channel interceptor.
 */
class StompHandshakeIT extends NotificationITBase {

    private static final String BEARER = "Bearer ";
    private static final String WRONG_SECRET = "wrong-secret-wrong-secret-wrong-secret-99";
    private static final String ATTACKER = "attacker";
    private static final long TOKEN_TTL_SECONDS = 300;
    /** Issued 10 minutes ago, expired 1 minute ago. */
    private static final Instant EXPIRED_ISSUED_AT = Instant.now().minusSeconds(600);
    private static final Instant EXPIRED_AT = Instant.now().minusSeconds(60);

    private static final List<Arguments> REJECTED_AUTHORIZATIONS = List.of(
            Arguments.of("forged (wrong secret)", BEARER + Jwt.issue(ATTACKER, WRONG_SECRET, TOKEN_TTL_SECONDS)),
            Arguments.of("expired", BEARER + Jwt.issue(TESTER, SECRET, EXPIRED_ISSUED_AT, EXPIRED_AT)),
            Arguments.of("malformed", BEARER + "not-a-jwt"),
            Arguments.of("wrong scheme", "Basic dGVzdGVyOnBhc3N3b3Jk"),
            Arguments.of("missing header", null));

    @Test
    void acceptsStompConnectWithAValidToken() throws Exception {
        StompSession session = stomp.connect(TESTER);

        assertThat(session.isConnected()).isTrue();
    }

    static Stream<Arguments> rejectedAuthorizations() {
        return REJECTED_AUTHORIZATIONS.stream();
    }

    // The connect never completes: the gateway answers the CONNECT with an ERROR frame.
    @ParameterizedTest(name = "{0} Authorization -> CONNECT rejected")
    @MethodSource("rejectedAuthorizations")
    void rejectsStompConnectWithoutAValidToken(String description, String authorization) {
        CompletableFuture<StompSession> connect = stomp.connectWithAuthorization(authorization);

        assertThatThrownBy(() -> connect.get(StompProbe.CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }
}
