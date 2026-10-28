package com.example.demo.marketdata.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Fast unit coverage of the directional LIMIT/STOP marketability logic (market price = 100). */
class MatchingRulesTest {

    private static final BigDecimal MARKET = new BigDecimal("100");
    private static final String MARKET_ORDER = "MARKET";
    private static final String LIMIT_ORDER = "LIMIT";
    private static final String STOP_ORDER = "STOP";
    private static final String BUY = "BUY";
    private static final String SELL = "SELL";

    @Test
    void marketOrdersAlwaysFill() {
        assertSoftly(softly -> {
            softly.assertThat(MatchingRules.isMarketable(MARKET_ORDER, BUY, MARKET, null, null)).isTrue();
            softly.assertThat(MatchingRules.isMarketable(null, SELL, MARKET, null, null)).isTrue();
        });
    }

    // BUY fills when market ≤ limit; SELL when market ≥ limit (boundary inclusive).
    @ParameterizedTest(name = "LIMIT {0} @ limit {1} -> {2}")
    @CsvSource({
        "BUY,100,true", "BUY,101,true", "BUY,99,false",
        "SELL,100,true", "SELL,99,true", "SELL,101,false"
    })
    void limitMarketability(String side, String limit, boolean marketable) {
        assertThat(MatchingRules.isMarketable(LIMIT_ORDER, side, MARKET, new BigDecimal(limit), null))
                .isEqualTo(marketable);
    }

    // BUY fills when market ≥ stop; SELL when market ≤ stop (boundary inclusive).
    @ParameterizedTest(name = "STOP {0} @ stop {1} -> {2}")
    @CsvSource({
        "BUY,100,true", "BUY,99,true", "BUY,101,false",
        "SELL,100,true", "SELL,101,true", "SELL,99,false"
    })
    void stopMarketability(String side, String stop, boolean marketable) {
        assertThat(MatchingRules.isMarketable(STOP_ORDER, side, MARKET, null, new BigDecimal(stop)))
                .isEqualTo(marketable);
    }
}
