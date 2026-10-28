package com.example.demo.watchlist.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistRepository extends JpaRepository<WatchlistEntity, UUID> {

    /** Owner-scoped lookup: another owner's watchlist is indistinguishable from a missing one. */
    @EntityGraph(attributePaths = "symbols")
    Optional<WatchlistEntity> findByIdAndOwner(UUID id, String owner);

    @EntityGraph(attributePaths = "symbols")
    List<WatchlistEntity> findByOwnerOrderByCreatedAtAscNameAsc(String owner);

    long countByOwner(String owner);

    boolean existsByOwnerAndName(String owner, String name);
}
