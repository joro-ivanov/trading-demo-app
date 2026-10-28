package com.example.demo.marketdata;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.springframework.http.HttpStatus;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Structure of the integration tests themselves (test classes only; it.harness is plumbing). Exact-value
 * assertions are enforced module-wide by the generated TestAssertionsArchitectureTest.
 */
@AnalyzeClasses(packages = "com.example.demo.marketdata", importOptions = ImportOption.OnlyIncludeTests.class)
class MarketDataIntegrationTestArchitectureTest {

    @ArchTest
    static final ArchRule integrationTestsMockOnlyExternalCommunication = noClasses()
            .that().resideInAPackage("..it..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.mockito..",
                    "org.springframework.test.context.bean.override.mockito..")
            .because("per-service ITs run the real service; only external communication is stubbed (WireMock)");

    @ArchTest
    static final ArchRule integrationTestsLiveInTheItPackage = classes()
            .that().haveSimpleNameEndingWith("IT")
            .should().resideInAPackage("..it")
            .because("one *IT class per endpoint/listener under <service>.it, plumbing under it.harness");

    @ArchTest
    static final ArchRule integrationTestsNeverExpectInternalServerError = noClasses()
            .that().resideInAPackage("..it")
            .should().accessField(HttpStatus.class, "INTERNAL_SERVER_ERROR")
            .orShould().accessField(HttpServletResponse.class, "SC_INTERNAL_SERVER_ERROR")
            .because("a 500 means a bug in the service: outage scenarios expect 503, bad upstream answers 502"
                    + " (literal statusCode(500) is caught by PMD NoInternalServerError)");
}
