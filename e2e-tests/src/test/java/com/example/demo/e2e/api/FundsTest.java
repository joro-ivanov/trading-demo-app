package com.example.demo.e2e.api;

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
import com.example.demo.e2e.framework.service.order.OrderHistoryService;
import com.example.demo.e2e.framework.service.order.OrdersService;
import com.example.demo.e2e.framework.service.portfolio.PortfolioService;
import com.example.demo.e2e.framework.support.ApiErrorMessages;
import com.example.demo.e2e.harness.RunDuringMarketHours;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.example.demo.e2e.framework.support.ApiErrorMessages.INVALID_REQUEST_CONTENT;
import static com.example.demo.e2e.framework.support.ApiPaths.FUNDS_DEPOSIT;
import static com.example.demo.e2e.framework.support.ApiPaths.FUNDS_WITHDRAW;
import static com.example.demo.e2e.framework.support.ApiPaths.ORDERS;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.BAD_REQUEST;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.BAD_REQUEST_REASON;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.CREATED;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.OK;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.UNPROCESSABLE_CONTENT;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.UNPROCESSABLE_CONTENT_REASON;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

/**
 * Funds (deposit/withdraw/balance) and the pre-trade funds guard, through the live stack. Each test
 * registers a fresh user so balances are deterministic across runs (new users start at zero).
 */
class FundsTest extends BaseApiTest {

    /** A fresh user's cash, as the funds guard reports it. */
    private static final BigDecimal NO_CASH = new BigDecimal("0.0000");
    private static final String FRESH_USER_PREFIX = "f";
    private static final BigDecimal DEPOSIT = new BigDecimal("1000");
    private static final BigDecimal WITHDRAWAL = new BigDecimal("400");
    private static final BigDecimal BALANCE_AFTER_WITHDRAWAL = new BigDecimal("600");
    private static final BigDecimal SMALL_DEPOSIT = new BigDecimal("100");
    private static final BigDecimal OVERDRAWING_WITHDRAWAL = new BigDecimal("500");
    private static final String OVERDRAW_MESSAGE = "Insufficient funds: balance 100.0000, requested 500.0000";
    private static final BigDecimal NEGATIVE_AMOUNT = new BigDecimal("-5");
    /** Enough cash to buy one share at any demo price. */
    private static final BigDecimal FUNDING_DEPOSIT = new BigDecimal("100000");
    /** The BUY fill and the SELL fill. */

    @Test
    void depositIncreasesTheBalance() {
        String unfundedToken = registerFreshUser(FRESH_USER_PREFIX).token();
        ApiResponse beforeBalanceResponse = FundsService.balance(unfundedToken);
        Balance beforeBalance = beforeBalanceResponse.as(Balance.class);
        assertSoftly(softly -> {
            softly.assertThat(beforeBalanceResponse.statusCode()).isEqualTo(OK);
            softly.assertThat(beforeBalance.cash()).isEqualByComparingTo(BigDecimal.ZERO);
        });

        FundsService.deposit(unfundedToken, DEPOSIT);

        ApiResponse afterBalanceResponse = FundsService.balance(unfundedToken);
        Balance afterBalance = afterBalanceResponse.as(Balance.class);
        assertSoftly(softly -> {
            softly.assertThat(afterBalanceResponse.statusCode()).isEqualTo(OK);
            softly.assertThat(afterBalance.cash()).isEqualByComparingTo(DEPOSIT);
        });
    }

    @Test
    void withdrawDecreasesTheBalance() {
        String unfundedToken = registerFreshUser(FRESH_USER_PREFIX).token();
        FundsService.deposit(unfundedToken, DEPOSIT);

        ApiResponse withdrawalResponse = FundsService.withdraw(unfundedToken, WITHDRAWAL);
        Balance after = withdrawalResponse.as(Balance.class);

        assertSoftly(softly -> {
            softly.assertThat(withdrawalResponse.statusCode()).isEqualTo(OK);
            softly.assertThat(after.cash()).isEqualByComparingTo(BALANCE_AFTER_WITHDRAWAL);
        });
    }

