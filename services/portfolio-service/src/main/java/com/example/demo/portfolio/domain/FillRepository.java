package com.example.demo.portfolio.domain;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Recent fills per owner, newest first; ties break by insertion sequence. */
public interface FillRepository extends JpaRepository<FillEntity, String> {

    List<FillEntity> findByOwnerOrderByFilledAtDescSequenceIdDesc(String owner, Pageable pageable);
}
