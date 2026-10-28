import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Deterministic change detection for the /it-gen workflow. Decides which backend services (and
 * which of their endpoints/listeners) need integration-test work, so unchanged services are never
 * analysed. Plain JDK, no dependencies: {@code java .claude/skills/it-gen/scripts/Scope.java [args]}.
 *
 * <pre>
 *   (no args)                 changes since merge-base(HEAD, origin/main) + uncommitted + untracked
 *   --base &lt;ref&gt;             changes since &lt;ref&gt; + uncommitted + untracked
 *   --all                     every service, full generation
 *   &lt;service&gt; [&lt;service&gt;…]   only these services, full generation ("order" or "order-service")
 * </pre>
 *
 * Prints one JSON object to stdout. Rules:
 * <ul>
 *   <li>services/&lt;svc&gt;/src/main/** changed → that service is in scope;</li>
 *   <li>common/src/main/** changed → only services whose main code imports an affected common class;</li>
 *   <li>a provider's web/ code changed → consumers that call it over HTTP get a stub check;</li>
 *   <li>a producer's Kafka payload/publisher changed → consumers of that topic get a contract check;</li>
 *   <li>test, docs, frontend, e2e and infra changes never put a service in scope.</li>
 * </ul>
 */
public class Scope {

    private static final Pattern CLASS_MAPPING = Pattern.compile(
            "@RequestMapping\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"([^\"]*)\"");
    private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch)Mapping(?:\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"([^\"]*)\"[^)]*\\))?");
    private static final Pattern LISTENER = Pattern.compile(
            "@KafkaListener\\([^)]*?topics\\s*=\\s*(\\{[^}]*\\}|\"[^\"]*\")");
    private static final Pattern SEND = Pattern.compile(
            "\\.send\\(\\s*(\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_.]*)");
    private static final Pattern STRING_CONSTANT = Pattern.compile(
            "static\\s+final\\s+String\\s+([A-Z_][A-Z0-9_]*)\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    private static Path root;

    public static void main(String[] args) throws Exception {
        root = Path.of(git("rev-parse", "--show-toplevel").trim());
        String base = null;
        boolean all = false;
        List<String> requested = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--base" -> base = args[++i];
                case "--all" -> all = true;
                default -> requested.add(normalize(args[i]));
            }
        }

        List<String> services = listServices();
        Map<String, ServiceScope> scopes = new LinkedHashMap<>();
        List<String> ignored = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<Map<String, Object>> stubChecks = new ArrayList<>();
        List<Map<String, Object>> eventChecks = new ArrayList<>();
        String mode;

        if (all || !requested.isEmpty()) {
            mode = all ? "all" : "requested";
            for (String svc : all ? services : requested) {
                if (!services.contains(svc)) {
                    fail("Unknown service '" + svc + "'. Known: " + services);
                }
                scope(scopes, svc).full = true;
                scope(scopes, svc).reasons.add(mode);
            }
        } else {
            mode = "diff";
            base = base == null ? defaultBase() : base;
            Set<String> changed = new TreeSet<>();
            changed.addAll(lines(git("diff", "--name-only", base)));
            changed.addAll(lines(git("ls-files", "--others", "--exclude-standard")));
            classify(changed, services, base, scopes, ignored, notes, stubChecks, eventChecks);
        }

        for (ServiceScope s : scopes.values()) {
            s.describe();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", mode);
        out.put("base", base);
        out.put("nothingToDo", scopes.isEmpty() && stubChecks.isEmpty() && eventChecks.isEmpty());
        out.put("services", scopes.values().stream().map(ServiceScope::toMap).toList());
        out.put("consumerStubChecks", stubChecks);
        out.put("eventContractChecks", eventChecks);
        out.put("notes", notes);
        out.put("ignoredFiles", ignored);
        System.out.println(json(out, 0));
    }

    private static void classify(Set<String> changed, List<String> services, String base,
                                 Map<String, ServiceScope> scopes, List<String> ignored, List<String> notes,
                                 List<Map<String, Object>> stubChecks, List<Map<String, Object>> eventChecks)
            throws IOException {
        Set<String> changedCommon = new LinkedHashSet<>();
        for (String file : changed) {
            if (file.startsWith("services/")) {
                String svc = file.split("/")[1];
                if (file.contains("/src/main/")) {
                    ServiceScope s = scope(scopes, svc);
                    s.changedFiles.add(file);
                    s.newService = !existsAt(base, "services/" + svc + "/pom.xml");
                } else if (file.endsWith("/pom.xml")) {
                    notes.add(file + " changed (build config only; no tests generated for it)");
                } else {
                    ignored.add(file);
                }
            } else if (file.startsWith("common/src/main/java/") && file.endsWith(".java")) {
                changedCommon.add(className(file));
            } else {
                ignored.add(file);
            }
        }

        if (!changedCommon.isEmpty()) {
            Set<String> affected = commonClosure(changedCommon);
            for (String svc : services) {
                for (Path f : javaFiles(root.resolve("services/" + svc + "/src/main/java"))) {
                    String src = read(f);
                    for (String fqcn : affected) {
                        String pkg = fqcn.substring(0, fqcn.lastIndexOf('.'));
                        if (src.contains("import " + fqcn + ";") || src.contains("import " + pkg + ".*;")) {
                            scope(scopes, svc).reasons.add("uses changed common class " + fqcn
                                    + " (in " + root.relativize(f) + ")");
                        }
                    }
                }
            }
        }

        for (ServiceScope s : List.copyOf(scopes.values())) {
            if (s.changedFiles.isEmpty()) {
                continue;
            }
            s.reasons.add(s.newService ? "new service" : "main code changed");
            boolean webChanged = s.changedFiles.stream().anyMatch(f -> f.contains("/web/"));
            if (webChanged) {
                for (String consumer : httpConsumersOf(s.name, services)) {
                    Map<String, Object> check = new LinkedHashMap<>();
                    check.put("consumer", consumer);
                    check.put("provider", s.name);
                    check.put("changedProviderFiles", s.changedFiles.stream().filter(f -> f.contains("/web/")).toList());
                    stubChecks.add(check);
                }
            }
            for (String topic : topicsProducedByChange(s)) {
                for (String consumer : services) {
                    if (consumer.equals(s.name)) {
                        continue;
                    }
                    for (Path f : javaFiles(root.resolve("services/" + consumer + "/src/main/java"))) {
                        if (listenerTopics(read(f)).contains(topic)) {
                            Map<String, Object> check = new LinkedHashMap<>();
                            check.put("consumer", consumer);
                            check.put("producer", s.name);
                            check.put("topic", topic);
                            check.put("listenerFile", root.relativize(f).toString());
                            eventChecks.add(check);
                        }
                    }
                }
            }
        }
    }

    /** Common classes changed directly, plus common classes that reference them (fixpoint). */
    private static Set<String> commonClosure(Set<String> changed) throws IOException {
        Set<String> affected = new LinkedHashSet<>(changed);
        List<Path> commonFiles = javaFiles(root.resolve("common/src/main/java"));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Path f : commonFiles) {
                String fqcn = className(root.relativize(f).toString());
                if (affected.contains(fqcn)) {
                    continue;
                }
                String src = read(f);
                for (String a : List.copyOf(affected)) {
                    if (Pattern.compile("\\b" + simpleName(a) + "\\b").matcher(src).find()) {
                        affected.add(fqcn);
                        grew = true;
                        break;
                    }
                }
            }
        }
        return affected;
    }

    /** Services whose application.yaml has a top-level key with a base-url pointing at the provider. */
    private static List<String> httpConsumersOf(String provider, List<String> services) throws IOException {
        List<String> consumers = new ArrayList<>();
        for (String svc : services) {
            Path yaml = root.resolve("services/" + svc + "/src/main/resources/application.yaml");
            if (svc.equals(provider) || !Files.exists(yaml)) {
                continue;
            }
            String topKey = null;
            for (String line : Files.readAllLines(yaml)) {
                if (line.matches("^[A-Za-z][\\w-]*:\\s*$")) {
                    topKey = line.substring(0, line.indexOf(':'));
                } else if (topKey != null && line.matches("^\\s+base-url:.*")
                        && (provider.equals(topKey) || provider.equals(topKey + "-service"))) {
                    consumers.add(svc);
                }
            }
        }
        return consumers;
    }

    /** Topics published by the changed files, or by publishers that reference a changed class. */
    private static Set<String> topicsProducedByChange(ServiceScope s) throws IOException {
        Set<String> topics = new TreeSet<>();
        Set<String> changedNames = new LinkedHashSet<>();
        for (String f : s.changedFiles) {
            if (f.endsWith(".java")) {
                changedNames.add(simpleName(className(f)));
            }
        }
        for (Path f : javaFiles(root.resolve("services/" + s.name + "/src/main/java"))) {
            String src = read(f);
            boolean relevant = s.changedFiles.contains(root.relativize(f).toString())
                    || changedNames.stream().anyMatch(n -> Pattern.compile("\\b" + n + "\\b").matcher(src).find());
            if (relevant) {
                topics.addAll(sentTopics(src));
            }
        }
        return topics;
    }

    private static Set<String> sentTopics(String src) {
        Map<String, String> constants = new LinkedHashMap<>();
        Matcher c = STRING_CONSTANT.matcher(src);
        while (c.find()) {
            constants.put(c.group(1), c.group(2));
        }
        Set<String> topics = new TreeSet<>();
        Matcher m = SEND.matcher(src);
        while (m.find()) {
            String arg = m.group(1);
            String name = arg.substring(arg.lastIndexOf('.') + 1);
            if (arg.startsWith("\"")) {
                topics.add(arg.substring(1, arg.length() - 1));
            } else if (constants.containsKey(name)) {
                topics.add(constants.get(name));
            }
        }
        return topics;
    }

    private static Set<String> listenerTopics(String src) {
        Set<String> topics = new TreeSet<>();
        Matcher m = LISTENER.matcher(src);
        while (m.find()) {
            Matcher q = QUOTED.matcher(m.group(1));
            while (q.find()) {
                topics.add(q.group(1));
            }
        }
        return topics;
    }

    private static List<String> endpoints(String src) {
        Matcher cm = CLASS_MAPPING.matcher(src);
        String prefix = cm.find() ? cm.group(1) : "";
        List<String> eps = new ArrayList<>();
        Matcher m = METHOD_MAPPING.matcher(src);
        while (m.find()) {
            String path = m.group(2) == null ? "" : m.group(2);
            eps.add(m.group(1).toUpperCase() + " " + (prefix + path).replaceAll("//+", "/"));
        }
        return eps;
    }

    // ---------------------------------------------------------------------------------------------

    private static final class ServiceScope {
        final String name;
        final Set<String> reasons = new LinkedHashSet<>();
        final List<String> changedFiles = new ArrayList<>();
        boolean full;
        boolean newService;
        final List<Map<String, Object>> changedControllers = new ArrayList<>();
        final List<Map<String, Object>> changedListeners = new ArrayList<>();
        final List<String> changedMigrations = new ArrayList<>();
        final List<String> allEndpoints = new ArrayList<>();
        final List<String> allListenerTopics = new ArrayList<>();
        final List<String> integrationTests = new ArrayList<>();
        String legacyIT;
        String itBase;
        final List<String> stubFixtures = new ArrayList<>();
        final List<String> httpDependencies = new ArrayList<>();

        ServiceScope(String name) {
            this.name = name;
        }

        void describe() throws IOException {
            Path svc = root.resolve("services/" + name);
            for (Path f : javaFiles(svc.resolve("src/main/java"))) {
                String rel = root.relativize(f).toString();
                String src = read(f);
                boolean controller = src.contains("@RestController") || src.contains("@Controller");
                Set<String> topics = listenerTopics(src);
                if (controller) {
                    allEndpoints.addAll(endpoints(src));
                }
                allListenerTopics.addAll(topics);
                if (changedFiles.contains(rel) && controller) {
                    changedControllers.add(Map.of("file", rel, "endpoints", endpoints(src)));
                }
                if (changedFiles.contains(rel) && !topics.isEmpty()) {
                    changedListeners.add(Map.of("file", rel, "topics", List.copyOf(topics)));
                }
            }
            for (String f : changedFiles) {
                if (f.contains("/db/migration/")) {
                    changedMigrations.add(f);
                }
            }
            for (Path f : javaFiles(svc.resolve("src/test/java"))) {
                String rel = root.relativize(f).toString();
                String file = f.getFileName().toString();
                if (file.endsWith("ServiceIT.java")) {
                    legacyIT = rel;
                }
                if (file.endsWith("IT.java")) {
                    integrationTests.add(rel);
                }
                if (file.endsWith("ITBase.java")) {
                    itBase = rel;
                }
                if (rel.contains("/harness/stubs/")) {
                    stubFixtures.add(rel);
                }
            }
            Path yaml = svc.resolve("src/main/resources/application.yaml");
            if (Files.exists(yaml)) {
                String topKey = null;
                for (String line : Files.readAllLines(yaml)) {
                    if (line.matches("^[A-Za-z][\\w-]*:\\s*$")) {
                        topKey = line.substring(0, line.indexOf(':'));
                    } else if (topKey != null && line.matches("^\\s+(base-url|ws-url):.*")) {
                        httpDependencies.add(topKey + "." + line.trim());
                    }
                }
            }
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("dir", "services/" + name);
            m.put("reasons", List.copyOf(reasons));
            m.put("fullGeneration", full || newService);
            m.put("newService", newService);
            m.put("changedFiles", changedFiles);
            m.put("changedControllers", changedControllers);
            m.put("changedListeners", changedListeners);
            m.put("changedMigrations", changedMigrations);
            m.put("allEndpoints", allEndpoints);
            m.put("allListenerTopics", allListenerTopics);
            m.put("externalDependencies", httpDependencies);
            m.put("integrationTests", integrationTests);
            m.put("legacyServiceIT", legacyIT);
            m.put("itBase", itBase);
            m.put("stubFixtures", stubFixtures);
            return m;
        }
    }

    private static ServiceScope scope(Map<String, ServiceScope> scopes, String svc) {
        return scopes.computeIfAbsent(svc, ServiceScope::new);
    }

    private static List<String> listServices() throws IOException {
        try (Stream<Path> dirs = Files.list(root.resolve("services"))) {
            return dirs.filter(d -> Files.exists(d.resolve("pom.xml")))
                    .map(d -> d.getFileName().toString()).sorted().toList();
        }
    }

    private static String normalize(String svc) {
        return svc.endsWith("-service") ? svc : svc + "-service";
    }

    private static String defaultBase() {
        for (String ref : List.of("origin/main", "main")) {
            try {
                return git("merge-base", "HEAD", ref).trim();
            } catch (IllegalStateException ignoredRef) {
                // try the next candidate
            }
        }
        return "HEAD";
    }

    private static boolean existsAt(String ref, String path) {
        try {
            git("cat-file", "-e", ref + ":" + path);
            return true;
        } catch (IllegalStateException missing) {
            return false;
        }
    }

    private static String className(String path) {
        String p = path.substring(path.indexOf("/java/") + "/java/".length());
        return p.substring(0, p.length() - ".java".length()).replace('/', '.');
    }

    private static String simpleName(String fqcn) {
        return fqcn.substring(fqcn.lastIndexOf('.') + 1);
    }

    private static List<Path> javaFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(f -> f.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String read(Path f) throws IOException {
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    private static List<String> lines(String s) {
        return s.lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
    }

    private static String git(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(cmd).directory(root == null ? null : root.toFile())
                    .redirectErrorStream(true).start();
            String out;
            try (InputStream in = p.getInputStream()) {
                out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (p.waitFor() != 0) {
                throw new IllegalStateException(String.join(" ", cmd) + " failed: " + out.trim());
            }
            return out;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(2);
    }

    @SuppressWarnings("unchecked")
    private static String json(Object o, int indent) {
        String pad = "  ".repeat(indent + 1);
        String end = "  ".repeat(indent);
        if (o == null) {
            return "null";
        }
        if (o instanceof Boolean || o instanceof Number) {
            return o.toString();
        }
        if (o instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return "{}";
            }
            List<String> parts = new ArrayList<>();
            ((Map<String, Object>) map).forEach((k, v) -> parts.add(pad + quote(k) + ": " + json(v, indent + 1)));
            return "{\n" + String.join(",\n", parts) + "\n" + end + "}";
        }
        if (o instanceof Iterable<?> it) {
            List<String> parts = new ArrayList<>();
            it.forEach(v -> parts.add(pad + json(v, indent + 1)));
            return parts.isEmpty() ? "[]" : "[\n" + String.join(",\n", parts) + "\n" + end + "]";
        }
        return quote(o.toString());
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
