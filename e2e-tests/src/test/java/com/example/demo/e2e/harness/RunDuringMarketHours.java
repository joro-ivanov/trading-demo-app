package com.example.demo.e2e.harness;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks an e2e test that places orders and therefore only passes while the market is open (the
 * order-service working-hours gate rejects orders outside the session). Outside hours the test is
 * skipped — JUnit's disabled reason surfaces in the Allure report as the skip message.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@ExtendWith(MarketHoursCondition.class)
public @interface RunDuringMarketHours {
}
