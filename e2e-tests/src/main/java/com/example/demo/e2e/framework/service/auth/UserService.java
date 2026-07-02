package com.example.demo.e2e.framework.service.auth;

import com.example.demo.e2e.framework.http.ApiRequest;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.http.HttpMethod;

/**
 * User-management functionality of the auth-service. Second {@code *Service} extending the
 * shared {@link BaseAuthService} (docs/DESIGN.md §8.4).
 */
public final class UserService extends BaseAuthService {

    private UserService() {
    }

    public static ApiResponse getUser(String token, String userId) {
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.GET)
                .path(BASE_PATH + "/users/{id}")
                .pathParam("id", userId)
                .token(token)
                .build());
    }

    public static ApiResponse createUser(ApiRequest request) {
        return execute(serviceUrl(), request);
    }
}
