import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the GA baseline evidence without external dependencies: the version
 * manifest must equal the pins declared in the build files, hand-copied version
 * literals must agree with their pin, and capability ledger rows must be well formed.
 */
public final class CheckBaseline {
    private static final String VERSIONS = "docs/evidence/versions.tsv";
    private static final String LEDGER = "docs/evidence/capabilities.tsv";
    private static final String VERSIONS_HEADER = "id\tvalue\tsource\tnote";
    private static final List<String> LEDGER_COLUMNS = List.of("id", "area", "capability", "status", "owner",
            "revision", "fixture", "command", "ci", "limits", "date");
    private static final Set<String> STATUSES = Set.of("proposed", "spike", "implemented-unqualified",
            "qualified", "blocked", "deprecated");
    private static final Set<String> EVIDENCED = Set.of("implemented-unqualified", "qualified");
    /** Verification commands that are not root npm scripts. */
    private static final Set<String> COMMANDS = Set.of(
            "./mvnw install",
            "tooling/intellij: ./gradlew test",
            "tooling/vscode: npm test");
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");
    private static final Pattern REVISION = Pattern.compile("[0-9a-f]{7,40}\\+?");
    private static final Pattern NPM_SCRIPT = Pattern.compile("(?m)^\\s*\"([a-z:]+)\":\\s*\"");
    private static final String V = "([0-9]+(?:\\.[0-9]+)*(?:-SNAPSHOT)?)";

    /** A version declared once in a build file; group 1 of the first match is its value. */
    private record Pin(String id, String source, String regex, String note) {}

    /** Every match of group 1 in the file must equal the named pin's value. */
    private record Copy(String pin, String source, String regex) {}

