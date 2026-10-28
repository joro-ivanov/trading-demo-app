package com.example.demo.order;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Service architecture rules (docs/DESIGN.md §13.1). Analyzes production classes only.
 */
@AnalyzeClasses(packages = "com.example.demo.order", importOptions = ImportOption.DoNotIncludeTests.class)
class OrderArchitectureTest {

    @ArchTest
    static final ArchRule controllersLiveInWeb = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..web..");

    @ArchTest
    static final ArchRule repositoriesAreInterfacesInDomain = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().beInterfaces()
            .andShould().resideInAPackage("..domain..");

    @ArchTest
    static final ArchRule noFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noLegacyDate = noClasses()
            .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
            .because("use java.time");
    /** 500 is reserved for unforeseen bugs: outages are 503, bad upstream answers 502 (common Downstream / ApiErrorHandler). */
    @ArchTest
    static final ArchRule neverProduceInternalServerError = noClasses()
            .should().accessField(HttpStatus.class, "INTERNAL_SERVER_ERROR")
            .orShould().accessField(HttpServletResponse.class, "SC_INTERNAL_SERVER_ERROR")
            .orShould().callMethod(ResponseEntity.class, "internalServerError")
            .because("a 500 means a bug; outages are 503 and bad upstream answers 502 (see common Downstream)");
}
