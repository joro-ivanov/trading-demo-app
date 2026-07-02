package com.example.demo.e2e.api;

import com.example.demo.e2e.framework.allure.TestLogArtifacts;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.model.Balance;
import com.example.demo.e2e.framework.model.ErrorResponse;
import com.example.demo.e2e.framework.model.Fill;
import com.example.demo.e2e.framework.model.OrderResponse;
import com.example.demo.e2e.framework.model.OrderStatus;
import com.example.demo.e2e.framework.model.OrderType;
import com.example.demo.e2e.framework.model.Price;
import com.example.demo.e2e.framework.model.Side;
import com.example.demo.e2e.framework.model.TradingSymbols;
import com.example.demo.e2e.framework.service.funds.FundsService;
import com.example.demo.e2e.framework.service.marketdata.MarketDataService;
import com.example.demo.e2e.framework.service.order.OrdersService;
import com.example.demo.e2e.framework.service.portfolio.PortfolioService;
import com.example.demo.e2e.harness.RequireRunningBackend;
import com.example.demo.e2e.harness.RunDuringMarketHours;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

/**
 * Funds (deposit/withdraw/balance) and the pre-trade funds guard, through the live stack. Each test
 * registers a fresh user so balances are deterministic across runs (new users start at zero).
 */
@Tag("api")
@ExtendWith(RequireRunningBackend.class)
@ExtendWith(TestLogArtifacts.class)
class FundsTest extends BaseApiTest {

    private static final Pattern INSUFFICIENT_FUNDS_FOR_AAPL =
            Pattern.compile("Insufficient funds to buy 1 AAPL \\(cost (?<cost>\\d+\\.\\d{4}), available 0\\.0000\\)");
    private static final BigDecimal LIVE_PRICE_TOLERANCE = new BigDecimal("1.0000");

    @Test
    void depositIncreasesTheBalance() {
        String unfundedToken = registerFreshUser("f").token();
        ApiResponse beforeBalanceResponse = FundsService.balance(unfundedToken);
        Balance beforeBalance = beforeBalanceResponse.as(Balance.class);
        assertSoftly(softly -> {
            softly.assertThat(beforeBalanceResponse.statusCode()).isEqualTo(200);
            softly.assertThat(beforeBalance.cash()).isEqualByComparingTo("0");
        });

        FundsService.deposit(unfundedToken, new BigDecimal("1000"));

        ApiResponse afterBalanceResponse = FundsService.balance(unfundedToken);
        Balance afterBalance = afterBalanceResponse.as(Balance.class);
        assertSoftly(softly -> {
            softly.assertThat(afterBalanceResponse.statusCode()).isEqualTo(200);
            softly.assertThat(afterBalance.cash()).isEqualByComparingTo("1000");
        });
    }

    @Test
    void withdrawDecreasesTheBalance() {
        String unfundedToken = registerFreshUser("f").token();
        FundsService.deposit(unfundedToken, new BigDecimal("1000"));

        ApiResponse withdrawalResponse = FundsService.withdraw(unfundedToken, new BigDecimal("400"));
        Balance after = withdrawalResponse.as(Balance.class);

        assertSoftly(softly -> {
            softly.assertThat(withdrawalResponse.statusCode()).isEqualTo(200);
            softly.assertThat(after.cash()).isEqualByComparingTo("600");
        });
    }

