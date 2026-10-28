package com.example.demo.risk.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskLimitRepository extends JpaRepository<RiskLimitEntity, String> {
}
