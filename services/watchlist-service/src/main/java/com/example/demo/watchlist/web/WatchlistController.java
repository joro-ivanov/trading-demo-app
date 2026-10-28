package com.example.demo.watchlist.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.watchlist.service.WatchlistService;
import com.example.demo.watchlist.web.WatchlistDtos.AddSymbolRequest;
import com.example.demo.watchlist.web.WatchlistDtos.AddedSymbolResponse;
import com.example.demo.watchlist.web.WatchlistDtos.CreateWatchlistRequest;
import com.example.demo.watchlist.web.WatchlistDtos.WatchlistResponse;
import com.example.demo.watchlist.web.WatchlistDtos.WatchlistSummaryResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * Owner-scoped watchlists. The owner is always the JWT subject ({@code authentication.getName()}),
 * so a user can only see and change their own watchlists; another user's id answers 404.
 */
@Validated
@RestController
@RequestMapping("/watchlists")
public class WatchlistController {

    private final WatchlistService watchlists;

    public WatchlistController(WatchlistService watchlists) {
        this.watchlists = watchlists;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistResponse create(@Valid @RequestBody CreateWatchlistRequest request, Authentication authentication) {
        return watchlists.create(authentication.getName(), request.name());
    }

    @GetMapping
    public List<WatchlistSummaryResponse> list(Authentication authentication) {
        return watchlists.list(authentication.getName());
    }

    @GetMapping("/{id}")
    public WatchlistResponse get(@PathVariable UUID id, Authentication authentication) {
        return watchlists.get(id, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        watchlists.delete(id, authentication.getName());
    }

    @PostMapping("/{id}/symbols")
    @ResponseStatus(HttpStatus.CREATED)
    public AddedSymbolResponse addSymbol(@PathVariable UUID id, @Valid @RequestBody AddSymbolRequest request,
                                         Authentication authentication) {
        return watchlists.addSymbol(id, authentication.getName(), request.symbol());
    }

    @DeleteMapping("/{id}/symbols/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSymbol(@PathVariable UUID id,
                             @PathVariable @Pattern(regexp = WatchlistDtos.SYMBOL_PATTERN) String symbol,
                             Authentication authentication) {
        watchlists.removeSymbol(id, authentication.getName(), symbol);
    }
}
