package com.example.demo.alert.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<AlertEntity, UUID> {

    List<AlertEntity> findByOwnerOrderByCreatedAtAscIdAsc(String owner);

    List<AlertEntity> findByOwnerAndStatusOrderByCreatedAtAscIdAsc(String owner, AlertStatus status);

    Optional<AlertEntity> findByIdAndOwner(UUID id, String owner);

    long countByOwnerAndStatus(String owner, AlertStatus status);

    List<AlertEntity> findBySymbolAndStatus(String symbol, AlertStatus status);
}
