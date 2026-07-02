package com.example.demo.e2e.framework.ws;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
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

import com.example.demo.e2e.framework.config.Config;
import com.example.demo.e2e.framework.logging.TestLog;

/**
 * Base of every WebSocket service object — the WS counterpart of {@code BaseService}
 * (docs/DESIGN.md §8.5). This is the <b>only</b> class that depends on the STOMP/SockJS client
 * ({@code org.springframework...socket}/{@code ...messaging.simp}); an ArchUnit rule enforces
 * that isolation so the transport is swappable in one place (docs/DESIGN.md §13.1).
 *
 * <p>Targets the app's own notification gateway, which speaks <b>STOMP over SockJS</b>. (The
 * mocked exchange feed is raw WS and is exercised separately — docs/DESIGN.md §6.) No
 * defensive validation: bad tokens/destinations reach the real gateway.
 */
public abstract class BaseWsService {

    /** Open a STOMP session and subscribe to the given destinations, capturing frames. */
    protected static WsSession connect(String token, String... destinations) {
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        WebSocketStompClient client = new WebSocketStompClient(new SockJsClient(transports));
        client.setMessageConverter(new StringMessageConverter());

        String url = Config.get().wsBaseUrl();
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        TestLog.log("WS connect " + url + " " + List.of(destinations));

        StompSession session = client
                .connectAsync(url, handshakeHeaders, connectHeaders, new StompSessionHandlerAdapter() {
                })
                .orTimeout(10, TimeUnit.SECONDS)
                .join();

        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        for (String destination : destinations) {
            session.subscribe(destination, frameHandler(destination, frames));
            TestLog.log("WS subscribe " + destination);
        }
        return new WsSession(session, frames);
    }

    private static StompFrameHandler frameHandler(String destination, BlockingQueue<String> frames) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                String frame = String.valueOf(payload);
                TestLog.log("WS <= " + destination + " : " + frame);
                frames.add(frame);
            }
        };
    }
}
