package com.example.demo.portfolio.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key for {@link PositionEntity}: (owner, symbol). */
public class PositionId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String owner;
    private String symbol;

    public PositionId() {
    }

    public PositionId(String owner, String symbol) {
        this.owner = owner;
        this.symbol = symbol;
    }

    public String getOwner() {
        return owner;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PositionId that)) {
            return false;
        }
        return Objects.equals(owner, that.owner) && Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, symbol);
    }
}
