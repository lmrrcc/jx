import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Measures whether the conformance bench detects defects. Each mutant in
 * {@code tests/conformance/mutants.tsv} is one deliberate change to JX. The tool
 * applies it in a scratch copy of the repository, rebuilds, and runs the JX side of
 * the bench: a failing bench kills the mutant, a passing bench means a case is missing.
 *
 * <p>The working tree and its Maven repository are never modified. The run takes
 * a build and all applicable JX rendering modes per mutant. Run it without
 * concurrent builds or browser suites to avoid resource contention and timeouts.
 */
public final class MutateBench {
    private static final String MUTANTS = "tests/conformance/mutants.tsv";
    private static final String RESULTS = "docs/evidence/mutation-results.tsv";
    private static final List<String> MODULES = List.of("jx-core", "jx-react", "jx-compiler", "jx-maven-plugin",
            "tests/conformance");
    private static final Pattern FAILED_CASE = Pattern.compile("(?m)^\\s+\\d+\\) \\[jx(?:-hydrate|-static|-lazy)?\\] › \\S+ › ([a-z0-9-]+): ");
    private static final long STEP_TIMEOUT_MINUTES = 6;

    private record Mutant(String id, String file, String find, String replace, String change) {}

    private record Outcome(Mutant mutant, String result, String detail) {}

    private final Path root;
    private final Path work;

    private MutateBench(Path root, Path work) {
        this.root = root;
        this.work = work;
    }

    public static void main(String[] args) throws Exception {
        Set<String> only = new TreeSet<>();
        boolean keep = false;
        for (int index = 0; index < args.length; index++) {
            if (args[index].equals("--only") && index + 1 < args.length) {
                only.addAll(Arrays.asList(args[++index].split(",")));
            } else if (args[index].equals("--keep")) {
                keep = true;
            } else {
                System.err.println("Usage: java tools/MutateBench.java [--only id,id] [--keep]");
                System.exit(2);
            }
        }
        Path root = Path.of(".").toRealPath();
        Path work = Files.createTempDirectory("jx-mutation-");
        var tool = new MutateBench(root, work);
        int status;
        try {
            status = tool.run(only);
        } finally {
            if (keep) {
                System.out.println("Scratch copy kept at " + work);
            } else {
                delete(work);
            }
        }
        System.exit(status);
    }

    private int run(Set<String> only) throws Exception {
        List<Mutant> mutants = readMutants().stream().filter(mutant -> only.isEmpty() || only.contains(mutant.id())).toList();
        if (mutants.isEmpty()) {
            System.err.println("FAIL: no mutants selected");
            return 2;
        }
        System.out.println("Copying the repository to " + work);
        copyRepository();
        if (!step("reference build", "npm", "run", "build:bench")) {
            System.err.println("FAIL: the reference build failed in the scratch copy; see " + work.resolve("mutation.log"));
            return 1;
        }
        if (!build() || !bench()) {
            System.err.println("FAIL: the unmutated bench does not pass; fix that first. Log: " + work.resolve("mutation.log"));
            return 1;
        }
        System.out.println("Unmutated bench passes. Running " + mutants.size() + " mutants.");
        List<Outcome> outcomes = new ArrayList<>();
        for (Mutant mutant : mutants) {
            Path file = work.resolve(mutant.file());
            String original = Files.readString(file, StandardCharsets.UTF_8);
            Outcome outcome;
            if (count(original, mutant.find()) != 1) {
                outcome = new Outcome(mutant, "stale", "target text occurs " + count(original, mutant.find()) + " times");
            } else {
                Files.writeString(file, original.replace(mutant.find(), mutant.replace()), StandardCharsets.UTF_8);
                try {
                    if (!build()) {
                        outcome = new Outcome(mutant, "invalid", "does not compile");
                    } else if (bench()) {
                        outcome = new Outcome(mutant, "survived", "");
                    } else {
                        outcome = new Outcome(mutant, "killed", failedCases());
                    }
                } finally {
                    Files.writeString(file, original, StandardCharsets.UTF_8);
                }
            }
            outcomes.add(outcome);
            System.out.printf("%-9s %-28s %s%n", outcome.result(), mutant.id(), outcome.detail());
        }
        long killed = outcomes.stream().filter(outcome -> outcome.result().equals("killed")).count();
        long survived = outcomes.stream().filter(outcome -> outcome.result().equals("survived")).count();
        long unusable = outcomes.size() - killed - survived;
        System.out.printf("Mutation result: %d killed, %d survived, %d unusable of %d.%n",
                killed, survived, unusable, outcomes.size());
        if (only.isEmpty()) {
            var table = new StringBuilder("id\tresult\tdetected-by\tchange\n");
            for (Outcome outcome : outcomes) {
                table.append(outcome.mutant().id()).append('\t').append(outcome.result()).append('\t')
                        .append(outcome.detail()).append('\t').append(outcome.mutant().change()).append('\n');
            }
            Files.writeString(root.resolve(RESULTS), table.toString(), StandardCharsets.UTF_8);
            System.out.println("Wrote " + RESULTS);
        }
        return survived == 0 && unusable == 0 ? 0 : 1;
    }

