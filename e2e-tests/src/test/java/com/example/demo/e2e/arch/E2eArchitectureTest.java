package com.example.demo.e2e.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.microsoft.playwright.Page;
import com.example.demo.e2e.framework.service.BaseService;
import com.example.demo.e2e.harness.RunDuringMarketHours;

/**
 * ArchUnit rules enforcing the e2e framework's design invariants (docs/DESIGN.md §13.1). Rules are
 * expressed with the ArchUnit DSL and predicate combinators (no operators, filters or control
 * statements), so the rule class itself obeys the test conventions.
 */
@AnalyzeClasses(packages = "com.example.demo.e2e")
class E2eArchitectureTest {

    @ArchTest
    static final ArchRule restAssuredIsConfinedToBaseService = noClasses()
            .that().haveNameNotMatching(".*\\.BaseService")
            .should().dependOnClassesThat().resideInAPackage("io.restassured..")
            .because("the HTTP engine must be swappable by editing BaseService.send only");

    @ArchTest
    static final ArchRule stompClientIsConfinedToTheWsPackage = noClasses()
            .that().resideOutsideOfPackages("com.example.demo.e2e.framework.ws..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.messaging..", "org.springframework.web.socket..")
            .because("the STOMP client is confined to BaseWsService / the ws package");

    @ArchTest
    static final ArchRule playwrightIsConfinedToTheUiLayer = noClasses()
            .that().resideOutsideOfPackages(
                    "com.example.demo.e2e.framework.ui..",
                    "com.example.demo.e2e.framework.pages..",
                    "com.example.demo.e2e.harness..",
                    "com.example.demo.e2e.arch..")
            .and().doNotHaveSimpleName("BaseUiTest")
            .should().dependOnClassesThat().resideInAPackage("com.microsoft.playwright..")
            .because("e2e tests drive the UI through shared UI test scaffolding and Page Objects");

    @ArchTest
    static final ArchRule frameworkDoesNotValidateInputs = noClasses()
            .that().resideInAPackage("com.example.demo.e2e.framework..")
            .should().dependOnClassesThat().resideInAPackage("jakarta.validation..")
            .because("no defensive programming — negative scenarios must reach the server");

    @ArchTest
    static final ArchRule apiTypesAreImmutableRecords = classes()
            .that().haveSimpleName("ApiRequest").or().haveSimpleName("ApiResponse")
            .should().beRecords();

    @ArchTest
    static final ArchRule serviceObjectsExtendBaseService = classes()
            .that().resideInAPackage("com.example.demo.e2e.framework.service..")
            .should().beAssignableTo(BaseService.class)
            .because("every API service object is a BaseService facade");

    @ArchTest
    static final ArchRule serviceMethodsAreStatic = methods()
            .that().areDeclaredInClassesThat().resideInAPackage("com.example.demo.e2e.framework.service..")
            .and().arePublic()
            .should().beStatic()
            .because("service objects are dumb pipes with static methods");

    @ArchTest
    static final ArchRule pageObjectsHaveOnlyPageConstructors = constructors()
            .that().areDeclaredInClassesThat().resideInAPackage("com.example.demo.e2e.framework.pages..")
            .should().haveRawParameterTypes(Page.class)
            .because("page objects are built only from a Playwright Page");

    // ---- DTOs only: tests send DTOs and assert on deserialized DTOs; raw JSON only via @RawJsonPayload ----

    private static final String RAW_JSON_PAYLOAD = "RawJsonPayload";
    private static final String[] JSON_TREE_PACKAGES = {
        "io.restassured.path.json..", "com.jayway.jsonpath..", "org.json..",
        "tools.jackson.databind.node..", "com.fasterxml.jackson.databind.node.."};
    private static final String JSON_NODE = "JsonNode";
    private static final String JSON_TREE_READ_LABEL = "a JSON tree/path read via ";
    private static final Set<String> JSON_TREE_READERS = Set.of("readTree", "jsonPath");
    private static final String REST_ASSURED = "io.restassured";
    private static final String JACKSON = "tools.jackson";
    private static final String JACKSON_2 = "com.fasterxml.jackson";
    private static final String FRAMEWORK_PACKAGE = "com.example.demo.e2e.framework..";
    private static final String JUNIT_TEST = "org.junit.jupiter.api.Test";
    private static final String JUNIT_PARAMETERIZED_TEST = "org.junit.jupiter.params.ParameterizedTest";

