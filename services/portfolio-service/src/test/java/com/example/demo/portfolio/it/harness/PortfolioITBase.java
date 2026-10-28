package com.example.demo.portfolio.it.harness;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.demo.common.json.Json;
import com.example.demo.common.security.Jwt;
import com.example.demo.portfolio.web.FillResponse;
import com.example.demo.portfolio.web.FundsDtos.BalanceResponse;
import com.example.demo.portfolio.web.PositionResponse;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Shared harness for the portfolio-service integration tests (docs/DESIGN.md §7.2): the real service
 * on a random port against real Postgres + cp-kafka (Testcontainers), driven over HTTP with
 * REST-Assured. Inbound fills/ticks are produced with {@link KafkaProbe}. Containers are singletons
 * started once per JVM, so every {@code *IT} class shares them and the cached Spring context —
 * which is why each test uses its own symbol and/or owner (positions accumulate).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PortfolioITBase {

    protected static final String SECRET = "demo-secret-demo-secret-demo-secret-1234";
    /** Owner of fills sent without an explicit owner, and subject of {@link #TOKEN}. */
    protected static final String TESTER = "tester";
    protected static final String TOKEN = token(TESTER);

    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

    protected static final KafkaProbe KAFKA_PROBE;

    static {
        POSTGRES.start();
        KAFKA.start();
        KAFKA_PROBE = new KafkaProbe(KAFKA.getBootstrapServers());
    }

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    /** An unauthenticated JSON request to the running service. */
    protected RequestSpecification api() {
        return RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON);
    }

    /** A JSON request carrying {@code Authorization: Bearer <token>}. */
    protected RequestSpecification api(String token) {
        return api().header("Authorization", "Bearer " + token);
    }

    /** A valid HS256 token for {@code subject}, signed with the shared secret (1 hour). */
    protected static String token(String subject) {
        return Jwt.issue(subject, SECRET, 3600);
    }

    /** {@code GET /portfolio} as the token's owner (expects 200). */
    protected List<PositionResponse> positions(String token) {
        String body = api(token).get("/portfolio").then().statusCode(200).extract().asString();
        return Json.readList(body, PositionResponse.class);
    }

    /** The owner's position in {@code symbol}; throws AssertionError (so Awaitility retries) if absent. */
    protected PositionResponse position(String token, String symbol) {
        return positions(token).stream()
                .filter(position -> position.symbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no position for " + symbol));
    }

    /** {@link #position(String, String)} for {@link #TESTER}. */
    protected PositionResponse position(String symbol) {
        return position(TOKEN, symbol);
    }

    /** {@code GET /portfolio/fills} as the token's owner (expects 200). */
    protected List<FillResponse> recentFills(String token) {
        String body = api(token).get("/portfolio/fills").then().statusCode(200).extract().asString();
        return Json.readList(body, FillResponse.class);
    }

    /** {@code GET /funds/balance} cash for the token's owner (expects 200). */
    protected BigDecimal balanceCash(String token) {
        String body = api(token).get("/funds/balance").then().statusCode(200).extract().asString();
        return Json.read(body, BalanceResponse.class).cash();
    }

    /** {@code POST /funds/deposit} (expects 200). */
    protected void deposit(String token, String amount) {
        api(token).body("{\"amount\":" + amount + "}").post("/funds/deposit").then().statusCode(200);
    }
}
