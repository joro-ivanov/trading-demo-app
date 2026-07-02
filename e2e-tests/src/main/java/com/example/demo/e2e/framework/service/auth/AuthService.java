package com.example.demo.e2e.framework.service.auth;

import com.example.demo.e2e.framework.http.ApiRequest;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.http.HttpMethod;
import com.example.demo.e2e.framework.model.LoginRequest;
import com.example.demo.e2e.framework.model.RegisterRequest;

/**
 * Login + self-service registration functionality of the auth-service. Dumb pipe: static methods
 * that build an {@link ApiRequest} and delegate to {@code BaseService.execute}. No validation (§8.2).
 */
public final class AuthService extends BaseAuthService {

    private AuthService() {
    }

    /** Few args -> build the request internally (docs/DESIGN.md §8.4). */
    public static ApiResponse login(String username, String password) {
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(BASE_PATH + "/login")
                .body(new LoginRequest(username, password))
                .build());
    }

    /** Many args / full control -> caller passes a prebuilt request. */
    public static ApiResponse login(ApiRequest request) {
        return execute(serviceUrl(), request);
    }

    public static ApiResponse register(String username, String email, String password) {
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(BASE_PATH + "/register")
                .body(new RegisterRequest(username, email, password))
                .build());
    }

    /** Full control (e.g. malformed bodies for negative tests) -> caller passes a prebuilt request. */
    public static ApiResponse register(ApiRequest request) {
        return execute(serviceUrl(), request);
    }
}
