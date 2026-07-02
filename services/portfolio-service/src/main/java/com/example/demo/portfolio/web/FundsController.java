package com.example.demo.portfolio.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.portfolio.service.FundsService;
import com.example.demo.portfolio.web.FundsDtos.BalanceResponse;
import com.example.demo.portfolio.web.FundsDtos.FundsRequest;

import jakarta.validation.Valid;

/**
 * Per-user cash: deposit, withdraw, and read the balance. Scoped to the authenticated subject
 * ({@code authentication.getName()}), so a user only ever moves their own money. order-service reads
 * {@code GET /funds/balance} (with a token minted for the order's owner) for the pre-trade funds guard.
 */
@RestController
@RequestMapping("/funds")
public class FundsController {

    private final FundsService funds;

    public FundsController(FundsService funds) {
        this.funds = funds;
    }

    @GetMapping("/balance")
    public BalanceResponse balance(Authentication authentication) {
        return new BalanceResponse(funds.balance(authentication.getName()).amount());
    }

    @PostMapping("/deposit")
    public BalanceResponse deposit(@Valid @RequestBody FundsRequest request, Authentication authentication) {
        return new BalanceResponse(funds.deposit(authentication.getName(), request.amount()).amount());
    }

    @PostMapping("/withdraw")
    public BalanceResponse withdraw(@Valid @RequestBody FundsRequest request, Authentication authentication) {
        return new BalanceResponse(funds.withdraw(authentication.getName(), request.amount()).amount());
    }
}
