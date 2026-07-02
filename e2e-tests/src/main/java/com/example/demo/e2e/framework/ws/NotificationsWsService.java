package com.example.demo.e2e.framework.ws;

import java.util.List;

/**
 * WebSocket service object for the notification-service gateway (STOMP/SockJS). Mirrors the API
 * service-object pattern: dumb pipe, static methods, returns a {@link WsSession} handle.
 */
public final class NotificationsWsService extends BaseWsService {

    private NotificationsWsService() {
    }

    /** Subscribe to the live price ticks for the given symbols. */
    public static WsSession subscribePrices(String token, List<String> symbols) {
        String[] destinations = symbols.stream()
                .map(symbol -> "/topic/prices." + symbol)
                .toArray(String[]::new);
        return connect(token, destinations);
    }

    /** Subscribe to order-fill notifications for the authenticated user. */
    public static WsSession subscribeFills(String token) {
        return connect(token, "/user/queue/fills");
    }
}
