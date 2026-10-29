package com.example.demo.spike;

import com.example.demo.testing.TestAssertionRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

/** The shared test rules (module test-rules) applied to every test in this module. */
@AnalyzeClasses(packages = "com.example.demo.spike", importOptions = ImportOption.OnlyIncludeTests.class)
class SpikeTestArchitectureTest {

    @ArchTest
    static final ArchTests testAssertions = ArchTests.in(TestAssertionRules.class);
}
