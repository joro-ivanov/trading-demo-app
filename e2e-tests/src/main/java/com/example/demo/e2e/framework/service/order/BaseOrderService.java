package com.example.demo.e2e.framework.service.order;

import com.example.demo.e2e.framework.config.Config;
import com.example.demo.e2e.framework.service.BaseService;

/**
 * Shared base for the order-service functionalities (orders + history). Multiple
 * functionalities -> {@code Base*Service} pattern (docs/DESIGN.md §8.4).
 */
public abstract class BaseOrderService extends BaseService {

    protected static final String BASE_PATH = "/orders";

    protected static String serviceUrl() {
        return Config.get().get("order.base.url");
    }
}
