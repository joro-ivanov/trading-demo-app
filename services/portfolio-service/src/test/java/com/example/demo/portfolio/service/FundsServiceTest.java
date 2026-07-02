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
        assertThat(funds.balance("nobody")).isEqualTo(Money.ZERO);
    }

    @Test
    void depositAddsToTheBalance() {
        givenBalance("alice", "100");
        assertThat(funds.deposit("alice", new BigDecimal("50"))).isEqualTo(Money.of("150"));
    }

    @Test
    void withdrawSubtractsFromTheBalance() {
        givenBalance("alice", "100");
        assertThat(funds.withdraw("alice", new BigDecimal("30"))).isEqualTo(Money.of("70"));
    }

    @Test
    void withdrawingMoreThanTheBalanceIsRejected() {
        givenBalance("alice", "20");
        assertThatThrownBy(() -> funds.withdraw("alice", new BigDecimal("50")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void aBuyFillSpendsCash() {
        BalanceEntity balance = givenBalance("alice", "1000");
        funds.applyFill(new FillEvent(
                "o1", "AAPL", "BUY", new BigDecimal("2"), new BigDecimal("100"), "alice",
                Instant.parse("2026-07-01T10:00:00Z"), 1L));
        assertThat(balance.getCash()).isEqualTo(Money.of("800"));
    }

    @Test
    void aSellFillReceivesCash() {
        BalanceEntity balance = givenBalance("alice", "1000");
        funds.applyFill(new FillEvent(
                "o1", "AAPL", "SELL", new BigDecimal("3"), new BigDecimal("50"), "alice",
                Instant.parse("2026-07-01T10:00:00Z"), 1L));
        assertThat(balance.getCash()).isEqualTo(Money.of("1150"));
    }

    private BalanceEntity givenBalance(String owner, String cash) {
        BalanceEntity balance = new BalanceEntity(owner, Money.of(cash));
        lenient().when(repository.findById(owner)).thenReturn(Optional.of(balance));
        return balance;
    }
}
