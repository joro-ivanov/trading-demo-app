package com.example.demo.spike;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.message;
import static com.github.tomakehurst.wiremock.client.WireMock.messageStubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.resetMessageStubs;
import static com.github.tomakehurst.wiremock.client.WireMock.sendMessage;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * M0 spike (docs/DESIGN.md §9) — proves WireMock 4 beta WebSocket mocking is viable for the
 * exchange price feed:
 * <ul>
 *   <li>accepts a raw WebSocket connection,</li>
 *   <li>matches an inbound frame against a message stub, and</li>
 *   <li>pushes one or more server frames back (modelling subscribe → price burst).</li>
 * </ul>
 * If this stays green, M2+ can mock the raw exchange feed with WireMock instead of the
 * documented embedded-WS-stub fallback.
 */
class WireMockWebSocketSpikeTest {

    private static final String HOST = "localhost";
    private static final String WS_ORIGIN_PREFIX = "ws://" + HOST + ":";
    private static final String EXCHANGE_PATH = "/exchange";
    private static final String MARKET_DATA_PATH = "/exchange/marketdata";
    private static final long FRAME_TIMEOUT_SECONDS = 5;
    private static final long SILENCE_WINDOW_SECONDS = 2;
    private static final long CONNECT_TIMEOUT_SECONDS = 5;

    private static final String ECHO_STUB = "echo";
    private static final String GREETING = "hello";
    private static final String ECHO_REPLY = "world";

    private static final String PRICE_FEED_STUB = "price-feed";
    private static final String MALFORMED_FEED_STUB = "malformed feed";
    private static final String KNOWN_FEED_STUB = "known feed";
    private static final String SUBSCRIBE_AAPL = "subscribe:AAPL";
    private static final String SUBSCRIBE_UNKNOWN = "subscribe:UNKNOWN";
    private static final String FIRST_TICK = "{\"symbol\":\"AAPL\",\"price\":190.10}";
    private static final String SECOND_TICK = "{\"symbol\":\"AAPL\",\"price\":190.40}";
    private static final String MALFORMED_FRAME = "}{not-json";
    private static final String ANY_PRICE_FRAME = "price";

    private static WireMockServer wireMock;

    @BeforeAll
    static void startServer() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(HOST, wireMock.port());
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        resetMessageStubs();
    }

    @Test
    void echoesInboundFrameBackToClient() throws Exception {
        messageStubFor(message()
                .withName(ECHO_STUB)
                .withBody(equalTo(GREETING))
                .willTriggerActions(sendMessage(ECHO_REPLY).onOriginatingChannel()));

        CapturingClient client = connect(EXCHANGE_PATH);
        client.send(GREETING);
        String reply = client.frames.poll(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        client.closeBlocking();

        assertThat(reply).isEqualTo(ECHO_REPLY);
    }

    @Test
    void streamsPriceBurstOnSubscribe() throws Exception {
        messageStubFor(message()
                .withName(PRICE_FEED_STUB)
                .withBody(equalTo(SUBSCRIBE_AAPL))
                .willTriggerActions(
                        sendMessage(FIRST_TICK).onOriginatingChannel(),
                        sendMessage(SECOND_TICK).onOriginatingChannel()));

        CapturingClient client = connect(MARKET_DATA_PATH);
        client.send(SUBSCRIBE_AAPL);
        String first = client.frames.poll(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String second = client.frames.poll(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        client.closeBlocking();

        assertThat(first).isEqualTo(FIRST_TICK);
        assertThat(second).isEqualTo(SECOND_TICK);
    }

    @Test
    void injectsAMalformedFrameForResilienceTesting() throws Exception {
        messageStubFor(message()
                .withName(MALFORMED_FEED_STUB)
                .withBody(equalTo(SUBSCRIBE_AAPL))
                .willTriggerActions(sendMessage(MALFORMED_FRAME).onOriginatingChannel()));

        CapturingClient client = connect(EXCHANGE_PATH);
        client.send(SUBSCRIBE_AAPL);
        String reply = client.frames.poll(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        client.closeBlocking();

        assertThat(reply).isEqualTo(MALFORMED_FRAME);
    }

    @Test
    void unmatchedMessageYieldsNoFrame() throws Exception {
        messageStubFor(message()
                .withName(KNOWN_FEED_STUB)
                .withBody(equalTo(SUBSCRIBE_AAPL))
                .willTriggerActions(sendMessage(ANY_PRICE_FRAME).onOriginatingChannel()));

        CapturingClient client = connect(EXCHANGE_PATH);
        client.send(SUBSCRIBE_UNKNOWN);
        String reply = client.frames.poll(SILENCE_WINDOW_SECONDS, TimeUnit.SECONDS);
        client.closeBlocking();

        assertThat(reply).isNull();
    }

    private static CapturingClient connect(String path) throws Exception {
        CapturingClient client = new CapturingClient(new URI(WS_ORIGIN_PREFIX + wireMock.port() + path));
        client.connectBlocking(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return client;
    }

    private static final class CapturingClient extends WebSocketClient {

        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();

        private CapturingClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            // no-op
        }

        @Override
        public void onMessage(String message) {
            frames.add(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            // no-op
        }

        @Override
        public void onError(Exception ex) {
            // no-op
        }
    }
}
