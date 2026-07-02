package com.example.demo.portfolio.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<PositionEntity, PositionId> {

    List<PositionEntity> findByOwner(String owner);

    List<PositionEntity> findBySymbol(String symbol);

    Optional<PositionEntity> findByOwnerAndSymbol(String owner, String symbol);
}