    private static final List<Pin> PINS = List.of(
            new Pin("jx.framework", "pom.xml", "<artifactId>jx-parent</artifactId><version>" + V + "<", "Maven artifacts"),
            new Pin("jx.npm-workspace", "package.json", "\"version\": \"" + V + "\"", "private development package"),
            new Pin("jx.intellij-plugin", "tooling/intellij/build.gradle.kts", "(?m)^version = \"" + V + "\"", ""),
            new Pin("jx.vscode-extension", "tooling/vscode/package.json", "\"version\": \"" + V + "\"", ""),
            new Pin("java.release", "pom.xml", "<maven.compiler.release>" + V + "<", "language level; JDK patch is not pinned"),
            new Pin("maven.wrapper", ".mvn/wrapper/maven-wrapper.properties", "wrapperVersion=" + V, ""),
            new Pin("maven.distribution", ".mvn/wrapper/maven-wrapper.properties", "apache-maven-" + V + "-bin\\.zip",
                    "no distributionSha256Sum"),
            new Pin("maven.api", "jx-maven-plugin/pom.xml", "<artifactId>maven-plugin-api</artifactId><version>" + V + "<", ""),
            new Pin("maven.plugin-tools", "jx-maven-plugin/pom.xml",
                    "<artifactId>maven-plugin-annotations</artifactId><version>" + V + "<", ""),
            new Pin("maven.compiler-plugin", "pom.xml", "<artifactId>maven-compiler-plugin</artifactId><version>" + V + "<", ""),
            new Pin("maven.surefire-plugin", "pom.xml", "<artifactId>maven-surefire-plugin</artifactId><version>" + V + "<", ""),
            new Pin("maven.jar-plugin", "pom.xml", "<artifactId>maven-jar-plugin</artifactId><version>" + V + "<", ""),
            new Pin("maven.resources-plugin", "pom.xml", "<artifactId>maven-resources-plugin</artifactId><version>" + V + "<", ""),
            new Pin("maven.clean-plugin", "pom.xml", "<artifactId>maven-clean-plugin</artifactId><version>" + V + "<", ""),
            new Pin("maven.install-plugin", "pom.xml", "<artifactId>maven-install-plugin</artifactId><version>" + V + "<", ""),
            new Pin("maven.shade-plugin", "jx-language-server/pom.xml",
                    "<artifactId>maven-shade-plugin</artifactId><version>" + V + "<", ""),
            new Pin("teavm", "pom.xml", "<teavm.version>" + V + "<", ""),
            new Pin("react", "package.json", "\"react\": \"" + V + "\"", "exact; lockfile must agree"),
            new Pin("react-dom", "package.json", "\"react-dom\": \"" + V + "\"", "exact; lockfile must agree"),
            new Pin("scheduler", "package-lock.json", "\"node_modules/scheduler\": \\{\\s*\"version\": \"" + V + "\"",
                    "transitive; from lockfile"),
            new Pin("esbuild", "package.json", "\"esbuild\": \"" + V + "\"", ""),
            new Pin("playwright", "package.json", "\"@playwright/test\": \"" + V + "\"",
                    "drives the installed Chrome channel; Chrome itself is not pinned"),
            new Pin("npm.lockfile", "package-lock.json", "\"lockfileVersion\": " + V, ""),
            new Pin("node.engine.framework", "package.json", "\"node\": \"(>=[0-9]+)\"",
                    "gap: documentation says 22 or later"),
            new Pin("node.engine.vscode", "tooling/vscode/package.json", "\"node\": \"(>=[0-9]+)\"", ""),
            new Pin("node.ci.verify", ".github/workflows/verify.yml", "node-version: '" + V + "'", ""),
            new Pin("node.ci.tooling", ".github/workflows/tooling.yml", "node-version: '" + V + "'",
                    "gap: differs from the Verify workflow"),
            new Pin("spring-boot", "examples/todo-api/pom.xml", "<spring-boot.version>" + V + "<",
                    "example only; its BOM resolves H2, Flyway and JUnit"),
            new Pin("junit.jupiter", "jx-core/pom.xml", "<artifactId>junit-jupiter</artifactId><version>" + V + "<",
                    "gap: the Spring example takes JUnit from the Boot BOM instead"),
            new Pin("lsp4j", "jx-language-server/pom.xml", "<artifactId>org.eclipse.lsp4j</artifactId><version>" + V + "<", ""),
            new Pin("gson", "jx-language-server/pom.xml", "<artifactId>gson</artifactId><version>" + V + "<", ""),
            new Pin("intellij.gradle-plugin", "tooling/intellij/build.gradle.kts",
                    "id\\(\"org\\.jetbrains\\.intellij\\.platform\"\\) version \"" + V + "\"", ""),
            new Pin("intellij.platform", "tooling/intellij/build.gradle.kts", "intellijIdea\\(\"" + V + "\"\\)", ""),
            new Pin("intellij.since-build", "tooling/intellij/build.gradle.kts", "sinceBuild = \"([0-9.]+)\"", ""),
            new Pin("intellij.until-build", "tooling/intellij/build.gradle.kts", "untilBuild = \"([0-9.*]+)\"", ""),
            new Pin("intellij.plugin-verifier", "tooling/intellij/build.gradle.kts", "pluginVerifier\\(\"" + V + "\"\\)", ""),
            new Pin("intellij.junit", "tooling/intellij/build.gradle.kts", "\"junit:junit:" + V + "\"", ""),
            new Pin("gradle.wrapper", "tooling/intellij/gradle/wrapper/gradle-wrapper.properties",
                    "gradle-" + V + "-bin\\.zip", "distribution checksum pinned"),
            new Pin("vscode.engine", "tooling/vscode/package.json", "\"vscode\": \"(\\^[0-9.]+)\"", ""),
            new Pin("vscode.languageclient", "tooling/vscode/package.json", "\"vscode-languageclient\": \"" + V + "\"", ""),
            new Pin("vscode.typescript", "tooling/vscode/package.json", "\"typescript\": \"" + V + "\"", ""),
            new Pin("vscode.vsce", "tooling/vscode/package.json", "\"@vscode/vsce\": \"" + V + "\"", ""),
            new Pin("vscode.test-electron", "tooling/vscode/package.json", "\"@vscode/test-electron\": \"" + V + "\"", ""),
            new Pin("vscode.npm-lockfile", "tooling/vscode/package-lock.json", "\"lockfileVersion\": " + V, ""),
            new Pin("redhat-java", "tooling/vscode/PrepareJavaBridge.java", "vsextensions/java/" + V + "/vspackage",
                    "required exactly by the VS Code extension"),
            new Pin("jdt-ls", "tooling/vscode/PrepareJavaBridge.java", "bundle-version=\\\\\"\\[" + V + ",", "lower bound"));