    @Test
    void overWithdrawingIsRejectedWith422() {
        String unfundedToken = registerFreshUser(FRESH_USER_PREFIX).token();
        FundsService.deposit(unfundedToken, SMALL_DEPOSIT);

        ApiResponse response = FundsService.withdraw(unfundedToken, OVERDRAWING_WITHDRAWAL);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(UNPROCESSABLE_CONTENT);
            softly.assertThat(problem.error()).isEqualTo(UNPROCESSABLE_CONTENT_REASON);
            softly.assertThat(problem.status()).isEqualTo(UNPROCESSABLE_CONTENT);
            softly.assertThat(problem.message())
                    .isEqualTo(OVERDRAW_MESSAGE);
            softly.assertThat(problem.path()).isEqualTo(FUNDS_WITHDRAW);
        });
    }

    @Test
    void aNonPositiveDepositIsRejectedWith400() {
        String unfundedToken = registerFreshUser(FRESH_USER_PREFIX).token();
        ApiResponse response = FundsService.deposit(unfundedToken, NEGATIVE_AMOUNT);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(BAD_REQUEST);
            softly.assertThat(problem.error()).isEqualTo(BAD_REQUEST_REASON);
            softly.assertThat(problem.status()).isEqualTo(BAD_REQUEST);
            softly.assertThat(problem.message()).isEqualTo(INVALID_REQUEST_CONTENT);
            softly.assertThat(problem.path()).isEqualTo(FUNDS_DEPOSIT);
        });
    }

    @RunDuringMarketHours
    @Test
    void anUnfundedBuyIsRejectedThenSucceedsAfterDeposit() {
        String unfundedToken = registerFreshUser(FRESH_USER_PREFIX).token();
        // The guard values the BUY at the live price (the demo feed random-walks once a second): the
        // price is read right before the order and the pair is retried until no tick lands in between,
        // so the whole message, cost included, is asserted exactly. A rejected order is not persisted.
        Allure.step("An unfunded BUY is rejected for insufficient funds, costed at the live price");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Price currentPrice = MarketDataService.latestPrice(unfundedToken, TradingSymbols.AAPL).as(Price.class);
            ApiResponse rejectedOrder = OrdersService.placeMarketOrder(unfundedToken, TradingSymbols.AAPL, Side.BUY, BigDecimal.ONE);
            ErrorResponse rejectedOrderProblem = rejectedOrder.as(ErrorResponse.class);
            String expectedMessage = ApiErrorMessages.insufficientFundsToBuy(
                    BigDecimal.ONE, TradingSymbols.AAPL, currentPrice.price().multiply(BigDecimal.ONE), NO_CASH);
            assertSoftly(softly -> {
                softly.assertThat(rejectedOrder.statusCode()).isEqualTo(UNPROCESSABLE_CONTENT);
                softly.assertThat(rejectedOrderProblem.error()).isEqualTo(UNPROCESSABLE_CONTENT_REASON);
                softly.assertThat(rejectedOrderProblem.status()).isEqualTo(UNPROCESSABLE_CONTENT);
                softly.assertThat(rejectedOrderProblem.message()).isEqualTo(expectedMessage);
                softly.assertThat(rejectedOrderProblem.path()).isEqualTo(ORDERS);
            });
        });

        Allure.step("After depositing, the same BUY succeeds");
        FundsService.deposit(unfundedToken, FUNDING_DEPOSIT);
        ApiResponse acceptedOrder = OrdersService.placeMarketOrder(unfundedToken, TradingSymbols.AAPL, Side.BUY, BigDecimal.ONE);
        OrderResponse acceptedOrderView = acceptedOrder.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(acceptedOrder.statusCode()).isEqualTo(CREATED);
            softly.assertThat(OrderHistoryService.history(unfundedToken).asList(OrderResponse.class))
                    .extracting(OrderResponse::id).containsExactly(acceptedOrderView.id());
            softly.assertThat(acceptedOrderView.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(acceptedOrderView.side()).isEqualTo(Side.BUY);
            softly.assertThat(acceptedOrderView.quantity()).isEqualByComparingTo(BigDecimal.ONE);
            softly.assertThat(acceptedOrderView.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(acceptedOrderView.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
    }

    @RunDuringMarketHours
    @Test
    void aBuyDecreasesCashAndAClosingSellIncreasesIt() {
        BigDecimal beforeBuy = FundsService.balance(token).as(Balance.class).cash();

        Allure.step("Buying 1 AAPL decreases the cash balance");
        ApiResponse buyResponse = OrdersService.placeMarketOrder(token, TradingSymbols.AAPL, Side.BUY, BigDecimal.ONE);
        OrderResponse buyOrder = buyResponse.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(buyResponse.statusCode()).isEqualTo(CREATED);
            softly.assertThat(OrderHistoryService.history(token).asList(OrderResponse.class))

                    .extracting(OrderResponse::id).containsExactly(buyOrder.id());
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
            softly.assertThat(sellResponse.statusCode()).isEqualTo(CREATED);
            softly.assertThat(OrderHistoryService.history(token).asList(OrderResponse.class))

                    .extracting(OrderResponse::id).containsExactlyInAnyOrder(buyOrder.id(), sellOrder.id());
            softly.assertThat(sellOrder.side()).isEqualTo(Side.SELL);
        });

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            List<Fill> fills = PortfolioService.recentFills(token).asList(Fill.class);
            assertThat(fills).hasSize(BUY_AND_SELL_FILLS);
            Fill sellFill = fills.getFirst();
            BigDecimal expectedAfterSell = afterBuy.add(sellFill.price().multiply(sellFill.quantity()));
            assertThat(FundsService.balance(token).as(Balance.class).cash()).isEqualByComparingTo(expectedAfterSell);
        });
    }
}
