package com.example.demo.notification.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
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

    @Test
    void acceptsStompConnectWithAValidToken() throws Exception {
        StompSession session = stomp.connect("tester");

        assertThat(session.isConnected()).isTrue();
    }

    static Stream<Arguments> rejectedAuthorizations() {
        return Stream.of(
                Arguments.of("forged (wrong secret)",
                        "Bearer " + Jwt.issue("attacker", "wrong-secret-wrong-secret-wrong-secret-99", 300)),
                Arguments.of("expired",
                        "Bearer " + Jwt.issue("tester", SECRET, Instant.now().minusSeconds(600),
                                Instant.now().minusSeconds(60))),
                Arguments.of("malformed", "Bearer not-a-jwt"),
                Arguments.of("wrong scheme", "Basic dGVzdGVyOnBhc3N3b3Jk"),
                Arguments.of("missing header", null));
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
