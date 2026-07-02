package com.example.demo.e2e.ui;

import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.model.Fill;
import com.example.demo.e2e.framework.model.Login;
import com.example.demo.e2e.framework.model.OrderResponse;
import com.example.demo.e2e.framework.model.Side;
import com.example.demo.e2e.framework.model.TradingSymbols;
import com.example.demo.e2e.framework.pages.DashboardPage;
import com.example.demo.e2e.framework.service.auth.AuthService;
import com.example.demo.e2e.framework.service.funds.FundsService;
import com.example.demo.e2e.framework.service.order.OrdersService;
import com.example.demo.e2e.framework.service.portfolio.PortfolioService;
import com.example.demo.e2e.harness.PlaywrightLifecycle;
import com.example.demo.e2e.harness.RequireRunningBackend;
import com.example.demo.e2e.harness.RunDuringMarketHours;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

@Tag("ui")
@ExtendWith(RequireRunningBackend.class)
@ExtendWith(PlaywrightLifecycle.class)
class LiveFillsTest extends BaseUiTest {

    private static final DateTimeFormatter DASHBOARD_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    @RunDuringMarketHours
    @Test
    void liveFillsPanelShowsOnlyTheLoggedInUsersOwnFills() {
        String uiUsername = uniqueUsername("fillsui");
        String apiUsername = uniqueUsername("fillsapi");
        ApiResponse uiRegistrationResponse = AuthService.register(
                uiUsername, emailFor(uiUsername), REGISTERED_USER_PASSWORD);
        ApiResponse apiRegistrationResponse = AuthService.register(
                apiUsername, emailFor(apiUsername), REGISTERED_USER_PASSWORD);
        Login uiUser = uiRegistrationResponse.as(Login.class);
        Login apiUser = apiRegistrationResponse.as(Login.class);
        FundsService.deposit(uiUser.token(), new BigDecimal("10000"));
        FundsService.deposit(apiUser.token(), new BigDecimal("10000"));

        Allure.step("Log in as the UI user and start with an empty fills panel");
        DashboardPage dashboard = openLoginPage().login(uiUsername, REGISTERED_USER_PASSWORD);
        assertThat(dashboard.fillCount()).isZero();

        Allure.step("Another user trades via the API; their fill must not appear in this dashboard");
        ApiResponse apiOrderResponse = OrdersService.placeMarketOrder(
                apiUser.token(), TradingSymbols.AAPL, Side.BUY, BigDecimal.ONE);
        OrderResponse apiOrder = apiOrderResponse.as(OrderResponse.class);
        assertThat(apiOrderResponse.statusCode()).isEqualTo(201);
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            List<Fill> apiFills = PortfolioService.recentFills(apiUser.token()).asList(Fill.class);
            assertSoftly(softly -> {
                softly.assertThat(apiFills).hasSize(1);
                softly.assertThat(apiFills.getFirst().orderId()).isEqualTo(apiOrder.id());
            });
        });
        await().during(STABILITY_TIMEOUT)
                .atMost(STABILITY_TIMEOUT.plusSeconds(1))
                .untilAsserted(() -> assertThat(dashboard.fillCount()).isZero());

        Allure.step("The logged-in user trades via the UI and sees only their own fill");
        dashboard.placeOrder(TradingSymbols.AAPL, Side.BUY, 1);
        dashboard.awaitFillCount(1);
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            List<Fill> uiFills = PortfolioService.recentFills(uiUser.token()).asList(Fill.class);
            assertThat(uiFills).hasSize(1);
            Fill latestFill = uiFills.getFirst();
            assertThat(dashboard.firstFillText()).isEqualTo(
                    dashboardFillText(latestFill));
        });

        Allure.step("Persisted fills stay owner-scoped for both users");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            List<Fill> uiFills = PortfolioService.recentFills(uiUser.token()).asList(Fill.class);
            List<Fill> apiFills = PortfolioService.recentFills(apiUser.token()).asList(Fill.class);
            assertSoftly(softly -> {
                softly.assertThat(uiFills).hasSize(1);
                softly.assertThat(uiFills.getFirst().symbol()).isEqualTo(TradingSymbols.AAPL);
                softly.assertThat(uiFills.getFirst().side()).isEqualTo(Side.BUY);
                softly.assertThat(apiFills).hasSize(1);
                softly.assertThat(apiFills.getFirst().orderId()).isEqualTo(apiOrder.id());
            });
        });
    }

    private String dashboardFillText(Fill fill) {
        return "%s UTC %s %s %s @ %s".formatted(
                DASHBOARD_TIMESTAMP.format(fill.filledAt()),
                fill.side(),
                fill.quantity().stripTrailingZeros().toPlainString(),
                fill.symbol(),
                fill.price().stripTrailingZeros().toPlainString());
    }
}
