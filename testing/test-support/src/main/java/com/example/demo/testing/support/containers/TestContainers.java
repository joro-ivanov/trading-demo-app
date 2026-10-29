package com.example.demo.testing.support.containers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The real infrastructure the ITs run against, one image per kind, each container logging to
 * {@code target/container-logs/<name>.log} ({@link ContainerLogs}), plus the Spring properties pointing a
 * service at it. Callers start the containers (singletons in a {@code static} block, or JUnit {@code @Container}).
 */
public final class TestContainers {

    public static final String POSTGRES_IMAGE = "postgres:17-alpine";
    public static final String KAFKA_IMAGE = "confluentinc/cp-kafka:7.8.0";
    public static final String REDIS_IMAGE = "redis:7-alpine";
    public static final int REDIS_PORT = 6379;

    public static final String POSTGRES = "postgres";
    public static final String KAFKA = "kafka";
    public static final String REDIS = "redis";

    private TestContainers() {
    }

    @SuppressWarnings("resource")
    public static PostgreSQLContainer postgres(String logName) {
        return new PostgreSQLContainer(POSTGRES_IMAGE).withLogConsumer(ContainerLogs.toFile(logName));
    }

    public static PostgreSQLContainer postgres() {
        return postgres(POSTGRES);
    }

    @SuppressWarnings("resource")
    public static ConfluentKafkaContainer kafka() {
        return new ConfluentKafkaContainer(KAFKA_IMAGE).withLogConsumer(ContainerLogs.toFile(KAFKA));
    }

    @SuppressWarnings("resource")
    public static GenericContainer<?> redis() {
        return new GenericContainer<>(REDIS_IMAGE).withExposedPorts(REDIS_PORT).withLogConsumer(ContainerLogs.toFile(REDIS));
    }

    /** {@code spring.datasource.*} for {@code postgres}. */
    public static void datasource(DynamicPropertyRegistry registry, PostgreSQLContainer postgres) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** {@code spring.kafka.bootstrap-servers} for {@code kafka}. */
    public static void kafka(DynamicPropertyRegistry registry, ConfluentKafkaContainer kafka) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    /** {@code spring.data.redis.*} for {@code redis}. */
    public static void redis(DynamicPropertyRegistry registry, GenericContainer<?> redis) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(REDIS_PORT));
    }
}
