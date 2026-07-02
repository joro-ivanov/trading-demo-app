package com.example.demo.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** Cash balances keyed by owner (the JWT subject). */
public interface BalanceRepository extends JpaRepository<BalanceEntity, String> {
}
