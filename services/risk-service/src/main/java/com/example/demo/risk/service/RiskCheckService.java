package com.example.demo.risk.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.example.demo.risk.client.PortfolioClient;
import com.example.demo.risk.domain.Side;
import com.example.demo.risk.web.RiskDtos.RiskCheckRequest;
import com.example.demo.risk.web.RiskDtos.RiskCheckResponse;
import com.example.demo.risk.web.RiskDtos.RiskLimitsResponse;

/**
 * The pre-trade risk check. Rules, in order (the first breach wins):
 * <ol>
 *   <li>notional ({@code quantity × price}) must not exceed the max order notional — skipped when
 *       no price is known;</li>
 *   <li>the resulting position ({@code held ± quantity}) must not exceed the max position quantity
 *       in absolute terms (so a SELL into a large short is limited too).</li>
 * </ol>
 */
@Service
public class RiskCheckService {

    private static final int DISPLAY_SCALE = 4;

    private final RiskLimitService limits;
    private final PortfolioClient portfolioClient;

    public RiskCheckService(RiskLimitService limits, PortfolioClient portfolioClient) {
        this.limits = limits;
        this.portfolioClient = portfolioClient;
    }

    public RiskCheckResponse check(String owner, RiskCheckRequest request) {
        RiskLimitsResponse ownerLimits = limits.limitsFor(owner);

        if (request.price() != null) {
            BigDecimal notional = request.quantity().multiply(request.price());
            if (notional.compareTo(ownerLimits.maxOrderNotional()) > 0) {
                return RiskCheckResponse.rejected("Order notional " + display(notional)
                        + " exceeds limit " + display(ownerLimits.maxOrderNotional()));
            }
        }

        BigDecimal held = portfolioClient.heldQuantity(owner, request.symbol());
        BigDecimal resulting = request.side() == Side.BUY
                ? held.add(request.quantity())
                : held.subtract(request.quantity());
        if (resulting.abs().compareTo(ownerLimits.maxPositionQuantity()) > 0) {
            return RiskCheckResponse.rejected("Resulting position " + display(resulting)
                    + " exceeds limit " + display(ownerLimits.maxPositionQuantity()));
        }
        return RiskCheckResponse.approved();
    }

    private static String display(BigDecimal value) {
        return value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
