package com.example.demo.notification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.example.demo.testing.ServiceArchitectureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

/** Service architecture rules (docs/DESIGN.md §13.1): the shared ones plus this service's own. Production classes only. */
@AnalyzeClasses(packages = "com.example.demo.notification", importOptions = ImportOption.DoNotIncludeTests.class)
class NotificationArchitectureTest {

    @ArchTest
    static final ArchTests serviceRules = ArchTests.in(ServiceArchitectureRules.class);

    @ArchTest
    static final ArchRule kafkaListenersLiveInConsume = classes()
            .that().haveSimpleNameEndingWith("Listeners")
            .should().resideInAPackage("..consume..");
}
