package com.example.demo.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.common.money.Money;
import com.example.demo.portfolio.domain.BalanceEntity;
import com.example.demo.portfolio.domain.BalanceRepository;
import com.example.demo.portfolio.events.FillEvent;

/** Fast unit coverage of the cash arithmetic and the overdraw guard (repository mocked). */
@ExtendWith(MockitoExtension.class)
class FundsServiceTest {

    private static final String OWNER = "alice";
    private static final String OWNER_WITHOUT_BALANCE = "nobody";
    private static final String STARTING_CASH = "100";
    private static final BigDecimal DEPOSIT = new BigDecimal("50");
    private static final String CASH_AFTER_DEPOSIT = "150";
    private static final BigDecimal WITHDRAWAL = new BigDecimal("30");
    private static final String CASH_AFTER_WITHDRAWAL = "70";
    private static final String LOW_CASH = "20";
    private static final BigDecimal OVERDRAWING_WITHDRAWAL = new BigDecimal("50");
    private static final String INSUFFICIENT_FUNDS = "Insufficient funds";

    // Fill settlement starts from this balance.
    private static final String SETTLEMENT_CASH = "1000";
    private static final String ORDER_ID = "o1";
    private static final String SYMBOL = "AAPL";
    private static final String BUY = "BUY";
    private static final String SELL = "SELL";
    private static final BigDecimal BUY_QUANTITY = new BigDecimal("2");
    private static final BigDecimal BUY_PRICE = new BigDecimal("100");
    private static final String CASH_AFTER_BUY = "800";
    private static final BigDecimal SELL_QUANTITY = new BigDecimal("3");
    private static final BigDecimal SELL_PRICE = new BigDecimal("50");
    private static final String CASH_AFTER_SELL = "1150";
    private static final Instant FILLED_AT = Instant.parse("2026-07-01T10:00:00Z");
    private static final long SEQUENCE_ID = 1L;

    @Mock
    BalanceRepository repository;

    @InjectMocks
    FundsService funds;

    @BeforeEach
    void echoSaves() {
        lenient().when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void balanceIsZeroWhenNoRowExists() {
        assertThat(funds.balance(OWNER_WITHOUT_BALANCE)).isEqualTo(Money.ZERO);
    }

    @Test
    void depositAddsToTheBalance() {
        givenBalance(OWNER, STARTING_CASH);
        assertThat(funds.deposit(OWNER, DEPOSIT)).isEqualTo(Money.of(CASH_AFTER_DEPOSIT));
    }

    @Test
    void withdrawSubtractsFromTheBalance() {
        givenBalance(OWNER, STARTING_CASH);
        assertThat(funds.withdraw(OWNER, WITHDRAWAL)).isEqualTo(Money.of(CASH_AFTER_WITHDRAWAL));
    }

    @Test
    void withdrawingMoreThanTheBalanceIsRejected() {
        givenBalance(OWNER, LOW_CASH);
        assertThatThrownBy(() -> funds.withdraw(OWNER, OVERDRAWING_WITHDRAWAL))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(INSUFFICIENT_FUNDS);
    }

    @Test
    void aBuyFillSpendsCash() {
        BalanceEntity balance = givenBalance(OWNER, SETTLEMENT_CASH);
        funds.applyFill(new FillEvent(ORDER_ID, SYMBOL, BUY, BUY_QUANTITY, BUY_PRICE, OWNER, FILLED_AT, SEQUENCE_ID));
        assertThat(balance.getCash()).isEqualTo(Money.of(CASH_AFTER_BUY));
    }

    @Test
    void aSellFillReceivesCash() {
        BalanceEntity balance = givenBalance(OWNER, SETTLEMENT_CASH);
        funds.applyFill(new FillEvent(ORDER_ID, SYMBOL, SELL, SELL_QUANTITY, SELL_PRICE, OWNER, FILLED_AT, SEQUENCE_ID));
        assertThat(balance.getCash()).isEqualTo(Money.of(CASH_AFTER_SELL));
    }

    private BalanceEntity givenBalance(String owner, String cash) {
        BalanceEntity balance = new BalanceEntity(owner, Money.of(cash));
        lenient().when(repository.findById(owner)).thenReturn(Optional.of(balance));
        return balance;
    }
}
