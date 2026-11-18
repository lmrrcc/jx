import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Checks this repository's instruction pack without external dependencies. */
public final class CheckAgentSetup {
    private static final Pattern NAME = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern LINK = Pattern.compile("!?\\[[^\\]\\r\\n]+\\]\\(([^)\\r\\n]+)\\)");
    private static final Pattern SCAFFOLD = Pattern.compile("\\[(?:TODO|TBD)\\b|\\bREPLACE_ME\\b");
    private final Path root;
    private final Path skills;
    private final List<String> failures = new ArrayList<>();
    private final Set<Path> documents = new HashSet<>();
    private int skillCount;
    private int linkCount;

    private CheckAgentSetup(Path root) throws IOException {
        this.root = root.toRealPath();
        this.skills = this.root.resolve(".agents/skills");
    }

    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Usage: java tools/CheckAgentSetup.java [repository-root]");
            System.exit(2);
        }
        try {
            var checker = new CheckAgentSetup(Path.of(args.length == 0 ? "." : args[0]));
            checker.check();
            if (!checker.failures.isEmpty()) {
                checker.failures.forEach(message -> System.err.println("FAIL: " + message));
                System.exit(1);
            }
            System.out.printf("PASS: %d skills, %d documents, %d local links, 3 compatibility links.%n",
                    checker.skillCount, checker.documents.size(), checker.linkCount);
            System.out.println("Checks instruction structure only; no application build or tests were run.");
        } catch (IOException exception) {
            System.err.println("FAIL: cannot inspect setup: " + exception.getMessage());
            System.exit(1);
        }
    }

    private void check() throws IOException {
        for (String file : List.of("AGENTS.md", "README.md", "CLAUDE.md", ".cursor/rules/jx.mdc")) {
            Path document = root.resolve(file);
            require(Files.isRegularFile(document), "missing " + file);
            if (Files.isRegularFile(document)) {
                documents.add(document);
            }
        }
        collectDocuments(root.resolve("docs"));
        if (!Files.isDirectory(skills)) {
            failures.add("missing canonical skills directory: .agents/skills");
            return;
        }
        require(skills.toRealPath().startsWith(root), "canonical skills must be inside this repository");
        collectDocuments(skills);
        checkSkills();
        checkCompatibilityLinks();
        for (Path document : documents.stream().sorted().toList()) {
            checkDocument(document);
        }
        Path claude = root.resolve("CLAUDE.md");
        if (Files.isRegularFile(claude)) {
            require(Files.readString(claude).lines().anyMatch(line -> line.strip().equals("@AGENTS.md")),
                    "CLAUDE.md must import @AGENTS.md");
        }
    }

    private void checkSkills() throws IOException {
        Set<String> names = new HashSet<>();
        String routing = Files.isRegularFile(root.resolve("AGENTS.md"))
                ? Files.readString(root.resolve("AGENTS.md")) : "";
        try (var entries = Files.list(skills)) {
            for (Path directory : entries.sorted().toList()) {
                if (!Files.isDirectory(directory)) {
                    failures.add("unexpected file in skill root: " + relative(directory));
                    continue;
                }
                skillCount++;
                Path entry = directory.resolve("SKILL.md");
                if (!Files.isRegularFile(entry)) {
                    failures.add("missing skill entrypoint: " + relative(entry));
                    continue;
                }
                String source = Files.readString(entry, StandardCharsets.UTF_8).replace("\r\n", "\n");
                int end = source.startsWith("---\n") ? source.indexOf("\n---\n", 4) : -1;
                if (end < 0) {
                    failures.add("missing delimited frontmatter: " + relative(entry));
                    continue;
                }
                String metadata = source.substring(4, end);
                String name = scalar(metadata, "name");
                String description = scalar(metadata, "description");
                require(NAME.matcher(name).matches() && name.length() <= 64,
                        "invalid skill name in " + relative(entry));
                require(name.equals(directory.getFileName().toString()),
                        "skill name must match directory: " + relative(entry));
                require(names.add(name), "duplicate skill name: " + name);
                require(!description.isBlank() && description.length() <= 1024,
                        "missing or oversized skill description: " + relative(entry));
                require(!source.substring(end + 5).isBlank(), "empty skill body: " + relative(entry));
                require(!SCAFFOLD.matcher(source).find(), "unfinished scaffold: " + relative(entry));
                require(routing.contains("(.agents/skills/" + directory.getFileName() + "/SKILL.md)"),
                        "skill is not routed from AGENTS.md: " + directory.getFileName());
            }
        }
        require(skillCount > 0, "canonical skill directory is empty");
    }

    // This checks required scalar fields, not the entire YAML specification.
    private String scalar(String metadata, String key) {
        var matcher = Pattern.compile("(?m)^" + Pattern.quote(key) + ":[ \\t]*([^\\n]*)$")
                .matcher(metadata);
        if (!matcher.find()) {
            return "";
        }
        String value = matcher.group(1).strip();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        if (value.equals("|") || value.equals(">") || value.equals("|-") || value.equals(">-")) {
            return metadata.substring(matcher.end()).lines().skip(1)
                    .takeWhile(line -> line.isBlank() || Character.isWhitespace(line.charAt(0)))
                    .map(String::strip).reduce("", (left, right) -> left + " " + right).strip();
        }
        return value;
    }

    private void checkCompatibilityLinks() throws IOException {
        for (String location : List.of(".codex/skills", ".claude/skills", ".cursor/skills")) {
            Path alias = root.resolve(location);
            if (!Files.isSymbolicLink(alias)) {
                failures.add("compatibility path must be a symlink: " + location);
                continue;
            }
            require(!Files.readSymbolicLink(alias).isAbsolute(), "compatibility link must be relative: " + location);
            require(Files.exists(alias) && alias.toRealPath().equals(skills.toRealPath()),
                    "compatibility link must resolve to canonical skills: " + location);
        }
    }

    private void collectDocuments(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            failures.add("missing documentation directory: " + relative(directory));
            return;
        }
        try (var files = Files.walk(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".md") || file.toString().endsWith(".mdc"))
                    .forEach(documents::add);
        }
    }

    private void checkDocument(Path document) throws IOException {
        String source = Files.readString(document, StandardCharsets.UTF_8);
        require(source.endsWith("\n"), "missing final newline: " + relative(document));
        require(source.lines().noneMatch(line -> !line.equals(line.stripTrailing())),
                "trailing whitespace: " + relative(document));
        var links = LINK.matcher(source);
        while (links.find()) {
            String destination = links.group(1).strip();
            if (destination.startsWith("<") && destination.contains(">")) {
                destination = destination.substring(1, destination.indexOf('>'));
            } else {
                destination = destination.split("\\s+", 2)[0];
            }
            if (destination.startsWith("#") || destination.matches("[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
                continue;
            }
            String file = destination.split("[#?]", 2)[0];
            if (file.isBlank()) {
                continue;
            }
            linkCount++;
            Path target = document.getParent().resolve(file).normalize();
            if (!Files.exists(target)) {
                failures.add("broken link in " + relative(document) + ": " + destination);
                continue;
            }
            Path actual = target.toRealPath();
            require(actual.startsWith(root), "link leaves repository in " + relative(document) + ": " + destination);
            if (document.startsWith(skills)) {
                require(actual.startsWith(skills.toRealPath()),
                        "portable skill link leaves pack in " + relative(document) + ": " + destination);
            }
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
    }

    private String relative(Path file) {
        return root.relativize(file).toString();
    }
}
