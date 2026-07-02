package com.example.demo.e2e.api;

import com.example.demo.common.security.Jwt;
import com.example.demo.e2e.framework.allure.TestLogArtifacts;
import com.example.demo.e2e.framework.http.ApiRequest;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.http.HttpMethod;
import com.example.demo.e2e.framework.model.ErrorResponse;
import com.example.demo.e2e.framework.model.OrderRequest;
import com.example.demo.e2e.framework.model.OrderResponse;
import com.example.demo.e2e.framework.model.OrderStatus;
import com.example.demo.e2e.framework.model.OrderType;
import com.example.demo.e2e.framework.model.Side;
import com.example.demo.e2e.framework.model.TradingSymbols;
import com.example.demo.e2e.framework.service.auth.AuthService;
import com.example.demo.e2e.framework.service.order.OrderHistoryService;
import com.example.demo.e2e.framework.service.order.OrdersService;
import com.example.demo.e2e.framework.support.OrderRequestBuilder;
import com.example.demo.e2e.harness.RunDuringMarketHours;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Security e2e (API): authentication of tokens and broken-access-control checks against the running
 * stack. Complements the existing expired-token / cross-user-read tests in {@link TradingTest}.
 * Token-bearing reads are not market-hours gated; the two tests that place real orders are.
 */
@Tag("api")
@ExtendWith(TestLogArtifacts.class)
class SecurityTest extends BaseApiTest {

    private static final String HISTORY = "/orders/history";

    // ── authentication ────────────────────────────────────────────────────────

