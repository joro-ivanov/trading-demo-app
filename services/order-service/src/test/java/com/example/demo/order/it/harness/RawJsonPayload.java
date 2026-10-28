package com.example.demo.order.it.harness;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The only opt-in for hand-written JSON. Tests send DTOs and assert on deserialized DTOs; a payload a DTO
 * cannot express (a wrong JSON type, a missing-vs-null field, syntactically broken JSON, a malformed
 * Kafka/WebSocket message) is allowed only inside a method carrying this annotation, with a reason.
 * PMD {@code NoRawJsonOutsideRawJsonPayload} and ArchUnit {@code rawPayloadsOnlyFromAnnotatedMethods}
 * enforce it (matched by simple name). Generated from .claude/skills/it-gen/RawJsonPayload.template.java.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RawJsonPayload {

    /** Why this scenario needs a payload no DTO can produce, e.g. "quantity sent as a JSON string". */
    String reason();
}
