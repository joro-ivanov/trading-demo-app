package com.example.demo.notification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Rules for every test in this module (unit, ArchUnit and integration tests):
 * <ul>
 *   <li>tests assert exact values (harness/ plumbing and the flagship's Stack excluded);</li>
 *   <li>tests use DTOs: no hand-read JSON (JsonPath, JsonNode, readTree, body(path, matcher)), and raw
 *       String payloads are sent only from methods annotated {@code @RawJsonPayload(reason)}.</li>
 * </ul>
 * Generated from .claude/skills/it-gen/TestAssertionsArchitectureTest.template.java; do not edit per module.
 */
@AnalyzeClasses(packages = "com.example.demo.notification", importOptions = ImportOption.OnlyIncludeTests.class)
class NotificationTestAssertionsArchitectureTest {

    /**
     * Partial, range and presence-only assertions. A test states the exact expected value instead:
     * isEqualTo / containsExactly / containsExactlyInAnyOrder / isEqualByComparingTo / hasSize / isEmpty,
     * or usingRecursiveComparison().ignoringFields(...) for server-generated fields (ids, timestamps)
     * that are asserted exactly elsewhere (e.g. the id against the persisted row).
     */
    private static final Set<String> WEAK_ASSERTJ = Set.of(
            "contains", "containsAll", "containsAnyOf", "containsAnyElementsOf", "containsSequence",
            "containsSubsequence", "containsOnlyOnce", "containsIgnoringCase", "containsIgnoringWhitespaces",
            "containsIgnoringNewLines", "containsPattern", "containsKey", "containsKeys", "containsEntry",
            "containsEntries", "containsValue", "containsValues", "containsOnlyKeys",
            "doesNotContain", "doesNotContainAnyElementsOf", "doesNotContainKey", "doesNotContainEntry",
            "startsWith", "endsWith", "matches", "doesNotMatch",
            "anyMatch", "allMatch", "noneMatch", "anySatisfy", "allSatisfy", "noneSatisfy", "haveAtLeast",
            "haveAtMost", "areAtLeast", "areAtMost",
            "hasSizeGreaterThan", "hasSizeGreaterThanOrEqualTo", "hasSizeLessThan", "hasSizeLessThanOrEqualTo",
            "hasSizeBetween",
            "isGreaterThan", "isGreaterThanOrEqualTo", "isLessThan", "isLessThanOrEqualTo", "isBetween",
            "isStrictlyBetween", "isCloseTo", "isNotCloseTo", "isPositive", "isNegative", "isNotPositive",
            "isNotNegative", "isNotZero",
            "isAfter", "isBefore", "isAfterOrEqualTo", "isBeforeOrEqualTo", "isInThePast", "isInTheFuture",
            "isToday", "isCloseToUtcNow",
            "isNotNull", "isNotEmpty", "isNotBlank", "isNotEqualTo", "isIn", "isNotIn", "isInstanceOfAny",
            "isNotSameAs");

    private static final Set<String> WEAK_HAMCREST = Set.of(
            "containsString", "containsStringIgnoringCase", "startsWith", "endsWith", "matchesPattern",
            "matchesRegex", "greaterThan", "greaterThanOrEqualTo", "lessThan", "lessThanOrEqualTo", "closeTo",
            "notNullValue", "anything", "hasItem", "hasItems", "hasKey", "hasEntry", "hasValue", "not",
            "either", "anyOf", "oneOf", "isIn", "isOneOf", "everyItem", "hasSize");

    private static final String WEAK_ASSERTION_LABEL = "weak assertion ";

    private static final Set<String> WEAK_WIREMOCK = Set.of("containing", "notContaining", "matching", "notMatching");

    // ---- DTOs only ----

    private static final String RAW_JSON_PAYLOAD = "RawJsonPayload";
    private static final String RAW_JSON_READ_LABEL = "hand-read JSON ";
    private static final String[] JSON_TREE_PACKAGES = {
        "io.restassured.path.json..", "com.jayway.jsonpath..", "org.json..",
        "tools.jackson.databind.node..", "com.fasterxml.jackson.databind.node.."};
    private static final String JSON_NODE = "JsonNode";
    private static final String REST_ASSURED = "io.restassured";
    private static final String JACKSON = "tools.jackson";
    private static final String JACKSON_2 = "com.fasterxml.jackson";
    private static final String WIREMOCK = "com.github.tomakehurst.wiremock";
    private static final Set<String> REST_ASSURED_JSON_READERS = Set.of("jsonPath", "path");
    private static final Set<String> JACKSON_TREE_READERS = Set.of("readTree");
    private static final Set<String> WIREMOCK_JSON_PATH = Set.of("matchingJsonPath");
    private static final Set<String> BODY = Set.of("body");
    private static final String STRING = String.class.getName();
    private static final List<String> STRING_ONLY = List.of(STRING);
    private static final String HARNESS_PACKAGE = "..harness..";
    private static final String JUNIT_TEST = "org.junit.jupiter.api.Test";
    private static final String JUNIT_PARAMETERIZED_TEST = "org.junit.jupiter.params.ParameterizedTest";

    private static final DescribedPredicate<JavaAnnotation<?>> RAW_JSON_PAYLOAD_ANNOTATION = DescribedPredicate.describe(
            "@" + RAW_JSON_PAYLOAD, annotation -> annotation.getRawType().getSimpleName().equals(RAW_JSON_PAYLOAD));

    /** REST-Assured {@code body(String path, Matcher, ...)}: an assertion on a JSON path of the raw response. */
    private static final DescribedPredicate<JavaMethodCall> BODY_PATH_ASSERTION =
            libraryCall(RAW_JSON_READ_LABEL + "REST-Assured body(path, matcher)", REST_ASSURED, BODY)
                    .and(DescribedPredicate.describe("with a path and a matcher",
                            call -> call.getTarget().getRawParameterTypes().size() > 1))
                    .and(DescribedPredicate.describe("where the path is a String",
                            call -> call.getTarget().getRawParameterTypes().get(0).getName().equals(STRING)));

    /** REST-Assured {@code RequestSpecification.body(String)}: a hand-written request body. */
    private static final DescribedPredicate<JavaMethodCall> RAW_STRING_BODY =
            libraryCall("REST-Assured body(String)", REST_ASSURED, BODY)
                    .and(DescribedPredicate.describe("with a raw String payload",
                            call -> call.getTarget().getRawParameterTypes().stream().map(JavaClass::getName).toList()
                                    .equals(STRING_ONLY)));

    /** A harness/framework raw sender, itself annotated {@code @RawJsonPayload}. */
    private static final DescribedPredicate<JavaMethodCall> RAW_SENDER = DescribedPredicate.describe(
            "a @" + RAW_JSON_PAYLOAD + " raw sender",
            call -> call.getTarget().resolveMember()
                    .map(method -> method.isAnnotatedWith(RAW_JSON_PAYLOAD_ANNOTATION)).orElse(false));

    private static final DescribedPredicate<JavaMethodCall> FROM_RAW_JSON_PAYLOAD_METHOD = DescribedPredicate.describe(
            "from a @" + RAW_JSON_PAYLOAD + " method",
            call -> call.getOrigin().isAnnotatedWith(RAW_JSON_PAYLOAD_ANNOTATION));

    @ArchTest
    static final ArchRule testsAssertExactValues = noClasses()
            .that().resideOutsideOfPackage(HARNESS_PACKAGE)
            .and().doNotHaveSimpleName("Stack")
            .should().callMethodWhere(libraryCall(WEAK_ASSERTION_LABEL + "AssertJ", "org.assertj", WEAK_ASSERTJ))
            .orShould().callMethodWhere(libraryCall(WEAK_ASSERTION_LABEL + "Hamcrest", "org.hamcrest", WEAK_HAMCREST))
            .orShould().callMethodWhere(libraryCall(WEAK_ASSERTION_LABEL + "WireMock", WIREMOCK, WEAK_WIREMOCK))
            .because("tests are deterministic and assert exact values: no contains/range/presence-only assertions");

    @ArchTest
    static final ArchRule testsNeverReadRawJson = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(JSON_TREE_PACKAGES)
            .orShould().dependOnClassesThat().haveSimpleName(JSON_NODE)
            .orShould().callMethodWhere(libraryCall(RAW_JSON_READ_LABEL + "REST-Assured", REST_ASSURED, REST_ASSURED_JSON_READERS))
            .orShould().callMethodWhere(libraryCall(RAW_JSON_READ_LABEL + "Jackson", JACKSON, JACKSON_TREE_READERS))
            .orShould().callMethodWhere(libraryCall(RAW_JSON_READ_LABEL + "Jackson 2", JACKSON_2, JACKSON_TREE_READERS))
            .orShould().callMethodWhere(libraryCall(RAW_JSON_READ_LABEL + "WireMock", WIREMOCK, WIREMOCK_JSON_PATH))
            .orShould().callMethodWhere(BODY_PATH_ASSERTION)
            .because("tests deserialize responses and messages into DTOs and assert on the objects (Json.read / as(Dto.class))");

    @ArchTest
    static final ArchRule rawPayloadsOnlyFromAnnotatedMethods = noClasses()
            .should().callMethodWhere(RAW_STRING_BODY.or(RAW_SENDER).and(DescribedPredicate.not(FROM_RAW_JSON_PAYLOAD_METHOD)))
            .because("payloads are DTOs; a hand-written payload is allowed only in a @RawJsonPayload(reason) method");

    @ArchTest
    static final ArchRule rawJsonPayloadOnlyOnTestsOrHarness = methods()
            .that().areAnnotatedWith(RAW_JSON_PAYLOAD_ANNOTATION)
            .should().beAnnotatedWith(JUNIT_TEST)
            .orShould().beAnnotatedWith(JUNIT_PARAMETERIZED_TEST)
            .orShould().beDeclaredInClassesThat().resideInAPackage(HARNESS_PACKAGE)
            .allowEmptyShould(true)
            .because("@RawJsonPayload marks a negative test (or a harness raw sender), never production-like code");

    private static DescribedPredicate<JavaMethodCall> libraryCall(String description, String packagePrefix,
                                                                  Set<String> names) {
        DescribedPredicate<JavaMethodCall> ownedByLibrary = DescribedPredicate.describe(
                description, call -> call.getTargetOwner().getPackageName().startsWith(packagePrefix));
        DescribedPredicate<JavaMethodCall> named = DescribedPredicate.describe(
                new TreeSet<>(names).toString(), call -> names.contains(call.getName()));
        return ownedByLibrary.and(named);
    }
}
