package com.example.demo.common.json;

import java.util.List;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Shared Jackson 3 facade for application and test-support code that needs typed JSON mapping. */
public final class Json {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private Json() {
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    public static <T> List<T> readList(String json, Class<T> type) {
        JavaType listType = MAPPER.getTypeFactory().constructCollectionType(List.class, type);
        return MAPPER.readValue(json, listType);
    }
}
