package com.example.demo.order.it.harness;

import com.example.demo.common.json.Json;

import io.restassured.mapper.ObjectMapper;
import io.restassured.mapper.ObjectMapperDeserializationContext;
import io.restassured.mapper.ObjectMapperSerializationContext;

/**
 * REST-Assured's object mapper for the ITs: request DTOs ({@code .body(dto)}) and responses
 * ({@code .as(Dto.class)}) go through the project's {@link Json}, so tests see exactly the JSON the
 * service sees and never build or read JSON by hand.
 */
final class JsonObjectMapper implements ObjectMapper {

    @Override
    public Object serialize(ObjectMapperSerializationContext context) {
        return Json.write(context.getObjectToSerialize());
    }

    @Override
    public Object deserialize(ObjectMapperDeserializationContext context) {
        return Json.read(context.getDataToDeserialize().asString(), (Class<?>) context.getType());
    }
}
