package com.example.demo.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import com.example.demo.common.security.Jwt;

/**
 * Real WebSocket integration test (docs/DESIGN.md §6, §7.2): STOMP-over-SockJS clients subscribe to
 * the app's gateway and Kafka messages must be fanned out to them. Exercises the same STOMP client
 * the e2e framework uses. Covers price-tick routing, owner-scoped fill routing, the JWT boundary
 * on CONNECT, and resilience to a malformed message.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class NotificationServiceIT {

    private static final String SECRET = "demo-secret-demo-secret-demo-secret-1234";

    @Container
    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Test
    void deliversKafkaPriceTickToSubscribedStompClient() throws Exception {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        StompSession session = connect("tester");
        subscribe(session, "/topic/prices.AAPL", frames);

        produce("price.ticks", "AAPL", "{\"symbol\":\"AAPL\",\"price\":190.40}");

        String frame = frames.poll(15, TimeUnit.SECONDS);
        session.disconnect();
        assertThat(frame).contains("\"symbol\":\"AAPL\"").contains("190.40");
    }

    @Test
    void deliversKafkaFillOnlyToItsOwner() throws Exception {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        BlockingQueue<String> otherFrames = new LinkedBlockingQueue<>();
        StompSession session = connect("tester");
        StompSession otherSession = connect("other");
        subscribe(session, "/user/queue/fills", frames);
        subscribe(otherSession, "/user/queue/fills", otherFrames);

        produce("fills", "AAPL", "{\"orderId\":\"o-1\",\"symbol\":\"AAPL\",\"side\":\"BUY\","
                + "\"quantity\":3,\"price\":190.40,\"owner\":\"tester\","
                + "\"filledAt\":\"2026-07-01T10:15:30Z\",\"sequenceId\":1}");

        String frame = frames.poll(15, TimeUnit.SECONDS);
        session.disconnect();
        otherSession.disconnect();
        String otherFrame = otherFrames.poll(1, TimeUnit.SECONDS);
        assertSoftly(softly -> {
            softly.assertThat(frame).contains("\"orderId\":\"o-1\"").contains("\"symbol\":\"AAPL\"");
            softly.assertThat(otherFrame).isNull();
        });
    }

    // One tick is broadcast to every subscriber of the symbol topic.
    @Test
    void fansOutOnePriceTickToMultipleSubscribers() throws Exception {
        BlockingQueue<String> framesA = new LinkedBlockingQueue<>();
        BlockingQueue<String> framesB = new LinkedBlockingQueue<>();
        StompSession sessionA = connect("alice");
        StompSession sessionB = connect("bob");
        subscribe(sessionA, "/topic/prices.MSFT", framesA);
        subscribe(sessionB, "/topic/prices.MSFT", framesB);

        produce("price.ticks", "MSFT", "{\"symbol\":\"MSFT\",\"price\":410.10}");

        String frameA = framesA.poll(15, TimeUnit.SECONDS);
        String frameB = framesB.poll(15, TimeUnit.SECONDS);
        sessionA.disconnect();
        sessionB.disconnect();
        assertSoftly(softly -> {
            softly.assertThat(frameA).contains("410.10");
            softly.assertThat(frameB).contains("410.10");
        });
    }

    // A poison price tick (invalid JSON) must not stall the gateway — a following good tick still
    // arrives (the listener's error is handled and it keeps consuming).
    @Test
    void aMalformedPriceTickDoesNotStallTheGateway() throws Exception {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        StompSession session = connect("tester");
        subscribe(session, "/topic/prices.GOOG", frames);

        produce("price.ticks", "GOOG", "}{ not json");
        produce("price.ticks", "GOOG", "{\"symbol\":\"GOOG\",\"price\":175.55}");

        String frame = frames.poll(20, TimeUnit.SECONDS);
        session.disconnect();
        assertThat(frame).contains("175.55");
    }

    // The STOMP CONNECT frame's JWT is validated by a channel interceptor (the WS security boundary).
    // A well-formed token signed with the WRONG secret must be rejected — the connect never completes.
    @Test
    void rejectsStompConnectWithForgedToken() {
        WebSocketStompClient stomp = new WebSocketStompClient(
                new SockJsClient(List.<Transport>of(new WebSocketTransport(new StandardWebSocketClient()))));
        stomp.setMessageConverter(new StringMessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer "
                + Jwt.issue("attacker", "wrong-secret-wrong-secret-wrong-secret-99", 300));

        CompletableFuture<StompSession> connect = stomp.connectAsync(
                "http://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
                });

        assertThatThrownBy(() -> connect.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
    }

    private StompSession connect(String subject) throws Exception {
        WebSocketStompClient stomp = new WebSocketStompClient(
                new SockJsClient(List.<Transport>of(new WebSocketTransport(new StandardWebSocketClient()))));
        stomp.setMessageConverter(new StringMessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + Jwt.issue(subject, SECRET, 300));
        return stomp.connectAsync("http://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
    }

    private static void subscribe(StompSession session, String destination, BlockingQueue<String> frames) {
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add((String) payload);
            }
        });
    }

    private void produce(String topic, String key, String payload) {
        Producer<String, String> producer = newProducer();
        producer.send(new ProducerRecord<>(topic, key, payload));
        producer.flush();
        producer.close();
    }

    private static Producer<String, String> newProducer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(props);
    }
}
