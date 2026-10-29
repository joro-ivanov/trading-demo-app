package com.example.demo.testing.support.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.restassured.specification.RequestSpecification;

/**
 * Root of every per-service IT harness: the real service on a random port, driven over HTTP with REST-Assured.
 * A service's {@code <Svc>ITBase} extends it and adds only its own containers, stubs and domain helpers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ServiceITBase {

    @LocalServerPort
    protected int port;

    /** An unauthenticated JSON request to the running service. */
    protected RequestSpecification api() {
        return ApiSpec.api(port);
    }

    /** A JSON request carrying {@code Authorization: Bearer <token>}. */
    protected RequestSpecification api(String token) {
        return ApiSpec.api(port, token);
    }
}
