package com.example.demo.portfolio.consume;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.demo.portfolio.events.FillEvent;
import com.example.demo.portfolio.events.PriceMessage;
import com.example.demo.portfolio.service.FundsService;
import com.example.demo.portfolio.service.PortfolioService;
import com.example.demo.portfolio.service.RecentFillsService;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Kafka consumers feeding the portfolio: fills update positions + cash, ticks update mark price. */
@Component
public class PortfolioListeners {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final PortfolioService portfolioService;
    private final FundsService fundsService;
    private final RecentFillsService recentFills;

    public PortfolioListeners(PortfolioService portfolioService, FundsService fundsService,
                              RecentFillsService recentFills) {
        this.portfolioService = portfolioService;
        this.fundsService = fundsService;
        this.recentFills = recentFills;
    }

    @KafkaListener(topics = "fills", groupId = "portfolio-fills")
    public void onFill(String payload) {
        FillEvent fill = MAPPER.readValue(payload, FillEvent.class);
        portfolioService.applyFill(fill);
        fundsService.applyFill(fill);
        recentFills.recordFill(fill);
    }

    @KafkaListener(topics = "price.ticks", groupId = "portfolio-ticks")
    public void onPriceTick(String payload) {
        portfolioService.applyPrice(MAPPER.readValue(payload, PriceMessage.class));
    }
}
