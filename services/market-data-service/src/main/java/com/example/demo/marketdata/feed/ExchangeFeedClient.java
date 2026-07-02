package com.example.demo.marketdata.feed;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Connects to the upstream exchange price feed over a raw WebSocket and forwards each frame to
 * {@link PriceProcessor}. On connect it subscribes to the configured symbols by sending
 * {@code subscribe:<symbol>} frames — the exact protocol the WireMock WS mock replies to
 * (docs/DESIGN.md §6, validated by the M0 spike).
 *
 * <p>Active by default (and in all tests); a {@link DemoFeed} replaces it when
 * {@code market-data.feed.mode=demo} (docker-compose, where there is no exchange to mock).
 *
 * <p>Implemented as a {@link SmartLifecycle} so it starts with the application context and is
 * driven by Spring's lifecycle (and so tests' WireMock stubs are registered before it connects).
 */
@Component
@ConditionalOnProperty(name = "market-data.feed.mode", havingValue = "exchange", matchIfMissing = true)
public class ExchangeFeedClient implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(ExchangeFeedClient.class);

    private final PriceProcessor processor;
    private final String wsUrl;
    private final List<String> symbols;

    private volatile WebSocketClient client;
    private volatile boolean running;

    public ExchangeFeedClient(PriceProcessor processor,
                              @Value("${exchange.ws-url}") String wsUrl,
                              @Value("${market-data.symbols}") List<String> symbols) {
        this.processor = processor;
        this.wsUrl = wsUrl;
        this.symbols = symbols;
    }

    @Override
    public void start() {
        client = new WebSocketClient(URI.create(wsUrl)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                LOG.info("Exchange feed connected to {}; subscribing to {}", wsUrl, symbols);
                symbols.forEach(symbol -> send("subscribe:" + symbol));
            }

            @Override
            public void onMessage(String message) {
                LOG.info("Exchange feed frame: {}", message);
                processor.process(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                LOG.info("Exchange feed closed: {} {}", code, reason);
            }

            @Override
            public void onError(Exception ex) {
                LOG.warn("Exchange feed error", ex);
            }
        };

        try {
            boolean connected = client.connectBlocking(10, TimeUnit.SECONDS);
            if (!connected) {
                LOG.warn("Could not connect to exchange feed at {}", wsUrl);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        running = true;
    }

    @Override
    public void stop() {
        if (client != null) {
            client.close();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
