package com.example.demo.notification.it.harness;

import static com.example.demo.testing.support.fixtures.TestTimeouts.EVENT_TIMEOUT;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.simp.stomp.StompSession;

import com.example.demo.common.json.Json;
import com.example.demo.testing.support.auth.TestJwt;
import com.example.demo.testing.support.contracts.FillEvent;
import com.example.demo.testing.support.contracts.PriceTick;
import com.example.demo.testing.support.fixtures.Topics;
import com.example.demo.testing.support.kafka.KafkaProbe;
import com.example.demo.testing.support.stomp.StompClient;
import com.example.demo.testing.support.stomp.StompFrames;

/**
 * STOMP sessions for the notification ITs: connect (with a valid or an arbitrary {@code Authorization}
 * header) through the shared {@link StompClient}, and subscribe once the subscription is provably live.
 * All waiting and branching lives here so the tests stay linear.
 *
 * <p><b>Subscription readiness.</b> Spring's simple broker answers RECEIPT only for DISCONNECT, never
 * for SUBSCRIBE, and SUBSCRIBE is processed asynchronously on the inbound channel. So the subscribe
 * methods prove the subscription is live end to end: they keep producing a <em>sentinel</em> message
 * for that destination until one is delivered to the new subscription. {@link StompFrames} filters
 * sentinel frames out for good, so late sentinels never reach a test's assertions.
 */
public final class StompProbe {

    /** Upper bound for a CONNECT to complete (or be rejected). */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** Interval between sentinel messages while waiting for a subscription to become live. */
    private static final Duration SENTINEL_INTERVAL = Duration.ofMillis(300);
    /** A price no real tick has, marking the readiness sentinel on a price topic. */
    private static final BigDecimal SENTINEL_PRICE = BigDecimal.valueOf(-1);
    /** The symbol of the readiness sentinel on the fills queue. */
    private static final String SENTINEL_SYMBOL = "READY";

    private final int port;
    private final KafkaProbe kafka;
    private final List<StompSession> sessions = new ArrayList<>();

    StompProbe(int port, KafkaProbe kafka) {
        this.port = port;
        this.kafka = kafka;
    }

    /** Connects as {@code subject} with a valid token signed with the shared secret. */
    public StompSession connect(String subject) throws Exception {
        StompSession session = connectWithAuthorization(TestJwt.bearer(TestJwt.token(subject)))
                .get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    /**
     * Starts a CONNECT carrying {@code authorization} verbatim as the {@code Authorization} native
     * header ({@code null} = no header). The future fails when the gateway rejects the CONNECT.
     */
    public CompletableFuture<StompSession> connectWithAuthorization(String authorization) {
        return StompClient.connect(port, authorization);
    }

    /** Subscribes to {@code /topic/prices.<symbol>} and returns once the subscription is live. */
    public StompFrames<PriceTick> subscribeToPrices(StompSession session, String symbol) {
        PriceTick sentinel = new PriceTick(symbol, SENTINEL_PRICE);
        return subscribeWhenLive(session, "/topic/prices." + symbol, Topics.PRICE_TICKS, symbol, sentinel, PriceTick.class);
    }

    /** Subscribes {@code owner}'s session to {@code /user/queue/fills} and returns once it is live. */
    public StompFrames<FillEvent> subscribeToFills(StompSession session, String owner) {
        FillEvent sentinel = new FillEvent("readiness-" + owner, SENTINEL_SYMBOL, "BUY", BigDecimal.ZERO, BigDecimal.ZERO,
                owner, Instant.EPOCH, 0L);
        return subscribeWhenLive(session, "/user/queue/fills", Topics.FILLS, SENTINEL_SYMBOL, sentinel, FillEvent.class);
    }

    private <T> StompFrames<T> subscribeWhenLive(StompSession session, String destination,
                                                 String topic, String key, T sentinel, Class<T> type) {
        StompFrames<T> frames = new StompFrames<>(type, Json.write(sentinel), EVENT_TIMEOUT);
        session.subscribe(destination, frames);
        await("subscription to " + destination + " is live")
                .atMost(EVENT_TIMEOUT)
                .pollDelay(Duration.ZERO)
                .pollInterval(SENTINEL_INTERVAL)
                .until(() -> {
                    kafka.send(topic, key, sentinel);
                    return frames.sentinelSeen();
                });
        return frames;
    }

    void disconnectAll() {
        for (StompSession session : sessions) {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
        sessions.clear();
    }
}
