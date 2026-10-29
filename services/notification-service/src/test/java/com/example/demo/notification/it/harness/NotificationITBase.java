package com.example.demo.notification.it.harness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import com.example.demo.testing.RawJsonPayload;
import com.example.demo.testing.support.containers.TestContainers;
import com.example.demo.testing.support.it.ServiceITBase;
import com.example.demo.testing.support.kafka.KafkaProbe;

/**
 * Shared harness for the notification-service integration tests (docs/DESIGN.md §7.2): the real
 * STOMP/SockJS gateway on a random port ({@link ServiceITBase}) against a real Kafka (Testcontainers).
 * Tests produce to the service's input topics and observe what STOMP clients receive. The Kafka container
 * is a singleton started once per JVM, so every {@code *IT} class shares it and the cached Spring context.
 */
public abstract class NotificationITBase extends ServiceITBase {

    static final ConfluentKafkaContainer KAFKA = TestContainers.kafka();
    private static final KafkaProbe KAFKA_PROBE;

    static {
        KAFKA.start();
        KAFKA_PROBE = new KafkaProbe(KAFKA.getBootstrapServers());
    }

    /** STOMP client side of the gateway; sessions opened in a test are disconnected after it. */
    protected StompProbe stomp;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestContainers.kafka(registry, KAFKA);
    }

    @BeforeEach
    void openStompProbe() {
        stomp = new StompProbe(port, KAFKA_PROBE);
    }

    @AfterEach
    void disconnectStompSessions() {
        stomp.disconnectAll();
    }

    /** Publishes {@code event} (serialized with {@code Json}) to {@code topic} and waits until Kafka has acknowledged it. */
    protected static void produce(String topic, String key, Object event) {
        KAFKA_PROBE.send(topic, key, event);
    }

    /** Publishes {@code payload} verbatim: only for malformed messages no event record can produce. */
    @RawJsonPayload(reason = "raw sender for malformed Kafka messages")
    protected static void produceRaw(String topic, String key, String payload) {
        KAFKA_PROBE.sendRaw(topic, key, payload);
    }
}
