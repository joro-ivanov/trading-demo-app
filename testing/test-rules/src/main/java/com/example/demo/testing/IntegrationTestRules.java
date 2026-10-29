package com.example.demo.testing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Structure of a service's integration tests (test classes only; it.harness is plumbing). Exact-value assertions
 * are {@link TestAssertionRules}; never expecting a 500 is PMD {@code NoInternalServerError}. Included next to
 * {@code TestAssertionRules} by every service's rules test.
 */
public final class IntegrationTestRules {

    @ArchTest
    public static final ArchRule integrationTestsMockOnlyExternalCommunication = noClasses()
            .that().resideInAPackage("..it..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.mockito..",
                    "org.springframework.test.context.bean.override.mockito..")
            .allowEmptyShould(true)
            .because("per-service ITs run the real service; only external communication is stubbed (WireMock)");

    @ArchTest
    public static final ArchRule integrationTestsLiveInTheItPackage = classes()
            .that().haveSimpleNameEndingWith("IT")
            .should().resideInAPackage("..it")
            .allowEmptyShould(true)
            .because("one *IT class per endpoint/listener under <service>.it, plumbing under it.harness");

    private IntegrationTestRules() {
    }
}
