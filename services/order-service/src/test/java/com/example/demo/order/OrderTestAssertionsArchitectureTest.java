package com.example.demo.order;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Every test in this module (unit, ArchUnit and integration tests; harness/ plumbing and the
 * flagship's Stack excluded) asserts exact values. Generated from
 * .claude/skills/it-gen/TestAssertionsArchitectureTest.template.java; do not edit per module.
 */
@AnalyzeClasses(packages = "com.example.demo.order", importOptions = ImportOption.OnlyIncludeTests.class)
class OrderTestAssertionsArchitectureTest {

    /**
     * Partial, range and presence-only assertions. A test states the exact expected value instead:
     * isEqualTo / containsExactly / containsExactlyInAnyOrder / isEqualByComparingTo / hasSize / isEmpty,
     * or usingRecursiveComparison().ignoringFields(...) for server-generated fields (ids, timestamps)
     * that are asserted exactly elsewhere (e.g. the id against the persisted row).
     */
    private static final Set<String> WEAK_ASSERTJ = Set.of(
            "contains", "containsAll", "containsAnyOf", "containsAnyElementsOf", "containsSequence",
            "containsSubsequence", "containsOnlyOnce", "containsIgnoringCase", "containsIgnoringWhitespaces",
            "containsIgnoringNewLines", "containsPattern", "containsKey", "containsKeys", "containsEntry",
            "containsEntries", "containsValue", "containsValues", "containsOnlyKeys",
            "doesNotContain", "doesNotContainAnyElementsOf", "doesNotContainKey", "doesNotContainEntry",
            "startsWith", "endsWith", "matches", "doesNotMatch",
            "anyMatch", "allMatch", "noneMatch", "anySatisfy", "allSatisfy", "noneSatisfy", "haveAtLeast",
            "haveAtMost", "areAtLeast", "areAtMost",
            "hasSizeGreaterThan", "hasSizeGreaterThanOrEqualTo", "hasSizeLessThan", "hasSizeLessThanOrEqualTo",
            "hasSizeBetween",
            "isGreaterThan", "isGreaterThanOrEqualTo", "isLessThan", "isLessThanOrEqualTo", "isBetween",
            "isStrictlyBetween", "isCloseTo", "isNotCloseTo", "isPositive", "isNegative", "isNotPositive",
            "isNotNegative", "isNotZero",
            "isAfter", "isBefore", "isAfterOrEqualTo", "isBeforeOrEqualTo", "isInThePast", "isInTheFuture",
            "isToday", "isCloseToUtcNow",
            "isNotNull", "isNotEmpty", "isNotBlank", "isNotEqualTo", "isIn", "isNotIn", "isInstanceOfAny",
            "isNotSameAs");

    private static final Set<String> WEAK_HAMCREST = Set.of(
            "containsString", "containsStringIgnoringCase", "startsWith", "endsWith", "matchesPattern",
            "matchesRegex", "greaterThan", "greaterThanOrEqualTo", "lessThan", "lessThanOrEqualTo", "closeTo",
            "notNullValue", "anything", "hasItem", "hasItems", "hasKey", "hasEntry", "hasValue", "not",
            "either", "anyOf", "oneOf", "isIn", "isOneOf", "everyItem", "hasSize");

    private static final String WEAK_ASSERTION_LABEL = "weak assertion ";

    private static final Set<String> WEAK_WIREMOCK = Set.of("containing", "notContaining", "matching", "notMatching");

    @ArchTest
    static final ArchRule testsAssertExactValues = noClasses()
            .that().resideOutsideOfPackage("..harness..")
            .and().doNotHaveSimpleName("Stack")
            .should().callMethodWhere(weakAssertion("AssertJ", "org.assertj", WEAK_ASSERTJ))
            .orShould().callMethodWhere(weakAssertion("Hamcrest", "org.hamcrest", WEAK_HAMCREST))
            .orShould().callMethodWhere(weakAssertion("WireMock", "com.github.tomakehurst.wiremock", WEAK_WIREMOCK))
            .because("tests are deterministic and assert exact values: no contains/range/presence-only assertions");

    private static DescribedPredicate<JavaMethodCall> weakAssertion(String library, String packagePrefix,
                                                                    Set<String> names) {
        DescribedPredicate<JavaMethodCall> ownedByLibrary = DescribedPredicate.describe(
                library, call -> call.getTargetOwner().getPackageName().startsWith(packagePrefix));
        DescribedPredicate<JavaMethodCall> weakName = DescribedPredicate.describe(
                WEAK_ASSERTION_LABEL + new TreeSet<>(names), call -> names.contains(call.getName()));
        return ownedByLibrary.and(weakName);
    }
}
