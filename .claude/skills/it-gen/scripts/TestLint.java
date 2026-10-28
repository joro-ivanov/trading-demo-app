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
 * callback-style branching/looping (forEach, ifPresent, …), and (in every test except ArchUnit rule classes) weak or
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

    private record Rule(String name, Pattern pattern, boolean assertionRule) {

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
            // Same as PMD NoInternalServerError: a 500 means a bug; outages are 503, bad upstream answers 502.
            new Rule("expects HTTP 500 (outage = 503, bad upstream answer = 502)", Pattern.compile(
                    "statusCode\\s*\\(\\s*500\\s*\\)|(?<![\\w$])status\\s*\\(\\s*500\\s*\\)|INTERNAL_SERVER_ERROR|isInternalServerError|internalServerError\\s*\\("), true),
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
                // Assertion rules apply to every test, except the ArchUnit rule classes (their predicates call
                // Set.contains / String.startsWith, which a regex cannot tell from AssertJ).
                if (rule.assertionRule() && file.toString().endsWith("ArchitectureTest.java")) {
                    continue;
                }
                Matcher m = rule.pattern().matcher(line);
                if (m.find()) {
                    out.add(file + ":" + (i + 1) + ": " + rule.name() + ": " + lines[i].trim());
                }
            }
        }
        for (int i : magicValueLines(lines)) {
            out.add(file + ":" + (i + 1) + ": magic value (name it: constant, local variable or annotation data table): "
                    + lines[i].trim());
        }
        return out;
    }

    // ---- no magic values (approximates PMD NoMagicValuesInTests in build/pmd/no-magic-values.xml) ----

    private static final Pattern FINAL_DECLARATION = Pattern.compile("\\bfinal\\b[^=;(]*=");
    private static final Pattern SIMPLE_LOCAL = Pattern.compile(
            "^\\s*[\\w.<>\\[\\]?, ]+\\s+[A-Za-z_]\\w*\\s*=\\s*[^(;]*;\\s*$");
    private static final Pattern PROSE_ARGUMENT = Pattern.compile(
            "(?:\\.(?:step|as|describedAs|withFailMessage|overridingErrorMessage)"
                    + "|new (?:AssertionError|IllegalStateException|IllegalArgumentException))"
                    + "\\(\\s*\"S\"(?:\\s*\\+\\s*[^,)\"]*(?:\"S\")?)*");
    private static final Pattern NON_EMPTY_STRING = Pattern.compile("\"S\"");
    private static final Pattern NUMBER = Pattern.compile(
            "(?<![\\w$.])(\\d[\\d_]*(?:\\.\\d+)?)(?:[eE][+-]?\\d+)?[lLfFdD]?(?![\\w.])");

    /**
     * Lines (0-based) with a string other than "" or a number other than 0/1, outside annotations, final
     * declarations (any depth, multi-line), simple local-variable initializers, and prose arguments.
     */
    private static List<Integer> magicValueLines(String[] lines) {
        List<Integer> hits = new ArrayList<>();
        int annotationDepth = 0;
        boolean inFinalInitializer = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (annotationDepth > 0) {
                annotationDepth += depthChange(line);
                continue;
            }
            if (trimmed.startsWith("@")) {
                annotationDepth = Math.max(0, depthChange(line));
                continue;
            }
            if (inFinalInitializer) {
                inFinalInitializer = !line.contains(";");
                continue;
            }
            if (trimmed.startsWith("import ") || trimmed.startsWith("package ")) {
                continue;
            }
            if (FINAL_DECLARATION.matcher(line).find()) {
                inFinalInitializer = !line.contains(";");
                continue;
            }
            if (SIMPLE_LOCAL.matcher(line).matches()) {
                continue;
            }
            String code = PROSE_ARGUMENT.matcher(line).replaceAll("");
            if (NON_EMPTY_STRING.matcher(code).find() || hasMagicNumber(code)) {
                hits.add(i);
            }
        }
        return hits;
    }

    private static boolean hasMagicNumber(String code) {
        Matcher m = NUMBER.matcher(code);
        while (m.find()) {
            double value = Double.parseDouble(m.group(1).replace("_", ""));
            if (value != 0 && value != 1) {
                return true;
            }
        }
        return false;
    }

    private static int depthChange(String line) {
        return (int) (line.chars().filter(ch -> ch == '(' || ch == '{').count()
                - line.chars().filter(ch -> ch == ')' || ch == '}').count());
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
                b.append("\"S\"");
                blank(b, src, i + 3, end);
                i = end;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != c && src.charAt(j) != '\n') {
                    j += src.charAt(j) == '\\' ? 2 : 1;
                }
                // Keep "" for an empty string (allowed) and "S" for any other, so magic values stay visible.
                b.append(c).append(c == '"' && j > i + 1 ? "S" : "").append(c);
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
