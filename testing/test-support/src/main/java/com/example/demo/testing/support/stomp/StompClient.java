package com.example.demo.testing.support.stomp;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/** A STOMP-over-SockJS client for notification-service's {@code /ws} endpoint, exchanging frames as Strings. */
public final class StompClient {

    private static final String AUTHORIZATION = "Authorization";
    private static final String ENDPOINT = "http://localhost:%d/ws";

    private StompClient() {
    }

    /**
     * Starts a CONNECT to {@code localhost:port/ws} carrying {@code authorization} verbatim as the
     * {@code Authorization} native header ({@code null} = no header). The future fails when the gateway rejects it.
     */
    public static CompletableFuture<StompSession> connect(int port, String authorization) {
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.<Transport>of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new StringMessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        if (authorization != null) {
            connectHeaders.add(AUTHORIZATION, authorization);
        }
        return client.connectAsync(ENDPOINT.formatted(port), new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                });
    }
}
