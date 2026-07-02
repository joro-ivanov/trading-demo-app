package com.example.demo.e2e.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.microsoft.playwright.Page;
import com.example.demo.e2e.framework.service.BaseService;

/**
 * ArchUnit rules enforcing the e2e framework's design invariants (docs/DESIGN.md §13.1). Conditions
 * use stream filters (not control statements) so the rule class itself obeys the test conventions.
 */
@AnalyzeClasses(packages = "com.example.demo.e2e")
class E2eArchitectureTest {

    private static final ArchCondition<JavaClass> HAVE_ONLY_PAGE_CONSTRUCTORS =
            new ArchCondition<>("have only constructors that take a Playwright Page") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    clazz.getConstructors().stream()
                            .filter(constructor -> constructor.getRawParameterTypes().size() != 1
                                    || !constructor.getRawParameterTypes().get(0).isEquivalentTo(Page.class))
                            .forEach(constructor -> events.add(SimpleConditionEvent.violated(
                                    constructor, constructor.getFullName() + " must take a Page")));
                }
            };

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
    static final ArchRule pageObjectsHaveOnlyPageConstructors = classes()
            .that().resideInAPackage("com.example.demo.e2e.framework.pages..")
            .should(HAVE_ONLY_PAGE_CONSTRUCTORS);
}
