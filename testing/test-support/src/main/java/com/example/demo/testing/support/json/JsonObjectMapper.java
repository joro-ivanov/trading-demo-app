package com.example.demo.testing.support.json;

import com.example.demo.common.json.Json;

import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.mapper.ObjectMapper;
import io.restassured.mapper.ObjectMapperDeserializationContext;
import io.restassured.mapper.ObjectMapperSerializationContext;

/**
 * REST-Assured's object mapper for the tests: request DTOs ({@code .body(dto)}) and responses
 * ({@code .as(Dto.class)}) go through the project's {@link Json}, so tests see exactly the JSON the
 * services see and never build or read JSON by hand.
 */
public final class JsonObjectMapper implements ObjectMapper {

    public static final JsonObjectMapper INSTANCE = new JsonObjectMapper();

    /** A REST-Assured config that (de)serializes every body with {@link #INSTANCE}. */
    public static final RestAssuredConfig DTO_CONFIG = RestAssuredConfig.config()
            .objectMapperConfig(new ObjectMapperConfig(INSTANCE));

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
