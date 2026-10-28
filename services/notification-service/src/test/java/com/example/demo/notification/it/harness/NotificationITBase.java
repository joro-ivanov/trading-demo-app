package com.example.demo.notification.it.harness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.kafka.ConfluentKafkaContainer;

/**
 * Shared harness for the notification-service integration tests (docs/DESIGN.md §7.2): the real
 * STOMP/SockJS gateway on a random port against a real Kafka (Testcontainers). Tests produce to the
 * service's input topics and observe what STOMP clients receive. The Kafka container is a singleton
 * started once per JVM, so every {@code *IT} class shares it and the cached Spring context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class NotificationITBase {

    protected static final String SECRET = "demo-secret-demo-secret-demo-secret-1234";

    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0")
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.kafka")));

    static {
        KAFKA.start();
    }

    @LocalServerPort
    int port;

    /** STOMP client side of the gateway; sessions opened in a test are disconnected after it. */
    protected StompProbe stomp;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeEach
    void openStompProbe() {
        stomp = new StompProbe(port, SECRET, KAFKA.getBootstrapServers());
    }

    @AfterEach
    void disconnectStompSessions() {
        stomp.disconnectAll();
    }

    /** Publishes one record to {@code topic} and waits until Kafka has acknowledged it. */
    protected static void produce(String topic, String key, String payload) {
        KafkaProbe.produce(KAFKA.getBootstrapServers(), topic, key, payload);
    }
}
