package com.example.demo.e2e.framework.http;

/**
 * HTTP verbs supported by the e2e service objects. Kept transport-agnostic on purpose so that
 * service objects never import the underlying HTTP engine (REST-Assured); only
 * {@code BaseService} maps these to engine calls. See docs/DESIGN.md §8, §13.1.
 */
public enum HttpMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS
}
