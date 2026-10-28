package com.example.demo.watchlist.client;

/** market-data-service could not answer (connection error, timeout, 5xx or an unexpected 4xx). */
public class MarketDataUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MarketDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
