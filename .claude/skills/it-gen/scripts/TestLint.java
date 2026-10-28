import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fast "no logic in tests" check for the /it-gen workflow. It catches what the PMD rule in
 * build/pmd/test-conventions.xml does not: boolean operators, negation, comparisons and
 * callback-style branching/looping (forEach, ifPresent, …), and (in *IT classes) weak or
 * non-deterministic assertions (contains, ranges, isNotNull, …), mirroring the ArchUnit rule. Plain JDK, no dependencies.
 *
 * <pre>
 *   java .claude/skills/it-gen/scripts/TestLint.java &lt;file-or-dir&gt; [&lt;file-or-dir&gt;…]
 * </pre>
 *
 * Checks *IT.java and *Test.java files; skips harness/ helpers (also excluded from PMD), which may contain
 * plumbing. Comments and string/char literals are ignored. Exit code 1 when violations exist.
 */
public class TestLint {

    private record Rule(String name, Pattern pattern, boolean integrationTestsOnly) {

        Rule(String name, Pattern pattern) {
            this(name, pattern, false);
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("if/else", Pattern.compile("\\bif\\s*\\(|\\belse\\b")),
            new Rule("loop", Pattern.compile("\\bfor\\s*\\(|\\bwhile\\s*\\(|\\bdo\\s*\\{")),
            new Rule("switch", Pattern.compile("\\bswitch\\s*\\(")),
            new Rule("try/catch (use assertThatThrownBy)", Pattern.compile("\\btry\\s*[{(]|\\bcatch\\s*\\(")),
            new Rule("ternary", Pattern.compile("(?<![<,]\\s{0,3})\\?(?!\\s*(?:extends|super)\\b)(?!\\s*[>,])")),
            new Rule("boolean operator && / ||", Pattern.compile("&&|\\|\\|")),
            new Rule("negation !", Pattern.compile("!(?!=)")),
            new Rule("comparison == / != (assert instead)", Pattern.compile("[=!]=")),
            new Rule("callback loop/branch", Pattern.compile(
                    "\\.(forEach|forEachOrdered|ifPresent|ifPresentOrElse|orElseGet|filter|takeWhile|dropWhile)\\s*\\(")),
            // Same list as the <Svc>IntegrationTestArchitectureTest ArchUnit rule (the authority).
            new Rule("weak/non-deterministic assertion (assert the exact value)", Pattern.compile(
                    "(?<![\\w$])(contains|containsAll|containsAnyOf|containsAnyElementsOf|containsSequence"
                    + "|containsSubsequence|containsOnlyOnce|containsIgnoringCase|containsIgnoringWhitespaces"
                    + "|containsIgnoringNewLines|containsPattern|containsKey|containsKeys|containsEntry|containsEntries"
                    + "|containsValue|containsValues|containsOnlyKeys|doesNotContain|doesNotContainAnyElementsOf"
                    + "|doesNotContainKey|doesNotContainEntry|startsWith|endsWith|matches|doesNotMatch|anyMatch|allMatch"
                    + "|noneMatch|anySatisfy|allSatisfy|noneSatisfy|haveAtLeast|haveAtMost|areAtLeast|areAtMost"
                    + "|hasSizeGreaterThan|hasSizeGreaterThanOrEqualTo|hasSizeLessThan|hasSizeLessThanOrEqualTo"
                    + "|hasSizeBetween|isGreaterThan|isGreaterThanOrEqualTo|isLessThan|isLessThanOrEqualTo|isBetween"
                    + "|isStrictlyBetween|isCloseTo|isNotCloseTo|isPositive|isNegative|isNotPositive|isNotNegative"
                    + "|isNotZero|isAfter|isBefore|isAfterOrEqualTo|isBeforeOrEqualTo|isInThePast|isInTheFuture|isToday"
                    + "|isCloseToUtcNow|isNotNull|isNotEmpty|isNotBlank|isNotEqualTo|isIn|isNotIn|isInstanceOfAny"
                    + "|isNotSameAs|containsString|containsStringIgnoringCase|matchesPattern|matchesRegex|greaterThan"
                    + "|greaterThanOrEqualTo|lessThan|lessThanOrEqualTo|closeTo|notNullValue|anything|hasItem|hasItems"
                    + "|hasKey|hasEntry|hasValue|not|either|anyOf|oneOf|isOneOf|everyItem|containing|notContaining"
                    + "|matching|notMatching)\\s*\\("), true));

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("usage: java TestLint.java <file-or-dir>...");
            System.exit(2);
        }
        List<String> violations = new ArrayList<>();
        for (String arg : args) {
            for (Path file : testFiles(Path.of(arg))) {
                violations.addAll(check(file));
            }
        }
        violations.forEach(System.out::println);
        System.out.println(violations.isEmpty() ? "TestLint: OK" : "TestLint: " + violations.size() + " violation(s)");
        System.exit(violations.isEmpty() ? 0 : 1);
    }

    private static List<Path> testFiles(Path path) throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(path)) {
            return files.filter(f -> {
                String s = f.toString().replace('\\', '/');
                return (s.endsWith("IT.java") || s.endsWith("Test.java"))
                        && !s.contains("/harness/");
            }).sorted().toList();
        }
    }

    private static List<String> check(Path file) throws IOException {
        String[] lines = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8)).split("\n", -1);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replaceAll("^\\s*(import|package)\\b.*", "");
            for (Rule rule : RULES) {
                if (rule.integrationTestsOnly() && !file.toString().endsWith("IT.java")) {
                    continue;
                }
                Matcher m = rule.pattern().matcher(line);
                if (m.find()) {
                    out.add(file + ":" + (i + 1) + ": " + rule.name() + ": " + lines[i].trim());
                }
            }
        }
        return out;
    }

    /** Blanks out comments, string/text-block and char literals, keeping newlines for line numbers. */
    private static String stripCommentsAndLiterals(String src) {
        StringBuilder b = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (src.startsWith("//", i)) {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (src.startsWith("/*", i)) {
                int end = src.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                blank(b, src, i, end);
                i = end;
            } else if (src.startsWith("\"\"\"", i)) {
                int end = src.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end + 3;
                b.append("\"\"");
                blank(b, src, i + 2, end);
                i = end;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != c && src.charAt(j) != '\n') {
                    j += src.charAt(j) == '\\' ? 2 : 1;
                }
                b.append(c).append(c);
                i = Math.min(j + 1, n);
            } else {
                b.append(c);
                i++;
            }
        }
        return b.toString();
    }

    private static void blank(StringBuilder b, String src, int from, int to) {
        for (int k = from; k < to; k++) {
            b.append(src.charAt(k) == '\n' ? '\n' : ' ');
        }
    }
}
