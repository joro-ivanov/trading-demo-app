package com.example.demo.notification.it.harness;

import static org.awaitility.Awaitility.await;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import com.example.demo.common.json.Json;
import com.example.demo.common.security.Jwt;

/**
 * STOMP-over-SockJS client plumbing for the notification ITs: connect (with a valid or an arbitrary
 * {@code Authorization} header), subscribe, and wait for frames with bounded timeouts. All waiting
 * and branching lives here so the tests stay linear.
 *
 * <p><b>Subscription readiness.</b> Spring's simple broker answers RECEIPT only for DISCONNECT, never
 * for SUBSCRIBE, and SUBSCRIBE is processed asynchronously on the inbound channel. So the subscribe
 * methods prove the subscription is live end to end: they keep producing a <em>sentinel</em> message
 * for that destination until one is delivered to the new subscription. Sentinel frames are filtered
 * out of {@link Frames} for good, so late sentinels never reach a test's assertions. Sentinels are event
 * records serialized with {@code Json}; received frames are deserialized into the subscription's record type.
 */
public final class StompProbe {

    /** Upper bound for a CONNECT to complete (or be rejected). */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** Upper bound for an expected frame to arrive. */
    public static final Duration FRAME_TIMEOUT = Duration.ofSeconds(20);

    /** Interval between sentinel messages while waiting for a subscription to become live. */
    private static final Duration SENTINEL_INTERVAL = Duration.ofMillis(300);
    /** A price no real tick has, marking the readiness sentinel on a price topic. */
    private static final BigDecimal SENTINEL_PRICE = BigDecimal.valueOf(-1);
    /** The symbol of the readiness sentinel on the fills queue. */
    private static final String SENTINEL_SYMBOL = "READY";

    private final int port;
    private final String secret;
    private final String kafkaBootstrapServers;
    private final List<StompSession> sessions = new ArrayList<>();

    StompProbe(int port, String secret, String kafkaBootstrapServers) {
        this.port = port;
        this.secret = secret;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    /** Connects as {@code subject} with a valid token signed with the shared secret. */
    public StompSession connect(String subject) throws Exception {
        StompSession session = connectWithAuthorization("Bearer " + Jwt.issue(subject, secret, 300))
                .get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    /**
     * Starts a CONNECT carrying {@code authorization} verbatim as the {@code Authorization} native
     * header ({@code null} = no header). The future fails when the gateway rejects the CONNECT.
     */
    public CompletableFuture<StompSession> connectWithAuthorization(String authorization) {
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.<Transport>of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new StringMessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        if (authorization != null) {
            connectHeaders.add("Authorization", authorization);
        }
        return client.connectAsync("http://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
                });
    }

    /** Subscribes to {@code /topic/prices.<symbol>} and returns once the subscription is live. */
    public Frames<PriceTick> subscribeToPrices(StompSession session, String symbol) {
        PriceTick sentinel = new PriceTick(symbol, SENTINEL_PRICE);
        return subscribeWhenLive(session, "/topic/prices." + symbol, "price.ticks", symbol, sentinel, PriceTick.class);
    }

    /** Subscribes {@code owner}'s session to {@code /user/queue/fills} and returns once it is live. */
    public Frames<FillEvent> subscribeToFills(StompSession session, String owner) {
        FillEvent sentinel = new FillEvent("readiness-" + owner, SENTINEL_SYMBOL, "BUY", BigDecimal.ZERO, BigDecimal.ZERO,
                owner, Instant.EPOCH, 0L);
        return subscribeWhenLive(session, "/user/queue/fills", "fills", SENTINEL_SYMBOL, sentinel, FillEvent.class);
    }

    private <T> Frames<T> subscribeWhenLive(StompSession session, String destination,
                                            String topic, String key, T sentinel, Class<T> type) {
        Frames<T> frames = new Frames<>(Json.write(sentinel), type);
        session.subscribe(destination, frames);
        await("subscription to " + destination + " is live")
                .atMost(FRAME_TIMEOUT)
                .pollDelay(Duration.ZERO)
                .pollInterval(SENTINEL_INTERVAL)
                .until(() -> {
                    KafkaProbe.produce(kafkaBootstrapServers, topic, key, sentinel);
                    return frames.sentinelSeen;
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

    /** The frames received on one subscription, deserialized into {@code T}. */
    public static final class Frames<T> implements StompFrameHandler {

        private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private final String sentinel;
        private final Class<T> type;
        private volatile boolean sentinelSeen;

        Frames(String sentinel, Class<T> type) {
            this.sentinel = sentinel;
            this.type = type;
        }

        /** The next frame, waiting up to {@link #FRAME_TIMEOUT}; fails the test if none arrives. */
        public T awaitFrame() throws InterruptedException {
            return Json.read(pollFrame(), type);
        }

        /** The next frame exactly as received, for the byte-for-byte forwarding check. */
        @RawJsonPayload(reason = "raw frame reader: proves the gateway forwards the Kafka payload byte for byte")
        public String awaitRawFrame() throws InterruptedException {
            return pollFrame();
        }

        private String pollFrame() throws InterruptedException {
            String frame = received.poll(FRAME_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (frame == null) {
                throw new AssertionError("No STOMP frame received within " + FRAME_TIMEOUT);
            }
            return frame;
        }

        /** Every frame that arrives within {@code window} (empty when nothing does). */
        public List<T> receivedWithin(Duration window) throws InterruptedException {
            List<String> frames = new ArrayList<>();
            String first = received.poll(window.toMillis(), TimeUnit.MILLISECONDS);
            if (first != null) {
                frames.add(first);
                received.drainTo(frames);
            }
            return frames.stream().map(frame -> Json.read(frame, type)).toList();
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            String frame = (String) payload;
            if (sentinel.equals(frame)) {
                sentinelSeen = true;
            } else {
                received.add(frame);
            }
        }
    }
}
