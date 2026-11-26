import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

/** Builds a copied consumer outside the reactor using only installed JARs, then tests its browser UI.
 * Run after installing the producer, framework and plugin into the supplied Maven repository.
 * The temporary project and Maven log are retained as evidence, not added to the repository. */
public final class VerifyComponentLibrary {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 1 || arguments.length > 2 || arguments.length == 2 && !arguments[1].equals("--package-only"))
            throw new IllegalArgumentException("Usage: java tools/VerifyComponentLibrary.java <Maven repository> [--package-only]");
        boolean packageOnly = arguments.length == 2;
        Path root = Path.of("").toRealPath();
        Path repository = Path.of(arguments[0]).toRealPath();
        Path jar = repository.resolve("dev/jx/component-library/0.1.0-SNAPSHOT/component-library-0.1.0-SNAPSHOT.jar");
        if (!Files.isRegularFile(jar)) throw new IOException("Install examples/component-library into " + repository + " first");
        try (var archive = new ZipFile(jar.toFile())) {
            if (archive.getEntry("META-INF/jx/components.idx") == null
                    || archive.getEntry("jx/example/library/LibraryCounter.class") == null
                    || archive.getEntry("jx/example/library/LibraryStatus.class") == null
                    || archive.getEntry("jx/example/library/LibrarySparse.class") == null)
                throw new IOException("Producer JAR is missing its classes or component descriptor");
            if (archive.stream().anyMatch(entry -> entry.getName().endsWith(".java") || entry.getName().endsWith(".jx")))
                throw new IOException("The source-independent proof must not contain producer sources");
        }
        Path proof = Files.createTempDirectory("jx-component-library-proof-");
        Path consumer = proof.resolve("consumer");
        Files.createDirectories(consumer);
        Path example = root.resolve("examples/library-consumer");
        copySources(example.resolve("src"), consumer.resolve("src"));
        String pom = Files.readString(example.resolve("pom.xml"));
        String relative = "<relativePath>../../pom.xml</relativePath>";
        if (!pom.contains(relative)) throw new IOException("Unexpected consumer parent contract");
        Files.writeString(consumer.resolve("pom.xml"), pom.replace(relative, "<relativePath/>"));
        for (String name : List.of("package.json", "package-lock.json")) Files.copy(root.resolve(name), proof.resolve(name));
        // Only framework-owned npm dependencies are shared. There is no producer source/output link.
        Files.createSymbolicLink(proof.resolve("node_modules"), root.resolve("node_modules").toRealPath());
        Path log = proof.resolve("maven.log");
        run(List.of(root.resolve("mvnw").toString(), "-B", "-ntp", "-f", consumer.resolve("pom.xml").toString(),
                "-Dmaven.repo.local=" + repository, "package", "-l", log.toString()), root, Map.of(), Duration.ofMinutes(5));
        String output = Files.readString(log);
        if (!output.contains(jar.toString()) || output.contains("examples/component-library/target/classes"))
            throw new IOException("TeaVM did not demonstrably consume only the installed producer JAR; inspect " + log);
        Path site = consumer.resolve("target/site");
        if (!Files.isRegularFile(site.resolve("application.js"))) throw new IOException("Missing standalone browser application");
        Path receipt = root.resolve("target/component-library-proof.txt");
        Files.createDirectories(receipt.getParent());
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        Files.writeString(receipt, "Producer JAR: " + jar + "\nSHA-256: " + digest + "\nConsumer: " + consumer
                + "\nMaven log: " + log + "\nBrowser site: " + site + "\nBrowser qualification: pending\n");
        System.out.println("Source-independent Maven consumer passed; testing " + site);
        if (packageOnly) {
            System.out.println("Browser qualification remains pending; saved site: " + site);
            return;
        }
        run(List.of("node", root.resolve("node_modules/@playwright/test/cli.js").toString(), "test", "-c",
                root.resolve("examples/library-consumer/playwright.config.js").toString(), "--workers=1"), root,
                Map.of("LIBRARY_SITE", site.toString()), Duration.ofMinutes(2));
        Files.writeString(receipt, Files.readString(receipt).replace("Browser qualification: pending", "Browser qualification: passed"));
        System.out.println("Component-library proof passed. Evidence: " + receipt);
    }

    private static void copySources(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path input : paths.sorted().toList()) {
                if (Files.isSymbolicLink(input)) throw new IOException("Proof input must not contain symlinks: " + input);
                Path output = target.resolve(source.relativize(input));
                if (Files.isDirectory(input)) Files.createDirectories(output);
                else Files.copy(input, output);
            }
        }
    }

    private static void run(List<String> command, Path directory, Map<String,String> environment, Duration timeout)
            throws IOException, InterruptedException {
        var builder = new ProcessBuilder(command).directory(directory.toFile()).inheritIO();
        builder.environment().putAll(environment);
        Process process = builder.start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new IOException("Proof step timed out: " + command.getFirst());
            if (process.exitValue() != 0) throw new IOException("Proof step failed with exit " + process.exitValue() + ": " + command.getFirst());
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }
}