    @Test
    void overWithdrawingIsRejectedWith422() {
        String unfundedToken = registerFreshUser("f").token();
        FundsService.deposit(unfundedToken, new BigDecimal("100"));

        ApiResponse response = FundsService.withdraw(unfundedToken, new BigDecimal("500"));
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(422);
            softly.assertThat(problem.error()).isEqualTo("Unprocessable Content");
            softly.assertThat(problem.status()).isEqualTo(422);
            softly.assertThat(problem.message())
                    .isEqualTo("Insufficient funds: balance 100.0000, requested 500.0000");
            softly.assertThat(problem.path()).isEqualTo("/funds/withdraw");
        });
    }

    @Test
    void aNonPositiveDepositIsRejectedWith400() {
        String unfundedToken = registerFreshUser("f").token();
        ApiResponse response = FundsService.deposit(unfundedToken, new BigDecimal("-5"));
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(400);
            softly.assertThat(problem.error()).isEqualTo("Bad Request");
            softly.assertThat(problem.status()).isEqualTo(400);
            softly.assertThat(problem.message()).isEqualTo("Invalid request content.");
            softly.assertThat(problem.path()).isEqualTo("/funds/deposit");
        });
    }

    @RunDuringMarketHours
    @Test
    void anUnfundedBuyIsRejectedThenSucceedsAfterDeposit() {
        String unfundedToken = registerFreshUser("f").token();
        Allure.step("An unfunded BUY is rejected for insufficient funds");
        ApiResponse rejectedOrder = OrdersService.placeMarketOrder(unfundedToken, TradingSymbols.AAPL, Side.BUY, new BigDecimal("1"));
        ErrorResponse rejectedOrderProblem = rejectedOrder.as(ErrorResponse.class);
        Price currentPrice = MarketDataService.latestPrice(unfundedToken, TradingSymbols.AAPL).as(Price.class);
        BigDecimal rejectedCost = rejectedCost(rejectedOrderProblem);
        assertSoftly(softly -> {
            softly.assertThat(rejectedOrder.statusCode()).isEqualTo(422);
            softly.assertThat(rejectedOrderProblem.error()).isEqualTo("Unprocessable Content");
            softly.assertThat(rejectedOrderProblem.status()).isEqualTo(422);
            softly.assertThat(rejectedOrderProblem.message())
                    .matches(INSUFFICIENT_FUNDS_FOR_AAPL);
            softly.assertThat(rejectedCost).isCloseTo(currentPrice.price(), within(LIVE_PRICE_TOLERANCE));
            softly.assertThat(rejectedOrderProblem.path()).isEqualTo("/orders");
        });

        Allure.step("After depositing, the same BUY succeeds");
        FundsService.deposit(unfundedToken, new BigDecimal("100000"));
        ApiResponse acceptedOrder = OrdersService.placeMarketOrder(unfundedToken, TradingSymbols.AAPL, Side.BUY, new BigDecimal("1"));
        OrderResponse acceptedOrderView = acceptedOrder.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(acceptedOrder.statusCode()).isEqualTo(201);
            softly.assertThat(acceptedOrderView.id()).isNotBlank();
            softly.assertThat(acceptedOrderView.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(acceptedOrderView.side()).isEqualTo(Side.BUY);
            softly.assertThat(acceptedOrderView.quantity()).isEqualByComparingTo("1");
            softly.assertThat(acceptedOrderView.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(acceptedOrderView.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
    }

    private static BigDecimal rejectedCost(ErrorResponse problem) {
        return INSUFFICIENT_FUNDS_FOR_AAPL.matcher(problem.message()).results()
                .findFirst()
                .map(match -> new BigDecimal(match.group("cost")))
                .orElseThrow(() -> new AssertionError("Unexpected insufficient-funds message: " + problem.message()));
    }

    @RunDuringMarketHours
    @Test
    void aBuyDecreasesCashAndAClosingSellIncreasesIt() {
        BigDecimal beforeBuy = FundsService.balance(token).as(Balance.class).cash();

        Allure.step("Buying 1 AAPL decreases the cash balance");
        ApiResponse buyResponse = OrdersService.placeMarketOrder(token, TradingSymbols.AAPL, Side.BUY, BigDecimal.ONE);
        OrderResponse buyOrder = buyResponse.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(buyResponse.statusCode()).isEqualTo(201);
            softly.assertThat(buyOrder.id()).isNotBlank();
            softly.assertThat(buyOrder.side()).isEqualTo(Side.BUY);
        });

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            List<Fill> fills = PortfolioService.recentFills(token).asList(Fill.class);
            assertThat(fills).hasSize(1);
            Fill buyFill = fills.getFirst();
            BigDecimal expectedAfterBuy = beforeBuy.subtract(buyFill.price().multiply(buyFill.quantity()));
            assertThat(FundsService.balance(token).as(Balance.class).cash()).isEqualByComparingTo(expectedAfterBuy);
        });
        BigDecimal afterBuy = FundsService.balance(token).as(Balance.class).cash();

        Allure.step("Selling the same quantity increases the cash balance again");
        ApiResponse sellResponse = OrdersService.placeMarketOrder(token, TradingSymbols.AAPL, Side.SELL, BigDecimal.ONE);
        OrderResponse sellOrder = sellResponse.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(sellResponse.statusCode()).isEqualTo(201);
            softly.assertThat(sellOrder.id()).isNotBlank();
            softly.assertThat(sellOrder.side()).isEqualTo(Side.SELL);
        });

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            List<Fill> fills = PortfolioService.recentFills(token).asList(Fill.class);
            assertThat(fills).hasSize(2);
            Fill sellFill = fills.getFirst();
            BigDecimal expectedAfterSell = afterBuy.add(sellFill.price().multiply(sellFill.quantity()));
            assertThat(FundsService.balance(token).as(Balance.class).cash()).isEqualByComparingTo(expectedAfterSell);
        });
    }
}
