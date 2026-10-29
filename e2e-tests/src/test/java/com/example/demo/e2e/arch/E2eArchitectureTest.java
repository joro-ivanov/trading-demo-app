package com.example.demo.e2e.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import com.example.demo.e2e.framework.service.BaseService;
import com.example.demo.e2e.harness.RunDuringMarketHours;
import com.example.demo.testing.TestAssertionRules;
import com.microsoft.playwright.Page;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

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

    /** Exact-value assertions, DTOs only (raw JSON only via @RawJsonPayload), no sleeping: shared with every module. */
    @ArchTest
    static final ArchTests testAssertions = ArchTests.in(TestAssertionRules.class);

    // @RunDuringMarketHours alone does not make a test: without @Test the method silently never runs.
    @ArchTest
    static final ArchRule marketHoursMethodsAreTests = methods()
            .that().areAnnotatedWith(RunDuringMarketHours.class)
            .should().beAnnotatedWith(Test.class)
            .orShould().beAnnotatedWith(ParameterizedTest.class)
            .allowEmptyShould(true)
            .because("a @RunDuringMarketHours method without @Test or @ParameterizedTest is never executed");
}
