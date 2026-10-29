package com.example.demo.testing.support.time;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.example.demo.testing.support.fixtures.TradingInstants;

/**
 * Replaces the service's {@code Clock} with a {@link MutableClock} the tests control
 * ({@code @Import(MutableClockConfig.class)}); it starts at {@link TradingInstants#DURING_HOURS}.
 */
@TestConfiguration
public class MutableClockConfig {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(TradingInstants.DURING_HOURS);
    }
}
