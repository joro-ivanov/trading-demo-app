package com.example.demo.portfolio.web;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.portfolio.domain.PositionEntity;
import com.example.demo.portfolio.domain.PositionRepository;
import com.example.demo.portfolio.service.RecentFillsService;

/**
 * Portfolio is per-user: positions are scoped to the authenticated subject ({@code authentication
 * .getName()} — the JWT subject), so one user never sees another's book. The holdings endpoint (the
 * short-sell guard's lookup) is scoped the same way, against the token order-service mints for the
 * order's owner.
 */
@RestController
@RequestMapping("/portfolio")
public class PortfolioController {

    private final PositionRepository repository;
    private final RecentFillsService recentFills;

    public PortfolioController(PositionRepository repository, RecentFillsService recentFills) {
        this.repository = repository;
        this.recentFills = recentFills;
    }

    @GetMapping
    public List<PositionResponse> positions(Authentication authentication) {
        return repository.findByOwner(authentication.getName()).stream().map(PositionResponse::from).toList();
    }

    @GetMapping("/holdings/{symbol}")
    public HoldingResponse holding(@PathVariable String symbol, Authentication authentication) {
        BigDecimal quantity = repository.findByOwnerAndSymbol(authentication.getName(), symbol)
                .map(PositionEntity::getQuantity)
                .orElse(BigDecimal.ZERO);
        return new HoldingResponse(symbol, quantity);
    }

    @GetMapping("/fills")
    public List<FillResponse> recentFills(Authentication authentication) {
        return recentFills.recentFills(authentication.getName()).stream().map(FillResponse::from).toList();
    }
}
