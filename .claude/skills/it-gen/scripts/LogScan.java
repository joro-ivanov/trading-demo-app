import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Extracts every ERROR / WARN log entry and every exception (with its cause chain) from the output
 * of an integration-test run, so a green run that logged an unexpected exception is still noticed.
 * Plain JDK, no dependencies.
 *
 * <pre>
 *   java .claude/skills/it-gen/scripts/LogScan.java [label=]path [[label=]path ...]
 * </pre>
 *
 * A path is a {@code target/failsafe-reports} directory (its {@code *-output.txt} files, written when
 * the run used {@code -Dmaven.test.redirectTestOutputToFile=true}) or a single log file. Use labels to
 * compare reruns: {@code run1=... run2=... run3=...}. Maven's own {@code [INFO]/[ERROR]} lines are
 * ignored, because they are build output, not service or test logs.
 *
 * Identical findings are grouped (ids, numbers and timestamps normalised) with a count per label and
 * test class. Nothing is filtered out as "noise": deciding whether a finding is expected is a
 * judgement for the reviewer, not for this script. Prints JSON.
 *
 * Container logs ({@code target/container-logs/<name>.log}, written by Testcontainers' Slf4jLogConsumer) are
 * read in their native formats: Postgres ({@code ERROR/FATAL/PANIC/WARNING}, with DETAIL/STATEMENT/HINT),
 * Kafka log4j ({@code [ts] LEVEL msg (logger)}) and Redis ({@code #} = warning). A request that ended in an
 * unhandled exception is flagged {@code unhandledHttp500: true}: the service answered 500, which is a bug.
 */
public class LogScan {

    private static final Pattern LEVEL_LINE = Pattern.compile(
            "^\\S+\\s+(ERROR|WARN)\\s+(\\S+)\\s+-\\s?(.*)$");
    private static final Pattern BOOT_LEVEL_LINE = Pattern.compile(
            // Spring Boot default console format; Boot 3.4+ prints two bracket groups: [app] [thread].
            "^\\S+\\s+(ERROR|WARN)\\s+\\d+\\s+---\\s+(?:\\[[^]]*]\\s*)+(\\S+)\\s*:\\s?(.*)$");
    private static final Pattern ANY_LEVEL_LINE = Pattern.compile("^\\S+\\s+(INFO|DEBUG|TRACE|ERROR|WARN)\\s");
    private static final Pattern EXCEPTION_LINE = Pattern.compile(
            "^(Caused by: |Suppressed: |\\s+Caused by: |\\s+Suppressed: )?"
                    + "((?:[a-z_$][\\w$]*\\.)+[A-Z][\\w$]*(?:Exception|Error|Throwable|Failure))(?::\\s*(.*))?$");
    private static final Pattern FRAME_LINE = Pattern.compile("^\\s+at ([\\w$.<>]+)\\(([^)]*)\\)");
    private static final Pattern MAVEN_LINE = Pattern.compile("^\\[(INFO|WARNING|ERROR|DEBUG)]");
    // Container logs (Testcontainers Slf4jLogConsumer writes "STDOUT: <line>" / "STDERR: <line>").
    private static final Pattern CONSUMER_PREFIX = Pattern.compile("^(STDOUT|STDERR): ?");
    private static final Pattern POSTGRES_LINE = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2} [\\d:.]+ \\S+ \\[\\d+] (PANIC|FATAL|ERROR|WARNING|LOG|INFO|NOTICE|DEBUG\\d?)"
                    + ":\\s+(.*)$");
    private static final Pattern POSTGRES_CONTINUATION = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2} [\\d:.]+ \\S+ \\[\\d+] (DETAIL|STATEMENT|HINT|CONTEXT):\\s+(.*)$");
    private static final Pattern KAFKA_LINE = Pattern.compile(
            "^\\[[\\d\\- :,]+] (ERROR|WARN|INFO|DEBUG|TRACE) (.*?)(?: \\(([\\w.$]+)\\))?$");
    private static final Pattern REDIS_LINE = Pattern.compile("^\\d+:[XCMS] \\d+ \\w+ \\d{4} [\\d:.]+ ([.\\-*#]) (.*)$");
    private static final String APP_PACKAGE = "com.example.demo.";

    private static final class Entry {
        String level;
        String logger = "";
        String message = "";
        final List<String> exceptions = new ArrayList<>();
        String firstAppFrame;
        String source;
        String testClass;
        int line;
    }

    private static final class Finding {
        String level;
        String logger;
        String message;
        List<String> exceptions;
        String firstAppFrame;
        String example;
        boolean unhandledHttp500;
        final Map<String, Integer> countByLabel = new TreeMap<>();
        final Map<String, Integer> countByTestClass = new TreeMap<>();
        int total;
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("usage: java LogScan.java [label=]path ...");
            System.exit(2);
        }
        Map<String, Finding> findings = new LinkedHashMap<>();
        List<String> scanned = new ArrayList<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            String label = eq > 0 ? arg.substring(0, eq) : "run";
            Path path = Path.of(eq > 0 ? arg.substring(eq + 1) : arg);
            for (Path file : files(path)) {
                scanned.add(label + ":" + file);
                for (Entry entry : parse(file)) {
                    String key = entry.level + "|" + entry.logger + "|" + normalise(entry.message) + "|"
                            + String.join(">", entry.exceptions) + "|" + entry.firstAppFrame;
                    Finding f = findings.computeIfAbsent(key, k -> newFinding(entry));
                    f.countByLabel.merge(label, 1, Integer::sum);
                    f.countByTestClass.merge(entry.testClass, 1, Integer::sum);
                    f.total++;
                }
            }
        }
        List<Finding> sorted = new ArrayList<>(findings.values());
        sorted.sort((a, b) -> rank(a.level) != rank(b.level) ? rank(a.level) - rank(b.level) : b.total - a.total);
        print(scanned, sorted, args);
    }

    private static Finding newFinding(Entry e) {
        Finding f = new Finding();
        f.level = e.level;
        f.logger = e.logger;
        f.message = e.message;
        f.exceptions = e.exceptions;
        f.firstAppFrame = e.firstAppFrame;
        f.example = e.source + ":" + e.line;
        f.unhandledHttp500 = e.logger.contains("dispatcherServlet") && e.message.contains("threw exception");
        return f;
    }

    private static int rank(String level) {
        return switch (level) {
            case "ERROR" -> 0;
            case "EXCEPTION" -> 1;
            default -> 2;
        };
    }

    private static List<Path> files(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        if (!Files.isDirectory(path)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(path)) {
            return walk.filter(p -> p.getFileName().toString().endsWith("-output.txt") || isContainerLog(p))
                    .sorted().toList();
        }
    }

    private static boolean isContainerLog(Path file) {
        return file.getParent() != null && file.getParent().getFileName().toString().equals("container-logs")
                && file.getFileName().toString().endsWith(".log");
    }

    /** One container log line in its native format → [level, logger, message], or null if not a level line. */
    private static String[] containerLevel(String container, String line) {
        Matcher pg = POSTGRES_LINE.matcher(line);
        if (pg.matches()) {
            String level = switch (pg.group(1)) {
                case "PANIC", "FATAL", "ERROR" -> "ERROR";
                case "WARNING" -> "WARN";
                default -> "INFO";
            };
            return new String[] {level, container + ":" + pg.group(1), pg.group(2)};
        }
        Matcher kafka = KAFKA_LINE.matcher(line);
        if (kafka.matches()) {
            String logger = kafka.group(3) == null ? container : container + ":" + kafka.group(3);
            return new String[] {kafka.group(1), logger, kafka.group(2)};
        }
        Matcher redis = REDIS_LINE.matcher(line);
        if (redis.matches()) {
            return new String[] {redis.group(1).equals("#") ? "WARN" : "INFO", container, redis.group(2)};
        }
        return null;
    }

    private static List<Entry> parse(Path file) throws IOException {
        boolean container = isContainerLog(file);
        String containerName = file.getFileName().toString().replaceFirst("\\.log$", "");
        String testClass = container
                ? "container:" + containerName
                : file.getFileName().toString().replaceFirst("-output\\.txt$", "");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<Entry> entries = new ArrayList<>();
        Entry current = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = container ? CONSUMER_PREFIX.matcher(lines.get(i)).replaceFirst("") : lines.get(i);
            if (container) {
                Matcher continuation = POSTGRES_CONTINUATION.matcher(line);
                if (continuation.matches() && current != null) {
                    current.message += " | " + continuation.group(1) + ": " + continuation.group(2);
                    continue;
                }
                String[] level = containerLevel(containerName, line);
                if (level != null) {
                    current = null;
                    if (level[0].equals("ERROR") || level[0].equals("WARN")) {
                        current = new Entry();
                        current.level = level[0];
                        current.logger = level[1];
                        current.message = level[2].trim();
                        current.source = file.toString();
                        current.testClass = testClass;
                        current.line = i + 1;
                        entries.add(current);
                    }
                    continue;
                }
            }
            if (MAVEN_LINE.matcher(line).find()) {
                current = null;
                continue;
            }
            Matcher level = LEVEL_LINE.matcher(line);
            Matcher boot = BOOT_LEVEL_LINE.matcher(line);
            // Boot format first: its lines also fit the looser logback pattern (with the PID as "logger").
            Matcher levelMatch = boot.matches() ? boot : level.matches() ? level : null;
            if (levelMatch != null) {
                current = new Entry();
                current.level = levelMatch.group(1);
                current.logger = levelMatch.group(2);
                current.message = levelMatch.group(3).trim();
                current.source = file.toString();
                current.testClass = testClass;
                current.line = i + 1;
                entries.add(current);
                continue;
            }
            if (ANY_LEVEL_LINE.matcher(line).find()) {
                current = null;
                continue;
            }
            Matcher ex = EXCEPTION_LINE.matcher(line);
            if (ex.matches()) {
                if (current == null) {
                    current = new Entry();
                    current.level = "EXCEPTION";
                    current.message = "(uncaught / printed stack trace)";
                    current.source = file.toString();
                    current.testClass = testClass;
                    current.line = i + 1;
                    entries.add(current);
                }
                String text = ex.group(3) == null ? "" : ": " + normalise(ex.group(3));
                current.exceptions.add(ex.group(2) + abbreviate(text));
                continue;
            }
            Matcher frame = FRAME_LINE.matcher(line);
            if (frame.find() && current != null && current.firstAppFrame == null
                    && frame.group(1).startsWith(APP_PACKAGE)) {
                current.firstAppFrame = frame.group(1) + "(" + frame.group(2) + ")";
            }
        }
        return entries;
    }

    private static String normalise(String message) {
        return message
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "<uuid>")
                .replaceAll("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z?", "<instant>")
                .replaceAll("(?<![A-Za-z])\\d+(\\.\\d+)?(?![A-Za-z])", "<n>")
                .replaceAll("@[0-9a-f]{6,}", "@<hash>");
    }

    private static String abbreviate(String text) {
        return text.length() > 240 ? text.substring(0, 240) + "…" : text;
    }

    private static void print(List<String> scanned, List<Finding> findings, String[] args) {
        StringBuilder b = new StringBuilder("{\n");
        b.append("  \"inputs\": ").append(quoteList(List.of(args))).append(",\n");
        b.append("  \"filesScanned\": ").append(scanned.size()).append(",\n");
        long errors = findings.stream().filter(f -> f.level.equals("ERROR")).count();
        long exceptions = findings.stream().filter(f -> f.level.equals("EXCEPTION")).count();
        long warns = findings.stream().filter(f -> f.level.equals("WARN")).count();
        b.append("  \"distinct\": {\"ERROR\": ").append(errors).append(", \"EXCEPTION\": ").append(exceptions)
                .append(", \"WARN\": ").append(warns).append("},\n");
        b.append("  \"findings\": [");
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            b.append(i == 0 ? "\n" : ",\n").append("    {")
                    .append("\"level\": ").append(quote(f.level))
                    .append(", \"logger\": ").append(quote(f.logger))
                    .append(", \"message\": ").append(quote(abbreviate(f.message)))
                    .append(", \"exceptions\": ").append(quoteList(f.exceptions))
                    .append(", \"firstAppFrame\": ").append(f.firstAppFrame == null ? "null" : quote(f.firstAppFrame))
                    .append(", \"unhandledHttp500\": ").append(f.unhandledHttp500)
                    .append(", \"total\": ").append(f.total)
                    .append(", \"byRun\": ").append(quoteMap(f.countByLabel))
                    .append(", \"byTestClass\": ").append(quoteMap(f.countByTestClass))
                    .append(", \"example\": ").append(quote(f.example))
                    .append("}");
        }
        b.append(findings.isEmpty() ? "]\n}" : "\n  ]\n}");
        System.out.println(b);
    }

    private static String quoteList(List<String> values) {
        return values.stream().map(LogScan::quote).reduce((a, c) -> a + ", " + c).map(s -> "[" + s + "]").orElse("[]");
    }

    private static String quoteMap(Map<String, Integer> map) {
        return map.entrySet().stream().map(e -> quote(e.getKey()) + ": " + e.getValue())
                .reduce((a, c) -> a + ", " + c).map(s -> "{" + s + "}").orElse("{}");
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\t' -> b.append("\\t");
                default -> b.append(ch < 0x20 ? String.format("\\u%04x", (int) ch) : String.valueOf(ch));
            }
        }
        return b.append('"').toString();
    }
}
