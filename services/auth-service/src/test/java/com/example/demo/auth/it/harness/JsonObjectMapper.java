package com.example.demo.auth.it.harness;

import com.example.demo.common.json.Json;

import io.restassured.mapper.ObjectMapper;
import io.restassured.mapper.ObjectMapperDeserializationContext;
import io.restassured.mapper.ObjectMapperSerializationContext;

/**
 * REST-Assured's object mapper, delegating to the project's {@link Json}: {@code .body(dto)} sends a DTO
 * serialized exactly as the services serialize it, so tests never hand-write a JSON request body.
 */
final class JsonObjectMapper implements ObjectMapper {

    @Override
    public Object deserialize(ObjectMapperDeserializationContext context) {
        return Json.read(context.getDataToDeserialize().asString(), (Class<?>) context.getType());
    }

    @Override
    public Object serialize(ObjectMapperSerializationContext context) {
        return Json.write(context.getObjectToSerialize());
    }
}
