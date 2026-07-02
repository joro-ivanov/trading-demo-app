package com.example.demo.marketdata.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.marketdata.cache.PriceCache;

@RestController
@RequestMapping("/prices")
public class PriceController {

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
}
