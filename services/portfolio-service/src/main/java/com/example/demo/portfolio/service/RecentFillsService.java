package com.example.demo.portfolio.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.portfolio.domain.FillEntity;
import com.example.demo.portfolio.domain.FillRepository;
import com.example.demo.portfolio.events.FillEvent;

/** Persists and serves the dashboard's recent fills per owner. */
@Service
public class RecentFillsService {

    private static final int RECENT_LIMIT = 20;

    private final FillRepository repository;

    public RecentFillsService(FillRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordFill(FillEvent fill) {
        repository.save(new FillEntity(
                fill.orderId(),
                fill.owner(),
                fill.symbol(),
                fill.side(),
                fill.quantity(),
                fill.price(),
                fill.filledAt(),
                fill.sequenceId()));
    }

    @Transactional(readOnly = true)
    public List<FillEntity> recentFills(String owner) {
        return repository.findByOwnerOrderByFilledAtDescSequenceIdDesc(owner, PageRequest.of(0, RECENT_LIMIT));
    }
}