    private static final List<Copy> COPIES = List.of(
            new Copy("jx.framework", "tooling/intellij/build.gradle.kts", "-" + V + "\\.jar"),
            new Copy("jx.framework", "tooling/vscode/VerifyPackages.java", "([0-9]+\\.[0-9]+\\.[0-9]+-SNAPSHOT)"),
            new Copy("jx.framework", "tooling/vscode/PrepareServer.java", "([0-9]+\\.[0-9]+\\.[0-9]+-SNAPSHOT)"),
            new Copy("jx.framework", "tooling/vscode/package.json", "([0-9]+\\.[0-9]+\\.[0-9]+-SNAPSHOT)"),
            new Copy("jx.framework", "README.md", "jx-maven-plugin:" + V + ":"),
            new Copy("jx.vscode-extension", "tooling/vscode/package.json", "jx-language-" + V + "\\.vsix"),
            new Copy("jx.vscode-extension", ".github/workflows/tooling.yml", "jx-language-" + V + "\\.vsix"),
            new Copy("jx.vscode-extension", "tooling/vscode/VerifyPackages.java", "jx-language-" + V + "\\.vsix"),
            new Copy("jx.vscode-extension", "tooling/vscode/test/run.cjs", "jx-language-" + V),
            new Copy("jx.intellij-plugin", ".github/workflows/tooling.yml", "jx-intellij-" + V + "\\.zip"),
            new Copy("jx.intellij-plugin", "tooling/vscode/VerifyPackages.java", "jx-intellij-" + V + "\\.zip"),
            new Copy("java.release", ".github/workflows/verify.yml", "java-version: '" + V + "'"),
            new Copy("java.release", ".github/workflows/tooling.yml", "java-version: '" + V + "'"),
            new Copy("java.release", "tooling/intellij/build.gradle.kts", "JavaLanguageVersion\\.of\\(" + V + "\\)"),
            new Copy("java.release", "tooling/vscode/PrepareJavaBridge.java", "\"--release\", \"" + V + "\""),
            new Copy("maven.distribution", "README.md", "downloads Maven " + V + "\\.(?:\\s|$)"),
            new Copy("maven.api", "jx-maven-plugin/pom.xml", "<artifactId>maven-core</artifactId><version>" + V + "<"),
            new Copy("maven.plugin-tools", "jx-maven-plugin/pom.xml",
                    "<artifactId>maven-plugin-plugin</artifactId><version>" + V + "<"),
            new Copy("react", "package-lock.json", "\"node_modules/react\": \\{\\s*\"version\": \"" + V + "\""),
            new Copy("react-dom", "package-lock.json", "\"node_modules/react-dom\": \\{\\s*\"version\": \"" + V + "\""),
            new Copy("esbuild", "package-lock.json", "\"node_modules/esbuild\": \\{\\s*\"version\": \"" + V + "\""),
            new Copy("esbuild", "tooling/vscode/package.json", "\"esbuild\": \"" + V + "\""),
            new Copy("playwright", "package-lock.json", "\"node_modules/@playwright/test\": \\{\\s*\"version\": \"" + V + "\""),
            new Copy("junit.jupiter", "jx-compiler/pom.xml", "<artifactId>junit-jupiter</artifactId><version>" + V + "<"),
            new Copy("junit.jupiter", "jx-elm/pom.xml", "<artifactId>junit-jupiter</artifactId><version>" + V + "<"),
            new Copy("junit.jupiter", "jx-language-server/pom.xml", "<artifactId>junit-jupiter</artifactId><version>" + V + "<"),
            new Copy("junit.jupiter", "jx-maven-plugin/pom.xml", "<artifactId>junit-jupiter</artifactId><version>" + V + "<"),
            new Copy("junit.jupiter", "examples/todo-app/pom.xml", "<artifactId>junit-jupiter</artifactId><version>" + V + "<"),
            new Copy("redhat-java", "tooling/vscode/PrepareJavaBridge.java", "(?i)redhat[. ]java[- ]" + V),
            new Copy("redhat-java", "tooling/vscode/PrepareJavaBridge.java", "Red Hat Java " + V),
            new Copy("redhat-java", "tooling/vscode/src/javaEditing.ts", "Red Hat(?: Java)? " + V),
            new Copy("redhat-java", "tooling/vscode/src/javaEditing.ts", "version !== '" + V + "'"),
            new Copy("redhat-java", "tooling/vscode/test/run.cjs", "redhat\\.java-" + V),
            new Copy("redhat-java", "tooling/vscode/test/native-probe.cjs", "redhat\\.java-" + V),
            new Copy("jdt-ls", "tooling/vscode/PrepareJavaBridge.java", "JDT LS " + V));

