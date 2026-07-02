package com.example.demo.portfolio.domain;

import com.example.demo.common.money.Money;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A user's cash balance (fixed-point {@link Money}). One row per owner (the JWT subject). */
@Entity
@Table(name = "balances")
public class BalanceEntity {

    @Id
    private String owner;

    @Column(nullable = false)
    private Money cash;

    protected BalanceEntity() {
        // for JPA
    }

    public BalanceEntity(String owner, Money cash) {
        this.owner = owner;
        this.cash = cash;
    }

    public String getOwner() {
        return owner;
    }

    public Money getCash() {
        return cash;
    }

    public void setCash(Money cash) {
        this.cash = cash;
    }
}
