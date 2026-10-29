package com.example.demo.portfolio.it.harness;

import static com.example.demo.testing.support.auth.TestJwt.token;
import static com.example.demo.testing.support.fixtures.TestUsers.TESTER;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.demo.common.json.Json;
import com.example.demo.portfolio.web.FillResponse;
import com.example.demo.portfolio.web.FundsDtos.BalanceResponse;
import com.example.demo.portfolio.web.FundsDtos.FundsRequest;
import com.example.demo.portfolio.web.PositionResponse;
import com.example.demo.testing.support.containers.TestContainers;
import com.example.demo.testing.support.it.ServiceITBase;
import com.example.demo.testing.support.kafka.KafkaProbe;

/**
 * Shared harness for the portfolio-service integration tests (docs/DESIGN.md §7.2): the real service
 * on a random port ({@link ServiceITBase}) against real Postgres + cp-kafka (Testcontainers). Inbound
 * fills/ticks are produced with {@link PortfolioEvents}. Containers are singletons started once per JVM,
 * so every {@code *IT} class shares them and the cached Spring context — which is why each test uses its
 * own symbol and/or owner (positions accumulate).
 */
public abstract class PortfolioITBase extends ServiceITBase {

    /** A token for {@code TESTER}, the owner of fills sent without an explicit owner. */
    protected static final String TOKEN = token(TESTER);
    /** Fill sides as the {@code fills} topic carries them. */
    protected static final String BUY = "BUY";
    protected static final String SELL = "SELL";
    protected static final String DEPOSIT_PATH = "/funds/deposit";
    protected static final String WITHDRAW_PATH = "/funds/withdraw";
    protected static final String HOLDINGS_PATH = "/portfolio/holdings/{symbol}";

    static final PostgreSQLContainer POSTGRES = TestContainers.postgres();
    static final ConfluentKafkaContainer KAFKA = TestContainers.kafka();

    protected static final PortfolioEvents EVENTS;

    static {
        POSTGRES.start();
        KAFKA.start();
        EVENTS = new PortfolioEvents(new KafkaProbe(KAFKA.getBootstrapServers()));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestContainers.datasource(registry, POSTGRES);
        TestContainers.kafka(registry, KAFKA);
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

    /** {@link #position(String, String)} for {@code TESTER}. */
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
        return api(token).get("/funds/balance").then().statusCode(200).extract().as(BalanceResponse.class).cash();
    }

    /** The request of a deposit or withdrawal. */
    protected static FundsRequest amount(String amount) {
        return new FundsRequest(new BigDecimal(amount));
    }

    /** {@code POST /funds/deposit} (expects 200). */
    protected void deposit(String token, String amount) {
        api(token).body(amount(amount)).post(DEPOSIT_PATH).then().statusCode(200);
    }
}
