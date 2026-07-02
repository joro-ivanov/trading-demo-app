package com.example.demo.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.demo.common.json.Json;
import com.example.demo.portfolio.domain.FillEntity;
import com.example.demo.portfolio.domain.FillRepository;
import com.example.demo.portfolio.domain.PositionRepository;
import com.example.demo.portfolio.web.FundsDtos.BalanceResponse;
import com.example.demo.portfolio.web.FillResponse;
import com.example.demo.portfolio.web.PositionResponse;

/**
 * Per-service test (docs/DESIGN.md §7.2): real Postgres + cp-kafka. Produces synthetic fills/ticks
 * directly to Kafka and asserts the position arithmetic served by {@code GET /portfolio}. Each test
 * uses a distinct symbol because positions accumulate in the shared context. Covers the BUY
 * weighted-average, the SELL-side rules (avg unchanged, short going negative — there is no
 * short-sell guard), and P&L direction (BVA/EP set reviewed with the grizzled-trader).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PortfolioServiceIT {

    private static final String SECRET = "demo-secret-demo-secret-demo-secret-1234";
    private static final String TOKEN = com.example.demo.common.security.Jwt.issue("tester", SECRET, 3600);

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PositionRepository positionRepository;

    @Autowired
    FillRepository fillRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Test
    void buildsPositionAndPnlFromFillThenPrice() {
        Producer<String, String> producer = newProducer();
        sendFill(producer, "AAPL", "BUY", "10", "190");
        producer.flush();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            PositionResponse position = positionFor("AAPL");
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo("10");
                softly.assertThat(position.avgPrice()).isEqualByComparingTo("190");
            });
        });

        sendTick(producer, "AAPL", "195");
        producer.flush();
        producer.close();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            PositionResponse position = positionFor("AAPL");
            assertSoftly(softly -> {
                softly.assertThat(position.marketPrice()).isEqualByComparingTo("195");
                softly.assertThat(position.unrealizedPnl()).isEqualByComparingTo("50");
            });
        });
    }

    // Per-user isolation: the same symbol held by two owners is two separate books (not one shared
    // total). If the portfolio were global, both would see 14.
    @Test
    void positionsAreScopedToTheOwner() {
        Producer<String, String> producer = newProducer();
        sendFillAs(producer, "alice", "ISOL", "BUY", "5", "100");
        sendFillAs(producer, "bob", "ISOL", "BUY", "9", "100");
        producer.flush();
        producer.close();

        String aliceToken = com.example.demo.common.security.Jwt.issue("alice", SECRET, 3600);
        String bobToken = com.example.demo.common.security.Jwt.issue("bob", SECRET, 3600);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor(aliceToken, "ISOL").quantity()).isEqualByComparingTo("5"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor(bobToken, "ISOL").quantity()).isEqualByComparingTo("9"));
    }

    @Test
    void sellReducesLongAndKeepsAveragePrice() {
        Producer<String, String> producer = newProducer();
        sendFill(producer, "MSFT", "BUY", "10", "190");
        producer.flush();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor("MSFT").quantity()).isEqualByComparingTo("10"));

        // Sell at a deliberately different price; the cost basis must NOT move on a reduction.
        sendFill(producer, "MSFT", "SELL", "4", "999");
        producer.flush();
        producer.close();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            PositionResponse position = positionFor("MSFT");
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo("6");
                softly.assertThat(position.avgPrice()).isEqualByComparingTo("190");
            });
        });
    }

    @Test
    void holdingsEndpointReportsHeldQuantity() {
        Producer<String, String> producer = newProducer();
        sendFill(producer, "HOLD", "BUY", "8", "190");
        producer.flush();
        producer.close();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor("HOLD").quantity()).isEqualByComparingTo("8"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            String body = mockMvc.perform(get("/portfolio/holdings/HOLD").header("Authorization", "Bearer " + TOKEN))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).contains("HOLD").contains("8.0000");
        });
    }

    @Test
    void holdingsEndpointReturnsZeroForUnknownSymbol() throws Exception {
        String body = mockMvc.perform(get("/portfolio/holdings/NOPE").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"quantity\":0");
    }

    // Portfolio applies fills verbatim — it has no guard of its own. The pre-trade short-sell guard
    // lives upstream in order-service (see OrderServiceIT); a raw oversell fill here still goes short.
    @Test
    void sellMoreThanHeldGoesNegativeWithNoShortGuard() {
        Producer<String, String> producer = newProducer();
        sendFill(producer, "GOOG", "BUY", "10", "190");
        producer.flush();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor("GOOG").quantity()).isEqualByComparingTo("10"));

        sendFill(producer, "GOOG", "SELL", "15", "190");
        producer.flush();
        producer.close();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            PositionResponse position = positionFor("GOOG");
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo("-5");
                softly.assertThat(position.avgPrice()).isEqualByComparingTo("190");
            });
        });
    }

    @Test
    void weightedAveragePriceAcrossBuysWithRounding() {
        Producer<String, String> producer = newProducer();
        sendFill(producer, "TSLA", "BUY", "10", "190");
        producer.flush();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor("TSLA").quantity()).isEqualByComparingTo("10"));

        // (10*190 + 5*205) / 15 = 2925 / 15 = 195.0000
        sendFill(producer, "TSLA", "BUY", "5", "205");
        producer.flush();
        producer.close();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            PositionResponse position = positionFor("TSLA");
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo("15");
                softly.assertThat(position.avgPrice()).isEqualByComparingTo("195");
            });
        });
    }

    @ParameterizedTest(name = "{0}: mark {1} on avg 190 -> P&L {2}")
    @CsvSource({"NFLX,195,50", "AMZN,180,-100", "META,190,0"})
    void unrealizedPnlReflectsMarkVersusAverage(String symbol, BigDecimal mark, BigDecimal expectedPnl) {
        Producer<String, String> producer = newProducer();
        sendFill(producer, symbol, "BUY", "10", "190");
        producer.flush();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor(symbol).quantity()).isEqualByComparingTo("10"));

        sendTick(producer, symbol, mark.toPlainString());
        producer.flush();
        producer.close();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            PositionResponse position = positionFor(symbol);
            assertSoftly(softly -> {
                softly.assertThat(position.marketPrice()).isEqualByComparingTo(mark);
                softly.assertThat(position.unrealizedPnl()).isEqualByComparingTo(expectedPnl);
            });
        });
    }

    // A single price tick marks the symbol for EVERY owner holding it (the per-user fan-out).
    @Test
    void priceTickMarksAllOwnersOfSymbol() {
        Producer<String, String> producer = newProducer();
        sendFillAs(producer, "alice", "MULTI", "BUY", "10", "100");
        sendFillAs(producer, "bob", "MULTI", "BUY", "10", "100");
        producer.flush();

        String aliceToken = com.example.demo.common.security.Jwt.issue("alice", SECRET, 3600);
        String bobToken = com.example.demo.common.security.Jwt.issue("bob", SECRET, 3600);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor(aliceToken, "MULTI").quantity()).isEqualByComparingTo("10"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor(bobToken, "MULTI").quantity()).isEqualByComparingTo("10"));

        sendTick(producer, "MULTI", "150");
        producer.flush();
        producer.close();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor(aliceToken, "MULTI").marketPrice()).isEqualByComparingTo("150"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor(bobToken, "MULTI").marketPrice()).isEqualByComparingTo("150"));
    }

    // A SHORT position's P&L is the mirror of a long: with avg 190 and qty -5, a mark ABOVE 190 is a
    // loss and a mark BELOW is a gain. This is the only path through (mark-avg)*qty with negative qty.
    @ParameterizedTest(name = "{0}: short avg 190, mark {1} -> P&L {2}")
    @CsvSource({"SHRTA,200,-50", "SHRTB,180,50", "SHRTC,190,0"})
    void shortPositionPnlReflectsMarkVersusAverage(String symbol, BigDecimal mark, BigDecimal expectedPnl) {
        Producer<String, String> producer = newProducer();
        sendFill(producer, symbol, "BUY", "10", "190");
        producer.flush();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor(symbol).quantity()).isEqualByComparingTo("10"));

        sendFill(producer, symbol, "SELL", "15", "190"); // flip to short -5
        producer.flush();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor(symbol).quantity()).isEqualByComparingTo("-5"));

        sendTick(producer, symbol, mark.toPlainString());
        producer.flush();
        producer.close();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor(symbol).unrealizedPnl()).isEqualByComparingTo(expectedPnl));
    }

    // Flipping long→short keeps the long's average (the SELL applier never recomputes avg).
    @Test
    void flipLongToShortKeepsTheLongAverage() {
        Producer<String, String> producer = newProducer();
        sendFill(producer, "FLIP", "BUY", "10", "190");
        producer.flush();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor("FLIP").quantity()).isEqualByComparingTo("10"));

        sendFill(producer, "FLIP", "SELL", "15", "50"); // sell through zero at a wild price
        producer.flush();
        producer.close();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            PositionResponse position = positionFor("FLIP");
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo("-5");
                softly.assertThat(position.avgPrice()).isEqualByComparingTo("190");
            });
        });
    }

    // Selling to flat then buying again resets the average to the new buy (the rebuy weighted-averages
    // against quantity zero, so the stale 190 is weighted out).
    @Test
    void sellToFlatThenRebuyResetsTheAverage() {
        Producer<String, String> producer = newProducer();
        sendFill(producer, "REBY", "BUY", "10", "190");
        producer.flush();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionFor("REBY").quantity()).isEqualByComparingTo("10"));

        sendFill(producer, "REBY", "SELL", "10", "999"); // to flat
        producer.flush();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positionsFor(TOKEN)).extracting(PositionResponse::symbol).doesNotContain("REBY"));

        sendFill(producer, "REBY", "BUY", "5", "205"); // rebuy
        producer.flush();
        producer.close();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            PositionResponse position = positionFor("REBY");
            assertSoftly(softly -> {
                softly.assertThat(position.quantity()).isEqualByComparingTo("5");
                softly.assertThat(position.avgPrice()).isEqualByComparingTo("205");
            });
        });
    }

    @Test
    void fillsAreStoredInTheDatabase() {
        Producer<String, String> producer = newProducer();
        sendFillAs(producer, "fills-db", "NVDA", "BUY", "3", "125", "2026-07-01T10:20:30Z");
        producer.flush();
        producer.close();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            FillEntity fill = fillRepository.findById("o-NVDA-fills-db-BUY")
                    .orElseThrow(() -> new AssertionError("recent fill was not persisted"));
            assertSoftly(softly -> {
                softly.assertThat(fill.getOwner()).isEqualTo("fills-db");
                softly.assertThat(fill.getSymbol()).isEqualTo("NVDA");
                softly.assertThat(fill.getSide()).isEqualTo("BUY");
                softly.assertThat(fill.getQuantity()).isEqualByComparingTo("3");
                softly.assertThat(fill.getPrice()).isEqualByComparingTo("125");
                softly.assertThat(fill.getFilledAt()).isEqualTo(Instant.parse("2026-07-01T10:20:30Z"));
                softly.assertThat(fill.getSequenceId()).isEqualTo(1L);
            });
        });
    }

    @Test
    void closedPositionsAreRemovedFromTheDatabaseAndNotReturned() throws Exception {
        String token = tokenFor("closed-db");
        Producer<String, String> producer = newProducer();
        sendFillAs(producer, "closed-db", "CLOS", "BUY", "4", "100", "2026-07-01T10:21:30Z");
        producer.flush();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            PositionResponse response = positionFor(token, "CLOS");
            assertSoftly(softly -> {
                softly.assertThat(response.quantity()).isEqualByComparingTo("4");
                softly.assertThat(positionRepository.findByOwnerAndSymbol("closed-db", "CLOS"))
                        .isPresent()
                        .get()
                        .extracting(position -> position.getQuantity().stripTrailingZeros())
                        .isEqualTo(new BigDecimal("4").stripTrailingZeros());
            });
        });

        sendFillAs(producer, "closed-db", "CLOS", "SELL", "4", "101", "2026-07-01T10:22:30Z");
        producer.flush();
        producer.close();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<PositionResponse> positions = positionsFor(token);
            assertSoftly(softly -> {
                softly.assertThat(positions).extracting(PositionResponse::symbol).doesNotContain("CLOS");
                softly.assertThat(positionRepository.findByOwnerAndSymbol("closed-db", "CLOS")).isEmpty();
            });
        });
    }

    @Test
    void recentFillsArePersistedAndReturnedNewestFirst() throws Exception {
        String token = tokenFor("fills-view");
        Producer<String, String> producer = newProducer();
        sendFillAs(producer, "fills-view", "AAPL", "BUY", "2", "100", "2026-07-01T10:15:30Z", 1L);
        sendFillAs(producer, "fills-view", "MSFT", "SELL", "1", "200", "2026-07-01T10:16:30Z", 2L);
        producer.flush();
        producer.close();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<FillResponse> fills = recentFillsFor(token);
            assertSoftly(softly -> {
                softly.assertThat(fills).hasSize(2);
                softly.assertThat(fills.get(0).orderId()).isEqualTo("o-MSFT-fills-view-SELL");
                softly.assertThat(fills.get(0).filledAt()).isEqualTo(Instant.parse("2026-07-01T10:16:30Z"));
                softly.assertThat(fills.get(0).sequenceId()).isEqualTo(2L);
                softly.assertThat(fills.get(1).orderId()).isEqualTo("o-AAPL-fills-view-BUY");
                softly.assertThat(fills.get(1).filledAt()).isEqualTo(Instant.parse("2026-07-01T10:15:30Z"));
                softly.assertThat(fills.get(1).sequenceId()).isEqualTo(1L);
            });
        });
    }

    @Test
    void recentFillsWithTheSameTimestampAreReturnedInInsertionOrderDescending() throws Exception {
        String token = tokenFor("fills-tie");
        Producer<String, String> producer = newProducer();
        sendFillAs(producer, "fills-tie", "AAPL", "BUY", "1", "100", "2026-07-01T10:15:30Z", 1L);
        sendFillAs(producer, "fills-tie", "AAPL", "SELL", "1", "101", "2026-07-01T10:15:30Z", 2L);
        producer.flush();
        producer.close();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<FillResponse> fills = recentFillsFor(token);
            assertSoftly(softly -> {
                softly.assertThat(fills).hasSize(2);
                softly.assertThat(fills.get(0).orderId()).isEqualTo("o-AAPL-fills-tie-SELL");
                softly.assertThat(fills.get(0).filledAt()).isEqualTo(Instant.parse("2026-07-01T10:15:30Z"));
                softly.assertThat(fills.get(0).sequenceId()).isEqualTo(2L);
                softly.assertThat(fills.get(1).orderId()).isEqualTo("o-AAPL-fills-tie-BUY");
                softly.assertThat(fills.get(1).filledAt()).isEqualTo(Instant.parse("2026-07-01T10:15:30Z"));
                softly.assertThat(fills.get(1).sequenceId()).isEqualTo(1L);
            });
        });
    }

    // ---- funds (cash balance + deposit/withdraw) ----

    @Test
    void depositThenBalanceReflectsIt() throws Exception {
        String token = tokenFor("funds-dep");
        String body = mockMvc.perform(post("/funds/deposit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":500}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        BigDecimal balance = balanceCash(token);
        assertSoftly(softly -> {
            softly.assertThat(Json.read(body, BalanceResponse.class).cash()).isEqualByComparingTo("500");
            softly.assertThat(balance).isEqualByComparingTo("500");
        });
    }

    @Test
    void withdrawReducesTheBalance() throws Exception {
        String token = tokenFor("funds-wd");
        deposit(token, "500");
        String body = mockMvc.perform(post("/funds/withdraw").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":200}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(Json.read(body, BalanceResponse.class).cash()).isEqualByComparingTo("300");
    }

    @Test
    void withdrawingMoreThanAvailableIsRejectedWith422() throws Exception {
        String token = tokenFor("funds-over");
        mockMvc.perform(post("/funds/withdraw").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-5", "0"})
    void aNonPositiveAmountIsRejectedWith400(String amount) throws Exception {
        String token = tokenFor("funds-bad");
        mockMvc.perform(post("/funds/deposit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void seededDemoUserStartsWithCash() throws Exception {
        String demo = tokenFor("demo");
        assertThat(balanceCash(demo)).isEqualByComparingTo("1000000");
    }

    @Test
    void cashIsScopedPerUser() throws Exception {
        String a = tokenFor("scopeA");
        String b = tokenFor("scopeB");
        deposit(a, "700");
        BigDecimal balanceA = balanceCash(a);
        BigDecimal balanceB = balanceCash(b);
        assertSoftly(softly -> {
            softly.assertThat(balanceA).isEqualByComparingTo("700");
            softly.assertThat(balanceB).isEqualByComparingTo("0");
        });
    }

    @Test
    void aBuyFillSpendsCash() throws Exception {
        String token = tokenFor("fillbuy");
        deposit(token, "1000");
        Producer<String, String> producer = newProducer();
        sendFillAs(producer, "fillbuy", "FBUY", "BUY", "2", "100");
        producer.close();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(balanceCash(token)).isEqualByComparingTo("800"));
    }

    @Test
    void aSellFillAddsCash() throws Exception {
        String token = tokenFor("fillsell");
        deposit(token, "1000");
        Producer<String, String> producer = newProducer();
        sendFillAs(producer, "fillsell", "FSELL", "SELL", "3", "50");
        producer.close();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(balanceCash(token)).isEqualByComparingTo("1150"));
    }

    private static String tokenFor(String subject) {
        return com.example.demo.common.security.Jwt.issue(subject, SECRET, 3600);
    }

    private void deposit(String token, String amount) throws Exception {
        mockMvc.perform(post("/funds/deposit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    private BigDecimal balanceCash(String token) throws Exception {
        String body = mockMvc.perform(get("/funds/balance").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Json.read(body, BalanceResponse.class).cash();
    }

    private PositionResponse positionFor(String symbol) throws Exception {
        return positionFor(TOKEN, symbol);
    }

    private List<PositionResponse> positionsFor(String token) throws Exception {
        String body = mockMvc.perform(get("/portfolio").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Json.readList(body, PositionResponse.class);
    }

    private PositionResponse positionFor(String token, String symbol) throws Exception {
        List<PositionResponse> positions = positionsFor(token);
        return positions.stream()
                .filter(position -> position.symbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no position for " + symbol));
    }

    private List<FillResponse> recentFillsFor(String token) throws Exception {
        String body = mockMvc.perform(get("/portfolio/fills").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Json.readList(body, FillResponse.class);
    }

    private static void sendFill(Producer<String, String> producer, String symbol, String side,
                                 String quantity, String price) {
        sendFillAs(producer, "tester", symbol, side, quantity, price, "2026-07-01T10:00:00Z", 1L);
    }

    private static void sendFillAs(Producer<String, String> producer, String owner, String symbol,
                                   String side, String quantity, String price) {
        sendFillAs(producer, owner, symbol, side, quantity, price, "2026-07-01T10:00:00Z", 1L);
    }

    private static void sendFillAs(Producer<String, String> producer, String owner, String symbol,
                                   String side, String quantity, String price, String filledAt) {
        sendFillAs(producer, owner, symbol, side, quantity, price, filledAt, 1L);
    }

    private static void sendFillAs(Producer<String, String> producer, String owner, String symbol,
                                   String side, String quantity, String price, String filledAt, Long sequenceId) {
        producer.send(new ProducerRecord<>("fills", symbol,
                "{\"orderId\":\"o-" + symbol + "-" + owner + "-" + side + "\",\"symbol\":\"" + symbol
                        + "\",\"side\":\"" + side + "\",\"quantity\":" + quantity + ",\"price\":" + price
                        + ",\"owner\":\"" + owner + "\",\"filledAt\":\"" + filledAt + "\",\"sequenceId\":"
                        + sequenceId + "}"));
    }

    private static void sendTick(Producer<String, String> producer, String symbol, String price) {
        producer.send(new ProducerRecord<>("price.ticks", symbol,
                "{\"symbol\":\"" + symbol + "\",\"price\":" + price + "}"));
    }

    private static Producer<String, String> newProducer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(props);
    }
}
