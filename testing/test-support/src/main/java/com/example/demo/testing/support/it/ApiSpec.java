package com.example.demo.testing.support.it;

import com.example.demo.testing.support.auth.TestJwt;
import com.example.demo.testing.support.json.JsonObjectMapper;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/** The REST-Assured request every test sends to a service on localhost: JSON, DTO-mapped, optionally authenticated. */
public final class ApiSpec {

    private static final String LOCALHOST = "http://localhost";
    private static final String AUTHORIZATION = "Authorization";

    private ApiSpec() {
    }

    /** An unauthenticated JSON request; {@code .body(dto)} and {@code .as(Dto.class)} use {@code Json}. */
    public static RequestSpecification api(int port) {
        return RestAssured.given()
                .config(JsonObjectMapper.DTO_CONFIG)
                .baseUri(LOCALHOST)
                .port(port)
                .contentType(ContentType.JSON);
    }

    /** A JSON request carrying {@code Authorization: Bearer <token>}. */
    public static RequestSpecification api(int port, String token) {
        return api(port).header(AUTHORIZATION, TestJwt.bearer(token));
    }
}
