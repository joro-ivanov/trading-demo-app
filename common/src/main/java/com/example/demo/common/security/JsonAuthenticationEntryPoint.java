package com.example.demo.common.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes a stable JSON body for 401 responses so clients and e2e tests can assert the exact reason
 * instead of reverse-engineering the presence or absence of a bearer token from an empty response.
 */
public final class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        OBJECT_MAPPER.writeValue(response.getOutputStream(), Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.UNAUTHORIZED.value(),
                "error", HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "message", messageFor(request, authException),
                "path", request.getRequestURI()));
    }

    private static String messageFor(HttpServletRequest request, AuthenticationException authException) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return "Authentication token is required";
        }
        if (!authorization.startsWith("Bearer ")) {
            return "Bearer token is required";
        }
        Throwable cause = authException.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        if (authException.getMessage() != null && !authException.getMessage().isBlank()) {
            return authException.getMessage();
        }
        return HttpStatus.UNAUTHORIZED.getReasonPhrase();
    }
}
