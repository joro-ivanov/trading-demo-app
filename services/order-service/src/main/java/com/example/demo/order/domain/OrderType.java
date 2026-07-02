package com.example.demo.order.domain;

/** MARKET fills at the latest price; LIMIT needs a limit price; STOP needs a stop (trigger) price. */
public enum OrderType {
    MARKET, LIMIT, STOP
}