    private final Path root;
    private final List<String> failures = new ArrayList<>();
    private final Map<String, String> sources = new LinkedHashMap<>();
    private int copyCount;
    private int rowCount;

    private CheckBaseline(Path root) throws IOException {
        this.root = root.toRealPath();
    }

    public static void main(String[] args) {
        List<String> arguments = new ArrayList<>(List.of(args));
        boolean write = arguments.remove("--write-versions");
        if (arguments.size() > 1 || arguments.stream().anyMatch(argument -> argument.startsWith("-"))) {
            System.err.println("Usage: java tools/CheckBaseline.java [--write-versions] [repository-root]");
            System.exit(2);
        }
        try {
            var checker = new CheckBaseline(Path.of(arguments.isEmpty() ? "." : arguments.get(0)));
            String manifest = checker.manifest();
            if (write && checker.failures.isEmpty()) {
                Files.writeString(checker.root.resolve(VERSIONS), manifest, StandardCharsets.UTF_8);
                System.out.println("Wrote " + VERSIONS);
            }
            checker.checkManifest(manifest);
            checker.checkCopies();
            checker.checkLedger();
            if (!checker.failures.isEmpty()) {
                checker.failures.forEach(message -> System.err.println("FAIL: " + message));
                System.exit(1);
            }
            System.out.printf("PASS: %d version pins, %d repeated literals, %d capability rows.%n",
                    PINS.size(), checker.copyCount, checker.rowCount);
            System.out.println("Checks recorded evidence structure only; no application build or tests were run.");
        } catch (IOException exception) {
            System.err.println("FAIL: cannot inspect baseline: " + exception.getMessage());
            System.exit(1);
        }
    }

    /** Renders the manifest from the build files; an unreadable pin is a failure. */
    private String manifest() throws IOException {
        var text = new StringBuilder(VERSIONS_HEADER).append('\n');
        Set<String> ids = new HashSet<>();
        for (Pin pin : PINS) {
            require(ids.add(pin.id()), "duplicate pin id in checker: " + pin.id());
            text.append(pin.id()).append('\t').append(value(pin)).append('\t')
                    .append(pin.source()).append('\t').append(pin.note()).append('\n');
        }
        return text.toString();
    }

    private String value(Pin pin) throws IOException {
        String source = source(pin.source());
        if (source == null) {
            return "";
        }
        Matcher matcher = Pattern.compile(pin.regex()).matcher(source);
        if (!matcher.find()) {
            failures.add("pin " + pin.id() + " not found in " + pin.source());
            return "";
        }
        return matcher.group(1);
    }

    private void checkManifest(String expected) throws IOException {
        Path file = root.resolve(VERSIONS);
        if (!Files.isRegularFile(file)) {
            failures.add("missing " + VERSIONS + "; run with --write-versions");
            return;
        }
        String actual = Files.readString(file, StandardCharsets.UTF_8);
        if (actual.equals(expected)) {
            return;
        }
        Map<String, String> recorded = rows(actual);
        Map<String, String> declared = rows(expected);
        for (var entry : declared.entrySet()) {
            String line = recorded.remove(entry.getKey());
            if (line == null) {
                failures.add(VERSIONS + " lacks pin " + entry.getKey());
            } else if (!line.equals(entry.getValue())) {
                failures.add(VERSIONS + " row " + entry.getKey() + " differs from its source: recorded ["
                        + line.replace('\t', '|') + "], declared [" + entry.getValue().replace('\t', '|') + "]");
            }
        }
        recorded.keySet().forEach(id -> failures.add(VERSIONS + " has unknown pin " + id));
        if (failures.stream().noneMatch(message -> message.startsWith(VERSIONS))) {
            failures.add(VERSIONS + " header, order or line endings differ; run with --write-versions");
        }
    }

    private static Map<String, String> rows(String manifest) {
        Map<String, String> rows = new LinkedHashMap<>();
        manifest.lines().skip(1).forEach(line -> rows.put(line.split("\t", 2)[0], line));
        return rows;
    }