    // A token with a valid shape but signed with the wrong secret must not be trusted.
    @Test
    void forgedTokenIsRejectedWith401() {
        String forgedToken = Jwt.issue(username, "wrong-wrong-wrong-wrong-wrong-wrong-1234", 300);
        Allure.step("A token signed with the wrong secret is rejected");
        ApiRequest forgedTokenRequest = ApiRequest.builder().method(HttpMethod.GET).path(HISTORY).token(forgedToken).build();
        ApiResponse response = OrderHistoryService.history(forgedTokenRequest);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(401);
            softly.assertThat(problem.error()).isEqualTo("Unauthorized");
            softly.assertThat(problem.status()).isEqualTo(401);
            softly.assertThat(problem.message()).isEqualTo("An error occurred while attempting to decode the Jwt: Signed JWT rejected: Invalid signature");
            softly.assertThat(problem.path()).isEqualTo(HISTORY);
        });
    }

    @ParameterizedTest(name = "malformed bearer token -> 401: {0}")
    @ValueSource(strings = {"not-a-jwt", "a.b.c", "header.payload.signature"})
    void malformedBearerTokenIsRejectedWith401(String token) {
        ApiRequest forgedTokenRequest = ApiRequest.builder().method(HttpMethod.GET).path(HISTORY).token(token).build();
        ApiResponse response = OrderHistoryService.history(forgedTokenRequest);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(401);
            softly.assertThat(problem.error()).isEqualTo("Unauthorized");
            softly.assertThat(problem.status()).isEqualTo(401);
            softly.assertThat(problem.message()).isEqualTo("An error occurred while attempting to decode the Jwt: Malformed token");
            softly.assertThat(problem.path()).isEqualTo(HISTORY);
        });
    }

    // A real token but without the "Bearer " scheme prefix is not accepted.
    @Test
    void authorizationHeaderWithoutBearerPrefixIsRejectedWith401() {
        String raw = token;
        Allure.step("A valid token sent without the 'Bearer ' prefix is rejected");
        ApiRequest forgedTokenRequest = ApiRequest.builder().method(HttpMethod.GET).path(HISTORY).header("Authorization", raw).build();
        ApiResponse response = OrderHistoryService.history(forgedTokenRequest);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(401);
            softly.assertThat(problem.error()).isEqualTo("Unauthorized");
            softly.assertThat(problem.status()).isEqualTo(401);
            softly.assertThat(problem.message()).isEqualTo("Bearer token is required");
            softly.assertThat(problem.path()).isEqualTo(HISTORY);
        });
    }

    @Test
    void missingTokenIsRejectedWith401() {
        Allure.step("A secured endpoint with no token is rejected");
        ApiRequest forgedTokenRequest = ApiRequest.builder().method(HttpMethod.GET).path(HISTORY).build();
        ApiResponse response = OrderHistoryService.history(forgedTokenRequest);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(401);
            softly.assertThat(problem.error()).isEqualTo("Unauthorized");
            softly.assertThat(problem.status()).isEqualTo(401);
            softly.assertThat(problem.message()).isEqualTo("Authentication token is required");
            softly.assertThat(problem.path()).isEqualTo(HISTORY);
        });
    }

    // Injection-style credentials must be treated as a normal failed login (401), never a 500 —
    // proving the lookup is parameterized, not string-concatenated.
    @ParameterizedTest(name = "injection login -> 401: {0}")
    @ValueSource(strings = {"' OR '1'='1", "admin'--", "'; DROP TABLE users;--"})
    void sqlInjectionStyleLoginIsRejectedNotErrored(String username) {
        ApiResponse response = AuthService.login(username, REGISTERED_USER_PASSWORD);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(401);
            softly.assertThat(problem.error()).isEqualTo("Unauthorized");
            softly.assertThat(problem.status()).isEqualTo(401);
            softly.assertThat(problem.message()).isEqualTo("Invalid credentials");
            softly.assertThat(problem.path()).isEqualTo("/auth/login");
        });
    }

    // ── broken access control ──────────────────────────────────────────────────

    // The order owner is the JWT subject (server-side); an "owner"/"username" field in the body is
    // ignored — you cannot place an order as someone else (mass-assignment / identity spoofing).
    @RunDuringMarketHours
    @Test
    void orderOwnerIsTheTokenSubjectNotTheRequestBody() {
        String aliceToken = token;
        String bobToken = registerFreshUserToken("u");
        Allure.step("Alice places an order whose body falsely claims owner=bob");
        OrderRequest aliceMarketOrderRequest = OrderRequestBuilder.anOrder().symbol(TradingSymbols.AAPL).side(Side.BUY).quantity(1).owner("bob").username("bob").build();
        ApiResponse placed = OrdersService.placeOrder(aliceToken, aliceMarketOrderRequest);
        OrderResponse placedOrder = placed.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(placed.statusCode()).isEqualTo(201);
            softly.assertThat(placedOrder.id()).isNotBlank();
            softly.assertThat(placedOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(placedOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(placedOrder.quantity()).isEqualByComparingTo("1");
            softly.assertThat(placedOrder.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(placedOrder.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = placedOrder.id();

        Allure.step("The order belongs to Alice (the token subject), not Bob");
        List<OrderResponse> aliceOrders = OrderHistoryService.history(aliceToken).asList(OrderResponse.class);
        List<OrderResponse> bobOrders = OrderHistoryService.history(bobToken).asList(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(aliceOrders).extracting(OrderResponse::id).containsExactly(orderId);
            softly.assertThat(bobOrders).isEmpty();
        });
    }

    // A user cannot cancel another user's order; "not yours" is a 404 (not 403) so existence is not
    // disclosed.
    @RunDuringMarketHours
    @Test
    void aUserCannotCancelAnotherUsersOrder() {
        String aliceToken = token;
        String bobToken = registerFreshUserToken("u");
        Allure.step("Alice places an order");
        ApiResponse placed = OrdersService.placeMarketOrder(aliceToken, TradingSymbols.AAPL, Side.BUY, new BigDecimal("1"));
        OrderResponse placedOrder = placed.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(placed.statusCode()).isEqualTo(201);
            softly.assertThat(placedOrder.id()).isNotBlank();
            softly.assertThat(placedOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(placedOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(placedOrder.quantity()).isEqualByComparingTo("1");
            softly.assertThat(placedOrder.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(placedOrder.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = placedOrder.id();

        Allure.step("Bob cannot cancel it — 404, not 403");
        ApiResponse cancelResponse = OrdersService.cancelOrder(bobToken, orderId);
        ErrorResponse cancelProblem = cancelResponse.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(cancelResponse.statusCode()).isEqualTo(404);
            softly.assertThat(cancelProblem.error()).isEqualTo("Not Found");
            softly.assertThat(cancelProblem.status()).isEqualTo(404);
            softly.assertThat(cancelProblem.message()).isEqualTo("Order not found: " + orderId);
            softly.assertThat(cancelProblem.path()).isEqualTo("/orders/" + orderId);
        });

        Allure.step("Alice still owns the order");
        List<OrderResponse> aliceOrders = OrderHistoryService.history(aliceToken).asList(OrderResponse.class);
        assertThat(aliceOrders).extracting(OrderResponse::id).containsExactly(orderId);
    }
}
