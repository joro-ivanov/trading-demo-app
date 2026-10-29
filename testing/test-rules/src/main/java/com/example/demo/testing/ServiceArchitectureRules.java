package com.example.demo.testing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Production architecture rules shared by every backend service (docs/DESIGN.md §13.1). A service runs them
 * from a class importing its production classes only ({@code ImportOption.DoNotIncludeTests}) and adds its own
 * rules next to them. Owners are referenced by name so this module needs no Spring or servlet dependency.
 */
public final class ServiceArchitectureRules {

    private static final String HTTP_STATUS = "org.springframework.http.HttpStatus";
    private static final String SERVLET_RESPONSE = "jakarta.servlet.http.HttpServletResponse";
    private static final String RESPONSE_ENTITY = "org.springframework.http.ResponseEntity";

    @ArchTest
    public static final ArchRule controllersLiveInWeb = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..web..")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule repositoriesAreInterfacesInDomain = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().beInterfaces()
            .andShould().resideInAPackage("..domain..")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule noFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    public static final ArchRule noStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    public static final ArchRule noLegacyDate = noClasses()
            .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
            .because("use java.time");

    /**
     * 500 is reserved for unforeseen bugs: outages are 503, bad upstream answers 502 (common Downstream /
     * ApiErrorHandler). Checked on bytecode here and on source by PMD {@code NoInternalServerError}.
     */
    @ArchTest
    public static final ArchRule neverProduceInternalServerError = noClasses()
            .should().accessField(HTTP_STATUS, "INTERNAL_SERVER_ERROR")
            .orShould().accessField(SERVLET_RESPONSE, "SC_INTERNAL_SERVER_ERROR")
            .orShould().callMethod(RESPONSE_ENTITY, "internalServerError")
            .because("a 500 means a bug; outages are 503 and bad upstream answers 502 (see common Downstream)");

    private ServiceArchitectureRules() {
    }
}
