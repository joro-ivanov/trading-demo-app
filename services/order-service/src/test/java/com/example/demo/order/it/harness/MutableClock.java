package com.example.demo.order.it.harness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock whose instant the test sets, to place orders inside or outside working hours. */
public class MutableClock extends Clock {

    private volatile Instant instant = OrderITBase.DURING_HOURS;

    public void set(Instant value) {
        this.instant = value;
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
