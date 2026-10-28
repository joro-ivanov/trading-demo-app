package com.example.demo.portfolio.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.common.money.Money;

public interface PositionRepository extends JpaRepository<PositionEntity, PositionId> {

    List<PositionEntity> findByOwner(String owner);

    Optional<PositionEntity> findByOwnerAndSymbol(String owner, String symbol);

    /**
     * Marks every position in {@code symbol} to {@code marketPrice}, touching only {@code market_price}.
     * A tick must never rewrite quantity or average price: a whole-entity save from the tick path could
     * restore a stale quantity over a concurrently committed fill (lost update). Does not bump the version,
     * so it never conflicts with a fill.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PositionEntity p set p.marketPrice = :marketPrice where p.symbol = :symbol")
    int markToMarket(@Param("symbol") String symbol, @Param("marketPrice") Money marketPrice);
}
