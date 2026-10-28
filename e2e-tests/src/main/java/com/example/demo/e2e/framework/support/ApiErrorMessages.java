package com.example.demo.e2e.framework.support;

/** Error messages the services return, shared by several suites (expected values in tests). */
public final class ApiErrorMessages {

    /** Any request body / parameter that fails validation (common ApiErrorHandler). */
    public static final String INVALID_REQUEST_CONTENT = "Invalid request content.";
    /** Wrong password or unknown user on login. */
    public static final String INVALID_CREDENTIALS = "Invalid credentials";
    /** Prefix of the 401 message when a bearer token cannot be decoded; the reason follows. */
    public static final String JWT_DECODE_ERROR = "An error occurred while attempting to decode the Jwt: ";

    private ApiErrorMessages() {
    }
}
