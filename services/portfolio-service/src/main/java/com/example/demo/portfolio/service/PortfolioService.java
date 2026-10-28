package com.example.demo.portfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.money.Money;
import com.example.demo.portfolio.domain.PositionEntity;
import com.example.demo.portfolio.domain.PositionRepository;
import com.example.demo.portfolio.events.FillEvent;
import com.example.demo.portfolio.events.PriceMessage;

/**
 * Maintains positions from fills and the latest mark price from ticks, using the fixed-point
 * {@link Money} type for all monetary arithmetic (docs/DESIGN.md §10) — never floating point.
 */
@Service
public class PortfolioService {

    private static final int SCALE = 4;

    private final PositionRepository repository;

    public PortfolioService(PositionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void applyFill(FillEvent fill) {
        PositionEntity position = repository.findByOwnerAndSymbol(fill.owner(), fill.symbol())
                .orElseGet(() -> new PositionEntity(fill.owner(), fill.symbol(), BigDecimal.ZERO, Money.ZERO, null));

        BigDecimal signedQty = "SELL".equals(fill.side()) ? fill.quantity().negate() : fill.quantity();
        BigDecimal newQty = position.getQuantity().add(signedQty);
        if (newQty.signum() == 0) {
            repository.delete(position);
            return;
        }

        if ("BUY".equals(fill.side())) {
            Money currentNotional = position.getAvgPrice().multiply(position.getQuantity());
            Money fillNotional = Money.of(fill.price()).multiply(fill.quantity());
            Money newAvg = currentNotional.add(fillNotional).divide(newQty);
            position.setAvgPrice(newAvg);
        }

        position.setQuantity(newQty.setScale(SCALE, RoundingMode.HALF_EVEN));
        repository.save(position);
    }

    @Transactional
    public void applyPrice(PriceMessage price) {
        // A tick marks the symbol for every owner that holds it, updating only market_price (see markToMarket).
        repository.markToMarket(price.symbol(), Money.of(price.price()));
    }
}