    private List<Mutant> readMutants() throws IOException {
        List<String> lines = Files.readAllLines(root.resolve(MUTANTS), StandardCharsets.UTF_8);
        List<Mutant> mutants = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] cells = lines.get(index).split("\t", -1);
            if (cells.length != 5) {
                throw new IOException(MUTANTS + ":" + (index + 1) + " needs 5 columns");
            }
            mutants.add(new Mutant(cells[0], cells[1], cells[2], cells[3], cells[4]));
        }
        return mutants;
    }

    /** Copies tracked and unignored files, the Maven repository, and links node_modules. */
    private void copyRepository() throws Exception {
        Process list = new ProcessBuilder("git", "ls-files", "-co", "--exclude-standard", "-z").directory(root.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String names = new String(list.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (list.waitFor() != 0) {
            throw new IOException("git ls-files failed");
        }
        for (String name : names.split("\0")) {
            Path source = root.resolve(name);
            if (name.isBlank() || !Files.isRegularFile(source)) {
                continue;
            }
            Path target = work.resolve(name);
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
        Path repository = root.resolve(".cache/maven-repository");
        if (!Files.isDirectory(repository) || !Files.isDirectory(root.resolve("node_modules"))) {
            throw new IOException("run npm ci and the Maven install once before mutation testing");
        }
        // A worktree may borrow the clone's cache; still copy it, never mutate it in place.
        repository = repository.toRealPath();
        Path copy = work.resolve(".cache/maven-repository");
        try (var files = Files.walk(repository)) {
            for (Path source : files.toList()) {
                Path target = copy.resolve(repository.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target);
                }
            }
        }
        Files.createSymbolicLink(work.resolve("node_modules"), root.resolve("node_modules"));
    }

    private boolean build() throws Exception {
        return step("build", "./mvnw", "-o", "-B", "-ntp", "-q", "-Dmaven.repo.local=.cache/maven-repository",
                "-DskipTests", "-pl", String.join(",", MODULES), "install")
                // Both bundles wrap compiled applications, so they follow every mutated build.
                && step("server bundle", "npm", "run", "build:bench-server")
                && step("lazy bundle", "npm", "run", "build:bench-lazy")
                && step("production bundles", "npm", "run", "build:bench-production");
    }

    private boolean bench() throws Exception {
        return step("bench", "npx", "playwright", "test", "-c", "tests/conformance/playwright.config.js",
                "--project=jx", "--project=jx-hydrate", "--project=jx-static", "--project=jx-lazy");
    }

    /** Runs one bounded step in the scratch copy; its output replaces the step log. */
    private boolean step(String name, String... command) throws Exception {
        Path log = work.resolve("mutation.log");
        var builder = new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile());
        builder.environment().put("BENCH_NO_COMPARE", "1");
        builder.environment().put("CI", "1");
        builder.environment().put("BENCH_PORT", "4574");
        Process process = builder.start();
        if (!process.waitFor(STEP_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            Files.writeString(log, "\n" + name + " timed out\n", StandardCharsets.UTF_8);
            return false;
        }
        return process.exitValue() == 0;
    }

    private String failedCases() throws IOException {
        String log = Files.readString(work.resolve("mutation.log"), StandardCharsets.UTF_8);
        Set<String> cases = new TreeSet<>();
        Matcher matcher = FAILED_CASE.matcher(log);
        while (matcher.find()) {
            cases.add(matcher.group(1));
        }
        return cases.isEmpty() ? "bench failed" : String.join(",", cases);
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int index = text.indexOf(part); index >= 0; index = text.indexOf(part, index + part.length())) {
            count++;
        }
        return count;
    }

    private static void delete(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult postVisitDirectory(Path visited, IOException failure) throws IOException {
                Files.delete(visited);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
