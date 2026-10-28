package com.example.demo.watchlist.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.common.http.Downstream;
import com.example.demo.watchlist.client.MarketDataClient;
import com.example.demo.watchlist.client.MarketDataUnavailableException;
import com.example.demo.watchlist.config.WatchlistLimits;
import com.example.demo.watchlist.domain.WatchlistEntity;
import com.example.demo.watchlist.domain.WatchlistRepository;
import com.example.demo.watchlist.domain.WatchlistSymbol;
import com.example.demo.watchlist.web.WatchlistDtos.AddedSymbolResponse;
import com.example.demo.watchlist.web.WatchlistDtos.SymbolResponse;
import com.example.demo.watchlist.web.WatchlistDtos.WatchlistResponse;
import com.example.demo.watchlist.web.WatchlistDtos.WatchlistSummaryResponse;

/**
 * Watchlist use cases. Database work runs in short explicit transactions; calls to market-data run
 * outside them, so a slow downstream never holds a connection or a lock.
 */
@Service
public class WatchlistService {

    private final WatchlistRepository repository;
    private final MarketDataClient marketData;
    private final WatchlistLimits limits;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public WatchlistService(WatchlistRepository repository, MarketDataClient marketData, WatchlistLimits limits,
                            TransactionTemplate transactions, Clock clock) {
        this.repository = repository;
        this.marketData = marketData;
        this.limits = limits;
        this.transactions = transactions;
        this.clock = clock;
    }

    public WatchlistResponse create(String owner, String rawName) {
        String name = rawName.strip();
        try {
            WatchlistEntity created = transactions.execute(status -> {
                if (repository.existsByOwnerAndName(owner, name)) {
                    throw duplicateName(name);
                }
                if (repository.countByOwner(owner) >= limits.maxPerOwner()) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Watchlist limit reached: at most " + limits.maxPerOwner() + " per user");
                }
                return repository.saveAndFlush(new WatchlistEntity(UUID.randomUUID(), owner, name, clock.instant()));
            });
            return new WatchlistResponse(created.getId(), created.getName(), created.getCreatedAt(), true, List.of());
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            throw duplicateName(name);
        }
    }

    public List<WatchlistSummaryResponse> list(String owner) {
        return repository.findByOwnerOrderByCreatedAtAscNameAsc(owner).stream()
                .map(watchlist -> new WatchlistSummaryResponse(watchlist.getId(), watchlist.getName(),
                        watchlist.symbolCount(), watchlist.getCreatedAt()))
                .toList();
    }

    /** The watchlist with the latest prices (one batch call); degrades to no prices if market-data is down. */
    public WatchlistResponse get(UUID id, String owner) {
        WatchlistEntity watchlist = find(id, owner);
        List<WatchlistSymbol> symbols = watchlist.getSymbols();
        Map<String, BigDecimal> prices;
        boolean pricesAvailable;
        try {
            prices = marketData.latestPrices(symbols.stream().map(WatchlistSymbol::symbol).toList(), owner);
            pricesAvailable = true;
        } catch (MarketDataUnavailableException unavailable) {
            prices = Map.of();
            pricesAvailable = false;
        }
        Map<String, BigDecimal> priceBySymbol = prices;
        List<SymbolResponse> enriched = symbols.stream()
                .map(entry -> new SymbolResponse(entry.symbol(), entry.addedAt(), priceBySymbol.get(entry.symbol())))
                .toList();
        return new WatchlistResponse(watchlist.getId(), watchlist.getName(), watchlist.getCreatedAt(),
                pricesAvailable, enriched);
    }

    public void delete(UUID id, String owner) {
        transactions.executeWithoutResult(status -> repository.delete(find(id, owner)));
    }

    /**
     * Adds a symbol after market-data confirms it exists. Cheap local checks (404, duplicate, limit) run
     * first so they win over the remote lookup; they are re-checked inside the write transaction, and
     * {@code @Version} turns a concurrent edit into a 409.
     */
    public AddedSymbolResponse addSymbol(UUID id, String owner, String symbol) {
        checkCanAdd(find(id, owner), symbol);
        boolean known;
        try {
            known = marketData.isKnownSymbol(symbol, owner);
        } catch (MarketDataUnavailableException unavailable) {
            throw Downstream.failure("Market data", unavailable.getCause());
        }
        if (!known) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown symbol: " + symbol);
        }
        try {
            WatchlistSymbol added = transactions.execute(status -> {
                WatchlistEntity watchlist = find(id, owner);
                checkCanAdd(watchlist, symbol);
                WatchlistSymbol entry = watchlist.addSymbol(symbol, clock.instant());
                repository.saveAndFlush(watchlist);
                return entry;
            });
            return new AddedSymbolResponse(id, added.symbol(), added.addedAt());
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException concurrentEdit) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Watchlist was modified concurrently; retry", concurrentEdit);
        }
    }

    public void removeSymbol(UUID id, String owner, String symbol) {
        transactions.executeWithoutResult(status -> {
            WatchlistEntity watchlist = find(id, owner);
            if (!watchlist.removeSymbol(symbol)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Symbol " + symbol + " is not on watchlist " + id);
            }
            repository.save(watchlist);
        });
    }

    private WatchlistEntity find(UUID id, String owner) {
        return repository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist not found: " + id));
    }

    private void checkCanAdd(WatchlistEntity watchlist, String symbol) {
        if (watchlist.contains(symbol)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Symbol " + symbol + " is already on watchlist " + watchlist.getId());
        }
        if (watchlist.symbolCount() >= limits.maxSymbols()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Symbol limit reached: at most " + limits.maxSymbols() + " per watchlist");
        }
    }

    private static ResponseStatusException duplicateName(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Watchlist already exists: " + name);
    }
}
