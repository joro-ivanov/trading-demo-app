package com.example.demo.integration;

import com.example.demo.common.json.Json;

import io.restassured.mapper.ObjectMapper;
import io.restassured.mapper.ObjectMapperDeserializationContext;
import io.restassured.mapper.ObjectMapperSerializationContext;

/**
 * REST-Assured mapper backed by the project's {@link Json}, so request DTOs are serialized and responses
 * deserialized exactly as the services do it, without a hand-written {@code body(String)}.
 */
final class JsonObjectMapper implements ObjectMapper {

    static final JsonObjectMapper INSTANCE = new JsonObjectMapper();

    private JsonObjectMapper() {
    }

    @Override
    public Object serialize(ObjectMapperSerializationContext context) {
        return Json.write(context.getObjectToSerialize());
    }

    @Override
    public Object deserialize(ObjectMapperDeserializationContext context) {
        return Json.read(context.getDataToDeserialize().asString(), (Class<?>) context.getType());
    }
}
