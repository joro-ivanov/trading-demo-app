package com.example.demo.order.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.order.domain.OrderEntity;
import com.example.demo.order.domain.OrderRepository;
import com.example.demo.order.domain.OrderStatus;
import com.example.demo.order.domain.OrderType;
import com.example.demo.order.domain.Side;
import com.example.demo.order.events.OrderEventPublisher;
import com.example.demo.order.exchange.ExchangeAck;
import com.example.demo.order.exchange.ExchangeClient;
import com.example.demo.order.marketdata.MarketPriceClient;
import com.example.demo.order.portfolio.PortfolioClient;
import com.example.demo.order.risk.RiskClient;
import com.example.demo.order.risk.RiskClient.RiskDecision;
import com.example.demo.order.risk.RiskProperties;
import com.example.demo.order.web.OrderResponse;
import com.example.demo.order.web.PlaceOrderRequest;

@Service
public class OrderService {

    private static final String ACCEPTED = "ACCEPTED";

    private final OrderRepository repository;
    private final ExchangeClient exchangeClient;
    private final OrderEventPublisher publisher;
    private final MarketHoursPolicy marketHours;
    private final PortfolioClient portfolioClient;
    private final MarketPriceClient marketPriceClient;
    private final RiskClient riskClient;
    private final RiskProperties riskProperties;
    private final Clock clock;

    public OrderService(OrderRepository repository, ExchangeClient exchangeClient,
                        OrderEventPublisher publisher, MarketHoursPolicy marketHours,
                        PortfolioClient portfolioClient, MarketPriceClient marketPriceClient,
                        RiskClient riskClient, RiskProperties riskProperties, Clock clock) {
        this.repository = repository;
        this.exchangeClient = exchangeClient;
        this.publisher = publisher;
        this.marketHours = marketHours;
        this.portfolioClient = portfolioClient;
        this.marketPriceClient = marketPriceClient;
        this.riskClient = riskClient;
        this.riskProperties = riskProperties;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse place(PlaceOrderRequest request, String owner) {
        if (!marketHours.isOpen(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Market is closed");
        }
        guardAgainstShortSell(request, owner);
        // Looked up once: the funds guard values a BUY with it, the risk check values a non-LIMIT order.
        Optional<BigDecimal> marketPrice = needsMarketPrice(request)
                ? marketPriceClient.latestPrice(request.symbol())
                : Optional.empty();
        guardAgainstInsufficientFunds(request, owner, marketPrice);
        guardAgainstRiskLimits(request, owner, marketPrice);

        OrderEntity order = new OrderEntity(
                UUID.randomUUID(),
                request.symbol(),
                request.side(),
                request.quantity(),
                request.typeOrDefault(),
                request.limitPrice(),
                request.stopPrice(),
                owner,
                OrderStatus.NEW,
                clock.instant());

        ExchangeAck ack = exchangeClient.route(order);
        order.setStatus(ACCEPTED.equals(ack.status()) ? OrderStatus.SUBMITTED : OrderStatus.REJECTED);

        repository.save(order);
        // A SUBMITTED order goes to the matcher, which resolves it to FILLED or PENDING. A REJECTED
        // order must never reach the matcher (no phantom fill).
        if (order.getStatus() == OrderStatus.SUBMITTED) {
            publisher.publishSubmitted(order);
        }
        return OrderResponse.from(order);
    }

    private void guardAgainstShortSell(PlaceOrderRequest request, String owner) {
        if (request.side() != Side.SELL) {
            return;
        }
        BigDecimal held = portfolioClient.heldQuantity(owner, request.symbol());
        if (request.quantity().compareTo(held) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Insufficient holdings to sell " + request.quantity() + " " + request.symbol()
                            + " (held " + held + ")");
        }
    }

    // A BUY must be affordable: estimated cost (quantity × current market price) ≤ the owner's cash.
    // When no price is cached the order can't be valued yet — let it through (it will rest until a
    // price exists), consistent with the matcher.
    private void guardAgainstInsufficientFunds(PlaceOrderRequest request, String owner,
                                               Optional<BigDecimal> marketPrice) {
        if (request.side() != Side.BUY || marketPrice.isEmpty()) {
            return;
        }
        BigDecimal cost = marketPrice.get().multiply(request.quantity());
        BigDecimal cash = portfolioClient.availableCash(owner);
        if (cost.compareTo(cash) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Insufficient funds to buy " + request.quantity() + " " + request.symbol()
                            + " (cost " + cost + ", available " + cash + ")");
        }
    }

    private boolean needsMarketPrice(PlaceOrderRequest request) {
        return request.side() == Side.BUY
                || (riskProperties.enabled() && request.typeOrDefault() != OrderType.LIMIT);
    }

    // The last pre-trade guard: risk-service applies the owner's notional and position limits. A LIMIT
    // order is valued at its limit price, anything else at the market price (null if none is known,
    // in which case risk-service skips the notional rule). A refusal is a 422 with risk-service's
    // reason; an unreachable risk-service is a 503 from the client (fail closed).
    private void guardAgainstRiskLimits(PlaceOrderRequest request, String owner, Optional<BigDecimal> marketPrice) {
        if (!riskProperties.enabled()) {
            return;
        }
        BigDecimal price = request.typeOrDefault() == OrderType.LIMIT
                ? request.limitPrice()
                : marketPrice.orElse(null);
        RiskDecision decision = riskClient.check(owner, request.symbol(), request.side(), request.quantity(), price);
        if (!decision.allowed()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, decision.reason());
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, String owner) {
        return OrderResponse.from(ownedOrder(id, owner));
    }

    @Transactional
    public OrderResponse cancel(UUID id, String owner) {
        OrderEntity order = ownedOrder(id, owner);
        if (order.getStatus() == OrderStatus.FILLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Filled orders cannot be cancelled: " + id);
        }
        order.setStatus(OrderStatus.CANCELLED);
        repository.save(order);
        return OrderResponse.from(order);
    }

    // Treat "not yours" exactly like "not found" so a non-owner cannot even probe for existence.
    private OrderEntity ownedOrder(UUID id, String owner) {
        return repository.findById(id)
                .filter(existing -> existing.getOwner().equals(owner))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> history(String owner) {
        return repository.findByOwner(owner).stream().map(OrderResponse::from).toList();
    }
}
