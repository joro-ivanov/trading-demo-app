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

    private static WireMockServer wireMock;

    @BeforeAll
    static void startServer() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor("localhost", wireMock.port());
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
                .withName("echo")
                .withBody(equalTo("hello"))
                .willTriggerActions(sendMessage("world").onOriginatingChannel()));

        CapturingClient client = connect("/exchange");
        client.send("hello");
        String reply = client.frames.poll(5, TimeUnit.SECONDS);
        client.closeBlocking();

        assertThat(reply).isEqualTo("world");
    }

    @Test
    void streamsPriceBurstOnSubscribe() throws Exception {
        messageStubFor(message()
                .withName("price-feed")
                .withBody(equalTo("subscribe:AAPL"))
                .willTriggerActions(
                        sendMessage("{\"symbol\":\"AAPL\",\"price\":190.10}").onOriginatingChannel(),
                        sendMessage("{\"symbol\":\"AAPL\",\"price\":190.40}").onOriginatingChannel()));

        CapturingClient client = connect("/exchange/marketdata");
        client.send("subscribe:AAPL");
        String first = client.frames.poll(5, TimeUnit.SECONDS);
        String second = client.frames.poll(5, TimeUnit.SECONDS);
        client.closeBlocking();

        assertThat(first).isEqualTo("{\"symbol\":\"AAPL\",\"price\":190.10}");
        assertThat(second).isEqualTo("{\"symbol\":\"AAPL\",\"price\":190.40}");
    }

    @Test
    void injectsAMalformedFrameForResilienceTesting() throws Exception {
        messageStubFor(message()
                .withName("malformed feed")
                .withBody(equalTo("subscribe:AAPL"))
                .willTriggerActions(sendMessage("}{not-json").onOriginatingChannel()));

        CapturingClient client = connect("/exchange");
        client.send("subscribe:AAPL");
        String reply = client.frames.poll(5, TimeUnit.SECONDS);
        client.closeBlocking();

        assertThat(reply).isEqualTo("}{not-json");
    }

    @Test
    void unmatchedMessageYieldsNoFrame() throws Exception {
        messageStubFor(message()
                .withName("known feed")
                .withBody(equalTo("subscribe:AAPL"))
                .willTriggerActions(sendMessage("price").onOriginatingChannel()));

        CapturingClient client = connect("/exchange");
        client.send("subscribe:UNKNOWN");
        String reply = client.frames.poll(2, TimeUnit.SECONDS);
        client.closeBlocking();

        assertThat(reply).isNull();
    }

    private static CapturingClient connect(String path) throws Exception {
        CapturingClient client = new CapturingClient(new URI("ws://localhost:" + wireMock.port() + path));
        client.connectBlocking(5, TimeUnit.SECONDS);
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
