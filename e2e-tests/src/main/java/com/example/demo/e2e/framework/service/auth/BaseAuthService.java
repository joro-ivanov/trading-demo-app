package com.example.demo.e2e.framework.service.auth;

import com.example.demo.e2e.framework.config.Config;
import com.example.demo.e2e.framework.service.BaseService;

/**
 * Shared base for the auth-service functionalities (login + user management). The auth-service
 * has multiple functionalities, so per docs/DESIGN.md §8.4 it gets a {@code Base*Service} that
 * the concrete {@code *Service} classes extend.
 */
public abstract class BaseAuthService extends BaseService {

    protected static final String BASE_PATH = "/auth";

    protected static String serviceUrl() {
        return Config.get().get("auth.base.url");
    }
}
