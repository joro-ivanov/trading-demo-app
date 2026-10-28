package com.example.demo.auth.it.harness;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.demo.common.security.Jwt;

import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Shared harness for the auth-service integration tests (docs/DESIGN.md §7.2): the real service on a
 * random port against a real Postgres (Testcontainers), driven over HTTP with REST-Assured. Flyway
 * seeds the demo users (alice/bob/demo, password {@code password}); every test starts from exactly that
 * seed (users registered by earlier tests are removed), so user lists can be asserted exactly. The container is a singleton
 * started once per JVM, so every {@code *IT} class shares it and the cached Spring context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AuthITBase {

    protected static final String SECRET = "demo-secret-demo-secret-demo-secret-1234";

    /** Request DTOs are serialized with the project's {@code Json}, never hand-written. */
    private static final RestAssuredConfig DTO_JSON = RestAssuredConfig.config()
            .objectMapperConfig(new ObjectMapperConfig().defaultObjectMapper(new JsonObjectMapper()));

    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.postgres")));

    static {
        POSTGRES.start();
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Back to the Flyway seed (u1 alice, u2 bob, u3 demo) before every test. */
    @BeforeEach
    void resetToSeededUsers() {
        jdbc.update("delete from users where id not in ('u1', 'u2', 'u3')");
    }

    /** An unauthenticated JSON request to the running service; {@code .body(dto)} serializes with {@code Json}. */
    protected RequestSpecification api() {
        return RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .config(DTO_JSON)
                .contentType(ContentType.JSON);
    }

    /** A JSON request carrying {@code Authorization: Bearer <token>}. */
    protected RequestSpecification api(String token) {
        return api().header("Authorization", "Bearer " + token);
    }

    /** The claims a test can assert exactly: who the token is for and how long it lives. */
    public record TokenClaims(String subject, long lifetimeSeconds) {
    }

    /** Verifies {@code token} with the shared secret (fails on a bad signature) and returns its claims. */
    protected static TokenClaims claimsOf(String token) {
        org.springframework.security.oauth2.jwt.Jwt jwt = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build()
                .decode(token);
        return new TokenClaims(jwt.getSubject(), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
    }

    /** A valid HS256 token for {@code subject}, signed with the shared secret (5 minutes). */
    protected static String validToken(String subject) {
        return Jwt.issue(subject, SECRET, 300);
    }
}
