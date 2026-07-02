package com.example.demo.portfolio.service;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.common.money.Money;
import com.example.demo.portfolio.domain.BalanceEntity;
import com.example.demo.portfolio.domain.BalanceRepository;
import com.example.demo.portfolio.events.FillEvent;

/**
 * Per-user cash. Deposits/withdrawals adjust the balance directly; fills move cash as trades settle
 * (a BUY spends, a SELL receives). A withdrawal cannot overdraw the account.
 */
@Service
public class FundsService {

    private final BalanceRepository repository;

    public FundsService(BalanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Money balance(String owner) {
        return repository.findById(owner).map(BalanceEntity::getCash).orElse(Money.ZERO);
    }

    @Transactional
    public Money deposit(String owner, BigDecimal amount) {
        BalanceEntity balance = balanceOf(owner);
        balance.setCash(balance.getCash().add(Money.of(amount)));
        return repository.save(balance).getCash();
    }

    @Transactional
    public Money withdraw(String owner, BigDecimal amount) {
        BalanceEntity balance = balanceOf(owner);
        Money requested = Money.of(amount);
        if (balance.getCash().compareTo(requested) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Insufficient funds: balance " + balance.getCash() + ", requested " + requested);
        }
        balance.setCash(balance.getCash().subtract(requested));
        return repository.save(balance).getCash();
    }

    /** A BUY fill spends cash; a SELL fill receives it (the cash leg of settling a trade). */
    @Transactional
    public void applyFill(FillEvent fill) {
        BalanceEntity balance = balanceOf(fill.owner());
        Money proceeds = Money.of(fill.price()).multiply(fill.quantity());
        Money updated = "SELL".equals(fill.side())
                ? balance.getCash().add(proceeds)
                : balance.getCash().subtract(proceeds);
        balance.setCash(updated);
        repository.save(balance);
    }

    private BalanceEntity balanceOf(String owner) {
        return repository.findById(owner).orElseGet(() -> new BalanceEntity(owner, Money.ZERO));
    }
}
