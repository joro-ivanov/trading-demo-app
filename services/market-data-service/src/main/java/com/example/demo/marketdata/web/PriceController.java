package com.example.demo.marketdata.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.marketdata.cache.PriceCache;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/prices")
public class PriceController {

    /** Upper bound for one batch lookup, so a single request cannot fan out into an unbounded Redis scan. */
    static final int MAX_BATCH_SYMBOLS = 20;

    private final PriceCache cache;

    public PriceController(PriceCache cache) {
        this.cache = cache;
    }

    @GetMapping("/{symbol}")
    public PriceResponse latest(@PathVariable String symbol) {
        return cache.latest(symbol)
                .map(money -> new PriceResponse(symbol, money.amount()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No price for " + symbol));
    }

    /**
     * Batch lookup: {@code GET /prices?symbols=AAPL,MSFT}. Returns the latest price of every symbol that
     * has one, in request order with duplicates collapsed; symbols without a price are omitted (not an
     * error), so a client can render a partial watchlist.
     */
    @GetMapping
    public List<PriceResponse> latest(
            @RequestParam
            @Size(min = 1, max = MAX_BATCH_SYMBOLS)
            List<@Pattern(regexp = "^[A-Z]{1,10}$") String> symbols) {
        return symbols.stream()
                .distinct()
                .flatMap(symbol -> cache.latest(symbol).map(money -> new PriceResponse(symbol, money.amount())).stream())
                .toList();
    }
}