    private static final DescribedPredicate<JavaAnnotation<?>> RAW_JSON_PAYLOAD_ANNOTATION = DescribedPredicate.describe(
            "@" + RAW_JSON_PAYLOAD, annotation -> annotation.getRawType().getSimpleName().equals(RAW_JSON_PAYLOAD));

    private static final DescribedPredicate<JavaMethodCall> RAW_SENDER = DescribedPredicate.describe(
            "a @" + RAW_JSON_PAYLOAD + " raw sender (e.g. ApiRequest.Builder.rawBody)",
            call -> call.getTarget().resolveMember()
                    .map(method -> method.isAnnotatedWith(RAW_JSON_PAYLOAD_ANNOTATION)).orElse(false));

    private static final DescribedPredicate<JavaMethodCall> FROM_RAW_JSON_PAYLOAD_METHOD = DescribedPredicate.describe(
            "from a method annotated @" + RAW_JSON_PAYLOAD,
            call -> call.getOrigin().isAnnotatedWith(RAW_JSON_PAYLOAD_ANNOTATION));

    @ArchTest
    static final ArchRule e2eNeverReadsRawJson = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(JSON_TREE_PACKAGES)
            .orShould().dependOnClassesThat().haveSimpleName(JSON_NODE)
            .orShould().callMethodWhere(libraryCall(JSON_TREE_READ_LABEL + REST_ASSURED, REST_ASSURED, JSON_TREE_READERS))
            .orShould().callMethodWhere(libraryCall(JSON_TREE_READ_LABEL + JACKSON, JACKSON, JSON_TREE_READERS))
            .orShould().callMethodWhere(libraryCall(JSON_TREE_READ_LABEL + JACKSON_2, JACKSON_2, JSON_TREE_READERS))
            .because("responses are deserialized into DTOs (ApiResponse.as / asList) and asserted as objects");

    @ArchTest
    static final ArchRule rawBodiesOnlyFromRawJsonPayloadTests = noClasses()
            .should().callMethodWhere(RAW_SENDER.and(DescribedPredicate.not(FROM_RAW_JSON_PAYLOAD_METHOD)))
            .because("request bodies are DTOs; a hand-written body (rawBody) only in a @RawJsonPayload(reason) test");

    @ArchTest
    static final ArchRule rawJsonPayloadOnlyOnTestsOrFramework = methods()
            .that().areAnnotatedWith(RAW_JSON_PAYLOAD_ANNOTATION)
            .should().beAnnotatedWith(JUNIT_TEST)
            .orShould().beAnnotatedWith(JUNIT_PARAMETERIZED_TEST)
            .orShould().beDeclaredInClassesThat().resideInAPackage(FRAMEWORK_PACKAGE)
            .allowEmptyShould(true)
            .because("@RawJsonPayload marks a negative test or a framework raw sender");

    // @RunDuringMarketHours alone does not make a test: without @Test the method silently never runs.
    @ArchTest
    static final ArchRule marketHoursMethodsAreTests = methods()
            .that().areAnnotatedWith(RunDuringMarketHours.class)
            .should().beAnnotatedWith(JUNIT_TEST)
            .orShould().beAnnotatedWith(JUNIT_PARAMETERIZED_TEST)
            .allowEmptyShould(true)
            .because("a @RunDuringMarketHours method without @Test or @ParameterizedTest is never executed");

    private static DescribedPredicate<JavaMethodCall> libraryCall(String description, String packagePrefix,
                                                                  Set<String> names) {
        DescribedPredicate<JavaMethodCall> ownedByLibrary = DescribedPredicate.describe(
                description, call -> call.getTargetOwner().getPackageName().startsWith(packagePrefix));
        DescribedPredicate<JavaMethodCall> named = DescribedPredicate.describe(
                new TreeSet<>(names).toString(), call -> names.contains(call.getName()));
        return ownedByLibrary.and(named);
    }
}
