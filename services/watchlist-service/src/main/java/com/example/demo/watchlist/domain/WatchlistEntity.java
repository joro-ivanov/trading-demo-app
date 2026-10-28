package com.example.demo.watchlist.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A named, owner-scoped list of symbols. The owner is the JWT subject. {@link Version} guards
 * concurrent edits of the same watchlist (two adds racing past the size limit).
 */
@Entity
@Table(name = "watchlists")
public class WatchlistEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String owner;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    @ElementCollection
    @CollectionTable(name = "watchlist_symbols", joinColumns = @JoinColumn(name = "watchlist_id"))
    @OrderBy("addedAt ASC, symbol ASC")
    private List<WatchlistSymbol> symbols = new ArrayList<>();

    protected WatchlistEntity() {
        // for JPA
    }

    public WatchlistEntity(UUID id, String owner, String name, Instant createdAt) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** The symbols in display order (added-at, then symbol). Read-only view. */
    public List<WatchlistSymbol> getSymbols() {
        return List.copyOf(symbols);
    }

    public int symbolCount() {
        return symbols.size();
    }

    public boolean contains(String symbol) {
        return symbols.stream().anyMatch(entry -> entry.symbol().equals(symbol));
    }

    public WatchlistSymbol addSymbol(String symbol, Instant addedAt) {
        WatchlistSymbol entry = new WatchlistSymbol(symbol, addedAt);
        symbols.add(entry);
        return entry;
    }

    /** @return whether the symbol was on the watchlist */
    public boolean removeSymbol(String symbol) {
        return symbols.removeIf(entry -> entry.symbol().equals(symbol));
    }
}