    private void checkCopies() throws IOException {
        Map<String, Pin> pins = new LinkedHashMap<>();
        PINS.forEach(pin -> pins.put(pin.id(), pin));
        for (Copy copy : COPIES) {
            Pin pin = pins.get(copy.pin());
            if (pin == null) {
                failures.add("checker copy refers to unknown pin " + copy.pin());
                continue;
            }
            String source = source(copy.source());
            if (source == null) {
                continue;
            }
            String expected = value(pin);
            Matcher matcher = Pattern.compile(copy.regex()).matcher(source);
            int matches = 0;
            while (matcher.find()) {
                matches++;
                if (!matcher.group(1).equals(expected)) {
                    failures.add(copy.source() + " repeats " + copy.pin() + " as " + matcher.group(1)
                            + " but " + pin.source() + " declares " + expected);
                }
            }
            require(matches > 0, "expected a repeated " + copy.pin() + " literal in " + copy.source()
                    + "; update tools/CheckBaseline.java if it moved");
            copyCount += matches;
        }
    }

    private void checkLedger() throws IOException {
        Path file = root.resolve(LEDGER);
        if (!Files.isRegularFile(file)) {
            failures.add("missing " + LEDGER);
            return;
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        require(text.endsWith("\n") && !text.contains("\r"), LEDGER + " must use LF line endings and end with a newline");
        List<String> lines = text.lines().toList();
        if (lines.isEmpty() || !lines.get(0).equals(String.join("\t", LEDGER_COLUMNS))) {
            failures.add(LEDGER + " header must be: " + String.join(" ", LEDGER_COLUMNS));
            return;
        }
        Set<String> commands = new HashSet<>(COMMANDS);
        String scripts = source("package.json");
        if (scripts != null) {
            Matcher script = NPM_SCRIPT.matcher(scripts.substring(Math.max(0, scripts.indexOf("\"scripts\""))));
            while (script.find()) {
                commands.add("npm run " + script.group(1));
            }
        }
        Set<String> ids = new HashSet<>();
        for (int index = 1; index < lines.size(); index++) {
            String where = LEDGER + ":" + (index + 1);
            String[] cells = lines.get(index).split("\t", -1);
            if (cells.length != LEDGER_COLUMNS.size()) {
                failures.add(where + " has " + cells.length + " columns, expected " + LEDGER_COLUMNS.size());
                continue;
            }
            rowCount++;
            String id = cells[0];
            String status = cells[3];
            String revision = cells[5];
            String fixture = cells[6];
            String command = cells[7];
            String ci = cells[8];
            String date = cells[10];
            where += " (" + id + ")";
            for (String cell : cells) {
                require(cell.equals(cell.strip()), where + " has a cell with surrounding whitespace");
            }
            require(ID.matcher(id).matches(), where + " has an invalid id");
            require(ids.add(id), where + " repeats an id");
            require(!cells[1].isEmpty() && !cells[2].isEmpty() && !cells[4].isEmpty(),
                    where + " needs area, capability and owner");
            require(STATUSES.contains(status), where + " has unknown status '" + status + "'");
            require(ci.isEmpty() || ci.startsWith("https://"), where + " ci must be empty or an https URL");
            require(!status.equals("qualified") || !ci.isEmpty(), where + " is qualified without a CI link");
            if (EVIDENCED.contains(status)) {
                require(REVISION.matcher(revision).matches(), where + " needs a commit revision");
                require(!fixture.isEmpty(), where + " needs a consumer fixture");
                require(commands.contains(command), where + " has unknown command '" + command + "'");
                require(isDate(date), where + " needs an ISO evidence date");
            } else {
                require(revision.isEmpty() || REVISION.matcher(revision).matches(), where + " has an invalid revision");
                require(command.isEmpty() || commands.contains(command), where + " has unknown command '" + command + "'");
                require(date.isEmpty() || isDate(date), where + " has an invalid date");
            }
            if (!fixture.isEmpty()) {
                Path target = root.resolve(fixture).normalize();
                require(target.startsWith(root) && Files.exists(target), where + " fixture does not exist: " + fixture);
            }
        }
        require(rowCount > 0, LEDGER + " has no capability rows");
    }

    private static boolean isDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    /** Returns the file's text, or null after recording that it is missing. */
    private String source(String file) throws IOException {
        if (!sources.containsKey(file)) {
            Path path = root.resolve(file);
            if (Files.isRegularFile(path)) {
                sources.put(file, Files.readString(path, StandardCharsets.UTF_8));
            } else {
                failures.add("missing version source: " + file);
                sources.put(file, null);
            }
        }
        return sources.get(file);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
    }
}
