package com.example.demo.watchlist;

import com.example.demo.testing.ServiceArchitectureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

/** Service architecture rules (docs/DESIGN.md §13.1), shared by every service. Production classes only. */
@AnalyzeClasses(packages = "com.example.demo.watchlist", importOptions = ImportOption.DoNotIncludeTests.class)
class WatchlistArchitectureTest {

    @ArchTest
    static final ArchTests serviceRules = ArchTests.in(ServiceArchitectureRules.class);
}
