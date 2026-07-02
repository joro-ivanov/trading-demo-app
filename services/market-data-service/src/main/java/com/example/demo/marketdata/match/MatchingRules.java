package com.example.demo.marketdata.match;

import java.math.BigDecimal;

/**
 * Pure marketability rule (extracted from {@link OrderMatcher} so it can be unit-tested directly):
 * MARKET always fills; a LIMIT fills when the market is at-or-through the limit (BUY: market ≤ limit;
 * SELL: market ≥ limit); a STOP fills once the trigger is breached (BUY: market ≥ stop; SELL: market
 * ≤ stop). Boundaries are inclusive (≤/≥).
 */
public final class MatchingRules {

    private MatchingRules() {
    }

    public static boolean isMarketable(String type, String side, BigDecimal market,
                                       BigDecimal limitPrice, BigDecimal stopPrice) {
        String orderType = type == null ? "MARKET" : type;
        boolean buy = "BUY".equals(side);
        return switch (orderType) {
            case "LIMIT" -> buy ? market.compareTo(limitPrice) <= 0
                                : market.compareTo(limitPrice) >= 0;
            case "STOP" -> buy ? market.compareTo(stopPrice) >= 0
                               : market.compareTo(stopPrice) <= 0;
            default -> true;
        };
    }
}
