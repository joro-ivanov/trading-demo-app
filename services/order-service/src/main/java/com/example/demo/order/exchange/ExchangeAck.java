package com.example.demo.order.exchange;

/** Acknowledgement returned by the (mocked) exchange when routing an order. */
public record ExchangeAck(String status, String exchangeOrderId) {
}
