import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Rehearses a Maven file-repository distribution with independent consumer caches.
 * The publisher seed must already contain this checkout's installed candidate artifacts.
 * Maven dependencies/plugins are file-only; the separate locked npm installation may use the
 * public registry. The JDK, Maven Wrapper distribution, Node and Chrome are provisioned tools.
 * No application generated sources, class directories, node_modules or local artifact cache are
 * copied into the consumer. Proof directories and receipts are retained for inspection. */
public final class VerifyArtifactConsumer {
    private static final String VERSION = "0.1.0-SNAPSHOT";
    private static final String REPOSITORY_ID = "jx-proof-file";
    private static final List<String> MODULES = List.of("jx-core", "jx-react", "jx-compiler", "jx-maven-plugin");
    private static final int MAX_FILES = 20_000;
    private static final long MAX_FILE_BYTES = 128L * 1024 * 1024;
    private static final long MAX_REPOSITORY_BYTES = 2L * 1024 * 1024 * 1024;
    private static final Path PLUGIN_JAR = Path.of("dev/jx/jx-maven-plugin", VERSION, "jx-maven-plugin-" + VERSION + ".jar");

    private record Artifact(Path path, long bytes, String sha256) {}
    private record Candidate(String artifact, Path source) {}

    public static void main(String[] arguments) throws Exception {
        Path root = Path.of("").toRealPath();
        if (arguments.length == 1 && arguments[0].equals("--self-test")) {
            verifyInventoryContracts();
            return;
        }
        if (arguments.length == 2 && arguments[0].equals("--browser-only")) {
            Path proof = Path.of(arguments[1]).toRealPath();
            if (!Files.readString(proof.resolve("proof-version.txt")).equals("jx-artifact-consumer-v1\n")
                    || !Files.isRegularFile(proof.resolve("maven-offline.log")))
                throw new IOException("Not a packaged artifact consumer proof: " + proof);
            browser(proof);
            Files.createDirectories(root.resolve("target"));
            Files.writeString(root.resolve("target/artifact-consumer-proof.txt"), Files.readString(proof.resolve("receipt.txt")));
            copyReports(root, proof);
            return;
        }
        if (arguments.length > 2 || arguments.length == 2 && !arguments[1].equals("--package-only"))
            throw new IllegalArgumentException("Usage: java tools/VerifyArtifactConsumer.java [publisher-repository] [--package-only] | --browser-only <proof>");
        Path seed = Path.of(arguments.length == 0 ? ".cache/maven-repository" : arguments[0]).toRealPath();
        verifyInventoryContracts();
        Path proof = Files.createTempDirectory("jx-artifact-consumer-").toRealPath();
        if (proof.startsWith(root)) throw new IOException("Consumer must be outside the checkout");
        Files.writeString(proof.resolve("proof-version.txt"), "jx-artifact-consumer-v1\n");
        Path receipt = root.resolve("target/artifact-consumer-proof.txt");
        Files.createDirectories(receipt.getParent());
        Files.writeString(receipt, "Proof: " + proof + "\nStatus: preparing\n");
        System.out.println("Artifact consumer proof: " + proof);
        try {
        Path repository = proof.resolve("repository");
        List<Artifact> artifacts = stageRepository(root, seed, repository);
        Path negativeRepository = proof.resolve("repository-without-plugin");
        copyTree(repository, negativeRepository, Set.of(PLUGIN_JAR, Path.of(PLUGIN_JAR + ".sha1"), Path.of(PLUGIN_JAR + ".sha256")));
        Path consumer = proof.resolve("consumer");
        copyTree(root.resolve("tests/artifact-consumer"), consumer, Set.of());
        if (Files.exists(consumer.resolve("target")) || Files.exists(consumer.resolve("node_modules")))
            throw new IOException("The authored consumer fixture must not include generated outputs/dependencies");
        Files.copy(root.resolve("mvnw"), consumer.resolve("mvnw"));
        if (!consumer.resolve("mvnw").toFile().setExecutable(true)) throw new IOException("Cannot make copied Maven Wrapper executable");
        Files.createDirectories(consumer.resolve(".mvn/wrapper"));
        Files.copy(root.resolve(".mvn/wrapper/maven-wrapper.properties"), consumer.resolve(".mvn/wrapper/maven-wrapper.properties"));
        Files.writeString(proof.resolve("global-settings.xml"), "<settings/>\n");
        Files.writeString(proof.resolve("settings.xml"), settings(repository));
        Files.writeString(proof.resolve("negative-settings.xml"), settings(negativeRepository));
        Files.writeString(proof.resolve("npm-user.rc"), "");
        Files.writeString(proof.resolve("npm-global.rc"), "");
        Files.writeString(proof.resolve("repository-sha256.tsv"), manifest(artifacts));
        verifyRepository(repository, artifacts);
        assertAbsent(proof.resolve("maven-local"), proof.resolve("maven-negative"), proof.resolve("npm-cache"));

        // This installation has its own lock, directory and initially empty npm cache. It is not
        // claimed to come from the Maven repository or to be an offline npm bootstrap.
        run(List.of("npm", "ci", "--no-audit", "--no-fund", "--registry=https://registry.npmjs.org",
                "--cache=" + proof.resolve("npm-cache"), "--userconfig=" + proof.resolve("npm-user.rc"),
                "--globalconfig=" + proof.resolve("npm-global.rc")), consumer, Map.of(), Duration.ofMinutes(3), true);

        int missing = run(maven(proof, "negative-settings.xml", "maven-negative", "maven-missing-plugin.log", false,
                "dev.jx:jx-maven-plugin:" + VERSION + ":generate"), consumer, Map.of(), Duration.ofMinutes(3), false);
        String negative = readBounded(proof.resolve("maven-missing-plugin.log"));
        if (missing == 0 || !negative.contains("jx-maven-plugin") || !negative.contains("Could not find artifact dev.jx:jx-maven-plugin:jar:" + VERSION))
            throw new IOException("Missing-plugin control did not reject the absent repository artifact; inspect " + proof);
        assertAbsent(proof.resolve("maven-local"), consumer.resolve("target"));

        run(maven(proof, "settings.xml", "maven-local", "maven-file.log", false, "clean", "package"), consumer,
                Map.of(), Duration.ofMinutes(5), true);
        verifyBuild(proof, root, artifacts, "maven-file.log");
        List<Artifact> resolved = verifyResolution(proof.resolve("maven-local"), repository, artifacts);
        Files.writeString(proof.resolve("resolved-sha256.tsv"), manifest(resolved));
        run(maven(proof, "settings.xml", "maven-local", "maven-offline.log", true, "clean", "package"), consumer,
                Map.of(), Duration.ofMinutes(5), true);
        verifyBuild(proof, root, artifacts, "maven-offline.log");
        verifyResolution(proof.resolve("maven-local"), repository, artifacts);
        verifyRepository(repository, artifacts);
        String summary = "Proof: " + proof + "\nStatus: Maven file-only and offline clean packages passed\n"
                + "Missing-plugin control: rejected from separate empty cache\n"
                + "Staged Maven artifacts: " + artifacts.size() + "\nResolved Maven artifacts: " + resolved.size() + "\n"
                + "JX candidate SHA-256: " + proof.resolve("repository-sha256.tsv") + "\n"
                + "Resolved SHA-256 and file provenance: " + proof.resolve("resolved-sha256.tsv") + "\n"
                + "npm: independent npm ci, public registry, pinned lock; not offline bootstrap\n"
                + "Browser qualification: pending\n";
        Files.writeString(proof.resolve("receipt.txt"), summary);
        Files.writeString(receipt, summary);
        if (arguments.length == 2) {
            System.out.println("Maven qualification passed; browser pending. Resume: java tools/VerifyArtifactConsumer.java --browser-only " + proof);
            return;
        }
        browser(proof);
        Files.writeString(receipt, Files.readString(proof.resolve("receipt.txt")));
        System.out.println("PASS: independent artifact consumer. Receipt: " + receipt);
        } finally {
            copyReports(root, proof);
        }
    }

    private static void copyReports(Path root, Path proof) throws IOException {
        Path output = root.resolve("target/artifact-consumer-reports").resolve(proof.getFileName());
        Files.createDirectories(output);
        for (String name : List.of("receipt.txt", "repository-sha256.tsv", "resolved-sha256.tsv",
                "maven-file.log", "maven-offline.log", "maven-missing-plugin.log")) {
            Path input = proof.resolve(name);
            if (Files.isRegularFile(input)) {
                if (Files.size(input) > 16 * 1024 * 1024) throw new IOException("Proof report exceeds 16 MiB: " + input);
                Files.copy(input, output.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static List<Artifact> stageRepository(Path root, Path seed, Path repository) throws Exception {
        var candidates = new ArrayList<Candidate>();
        candidates.add(new Candidate("jx-parent", root.resolve("pom.xml")));
        for (String module : MODULES) {
            candidates.add(new Candidate(module, root.resolve(module + "/pom.xml")));
            candidates.add(new Candidate(module, root.resolve(module + "/target/" + module + "-" + VERSION + ".jar")));
        }
        for (Candidate candidate : candidates) {
            String extension = candidate.source().toString().endsWith(".jar") ? "jar" : "pom";
            Path installed = seed.resolve("dev/jx/" + candidate.artifact() + "/" + VERSION + "/" + candidate.artifact() + "-" + VERSION + "." + extension);
            if (!Files.isRegularFile(candidate.source()) || !Files.isRegularFile(installed)
                    || Files.mismatch(candidate.source(), installed) != -1)
                throw new IOException("Install this checkout's candidate before rehearsal; mismatched/missing " + installed);
        }
        var artifacts = new ArrayList<Artifact>();
        long total = 0;
        try (var paths = Files.walk(seed)) {
            for (Iterator<Path> iterator = paths.iterator(); iterator.hasNext();) {
                Path input = iterator.next();
                if (Files.isSymbolicLink(input)) throw new IOException("Repository seed contains a symlink: " + input);
                if (!Files.isRegularFile(input)) continue;
                Path relative = seed.relativize(input);
                String name = input.getFileName().toString();
                if (!(name.endsWith(".pom") || name.endsWith(".jar"))) continue;
                if (relative.startsWith(Path.of("dev/jx")) && candidates.stream().noneMatch(candidate ->
                        relative.equals(Path.of("dev/jx/" + candidate.artifact() + "/" + VERSION + "/" + candidate.artifact()
                                + "-" + VERSION + (candidate.source().toString().endsWith(".jar") ? ".jar" : ".pom"))))) continue;
                long size = Files.size(input);
                if (artifacts.size() >= MAX_FILES || size > MAX_FILE_BYTES || (total += size) > MAX_REPOSITORY_BYTES)
                    throw new IOException("Publisher repository exceeds the bounded rehearsal inventory");
                Path output = repository.resolve(relative);
                Files.createDirectories(output.getParent());
                Files.copy(input, output);
                String digest = digest(output, "SHA-256");
                Files.writeString(Path.of(output + ".sha256"), digest + "\n");
                Files.writeString(Path.of(output + ".sha1"), digest(output, "SHA-1") + "\n");
                artifacts.add(new Artifact(relative, size, digest));
            }
        }
        artifacts.sort(Comparator.comparing(artifact -> artifact.path().toString()));
        return List.copyOf(artifacts);
    }

    private static String settings(Path repository) {
        return "<settings><mirrors><mirror><id>" + REPOSITORY_ID + "</id><mirrorOf>*</mirrorOf><url>"
                + repository.toUri().toASCIIString().replace("&", "&amp;") + "</url></mirror></mirrors>"
                + "<profiles><profile><id>file-only</id><repositories><repository><id>jx-input</id><url>file:///unused</url>"
                + "<releases><enabled>true</enabled><checksumPolicy>fail</checksumPolicy></releases>"
                + "<snapshots><enabled>true</enabled><checksumPolicy>fail</checksumPolicy></snapshots></repository></repositories>"
                + "<pluginRepositories><pluginRepository><id>jx-plugin-input</id><url>file:///unused</url>"
                + "<releases><enabled>true</enabled><checksumPolicy>fail</checksumPolicy></releases>"
                + "<snapshots><enabled>true</enabled><checksumPolicy>fail</checksumPolicy></snapshots></pluginRepository></pluginRepositories>"
                + "</profile></profiles><activeProfiles><activeProfile>file-only</activeProfile></activeProfiles></settings>\n";
    }

    private static List<String> maven(Path proof, String settings, String cache, String log, boolean offline, String... goals) {
        var command = new ArrayList<>(List.of(proof.resolve("consumer/mvnw").toString(), "-B", "-ntp", "-C",
                "-s", proof.resolve(settings).toString(), "-gs", proof.resolve("global-settings.xml").toString(),
                "-Dmaven.repo.local=" + proof.resolve(cache), "-l", proof.resolve(log).toString()));
        if (offline) command.add("-o");
        command.addAll(List.of(goals));
        return List.copyOf(command);
    }

    private static void verifyBuild(Path proof, Path root, List<Artifact> artifacts, String name) throws IOException {
        Path consumer = proof.resolve("consumer");
        for (String generated : List.of("target/generated-sources/jx/example/distribution/Counter.java",
                "target/classes/example/distribution/Counter.class", "target/browser/application.js", "target/site/application.js"))
            if (!Files.isRegularFile(consumer.resolve(generated))) throw new IOException("Normal lifecycle did not produce " + generated);
        String log = readBounded(proof.resolve(name));
        if (!log.contains("BUILD SUCCESS") || log.contains(root.toString())) throw new IOException("Consumer build failed or referenced the checkout: " + name);
        for (String module : List.of("jx-react", "jx-core")) {
            String jar = proof.resolve("maven-local/dev/jx/" + module + "/" + VERSION + "/" + module + "-" + VERSION + ".jar").toString();
            if (!log.contains(jar)) throw new IOException("TeaVM did not report the independent " + module + " JAR");
        }
        if (artifacts.stream().filter(artifact -> artifact.path().startsWith(Path.of("dev/jx"))).count() != 9)
            throw new IOException("Unexpected JX artifact inventory");
    }

    private static List<Artifact> verifyResolution(Path cache, Path repository, List<Artifact> inventory) throws Exception {
        var expected = new HashMap<Path, Artifact>();
        for (Artifact artifact : inventory) {
            if (expected.put(artifact.path(), artifact) != null) throw new IOException("Duplicate inventory path");
        }
        var result = new ArrayList<Artifact>();
        try (var paths = Files.walk(cache)) {
            for (Iterator<Path> iterator = paths.iterator(); iterator.hasNext();) {
                Path local = iterator.next();
                if (Files.isSymbolicLink(local)) throw new IOException("Consumer cache must not link to publisher artifacts");
                if (!Files.isRegularFile(local)) continue;
                String name = local.getFileName().toString();
                if (!(name.endsWith(".jar") || name.endsWith(".pom"))) continue;
                if (result.size() >= MAX_FILES || Files.size(local) > MAX_FILE_BYTES) throw new IOException("Resolved artifact inventory exceeds bound");
                Path relative = cache.relativize(local);
                Artifact original = expected.get(relative);
                if (original == null || original.bytes() != Files.size(local)
                        || !original.sha256().equals(digest(local, "SHA-256")))
                    throw new IOException("Resolved artifact is absent from or differs from the initial inventory: " + relative);
                Path origin = repository.resolve(relative);
                if (!Files.isRegularFile(origin) || Files.mismatch(origin, local) != -1)
                    throw new IOException("Resolved bytes are absent from or differ from the file repository: " + relative);
                var tracking = new Properties();
                Path provenance = local.resolveSibling("_remote.repositories");
                try (var reader = Files.newBufferedReader(provenance)) { tracking.load(reader); }
                if (!tracking.containsKey(name + ">" + REPOSITORY_ID))
                    throw new IOException("Artifact does not record the file repository as its origin: " + relative);
                for (String key : tracking.stringPropertyNames())
                    if (key.startsWith(name + ">") && !key.equals(name + ">" + REPOSITORY_ID))
                        throw new IOException("Artifact has an unexpected repository origin: " + key);
                result.add(new Artifact(relative, Files.size(local), digest(local, "SHA-256")));
            }
        }
        result.sort(Comparator.comparing(artifact -> artifact.path().toString()));
        return List.copyOf(result);
    }

    private static void verifyRepository(Path repository, List<Artifact> artifacts) throws Exception {
        var remaining = new HashSet<Path>();
        for (Artifact artifact : artifacts) {
            Path file = repository.resolve(artifact.path());
            if (!remaining.add(artifact.path())) throw new IOException("Duplicate prepared artifact path");
            if (Files.size(file) != artifact.bytes() || !digest(file, "SHA-256").equals(artifact.sha256()))
                throw new IOException("Prepared repository was modified: " + artifact.path());
        }
        try (var paths = Files.walk(repository)) {
            for (Iterator<Path> iterator = paths.iterator(); iterator.hasNext();) {
                Path file = iterator.next();
                if (Files.isSymbolicLink(file)) throw new IOException("Prepared repository contains a symlink");
                if (!Files.isRegularFile(file)) continue;
                String name = file.getFileName().toString();
                if ((name.endsWith(".jar") || name.endsWith(".pom")) && !remaining.remove(repository.relativize(file)))
                    throw new IOException("Artifact was added after repository preparation: " + file);
            }
        }
        if (!remaining.isEmpty()) throw new IOException("Prepared artifacts disappeared");
    }

    /** Negative controls for the inventory checks, independent of Maven's resolver. */
    private static void verifyInventoryContracts() throws Exception {
        Path control = Files.createTempDirectory("jx-artifact-inventory-control-");
        Path repository = control.resolve("repository");
        Path cache = control.resolve("cache");
        Path relative = Path.of("fixture/contract/1/contract-1.jar");
        Files.createDirectories(repository.resolve(relative).getParent());
        Files.writeString(repository.resolve(relative), "prepared bytes");
        List<Artifact> original = List.of(new Artifact(relative, Files.size(repository.resolve(relative)), digest(repository.resolve(relative), "SHA-256")));
        copyTree(repository, cache, Set.of());
        Files.writeString(cache.resolve(relative).resolveSibling("_remote.repositories"), "contract-1.jar>" + REPOSITORY_ID + "=\n");
        verifyRepository(repository, original);
        if (verifyResolution(cache, repository, original).size() != 1) throw new IOException("Valid inventory control failed");
        Path extra = Path.of("fixture/contract/1/late-1.jar");
        Files.writeString(repository.resolve(extra), "late bytes");
        Files.writeString(cache.resolve(extra), "late bytes");
        Files.writeString(cache.resolve(relative).resolveSibling("_remote.repositories"),
                "contract-1.jar>" + REPOSITORY_ID + "=\nlate-1.jar>" + REPOSITORY_ID + "=\n");
        try { verifyRepository(repository, original); throw new IllegalStateException("Late repository artifact was accepted"); }
        catch (IOException expected) { if (!expected.getMessage().startsWith("Artifact was added")) throw expected; }
        try { verifyResolution(cache, repository, original); throw new IllegalStateException("Late resolved artifact was accepted"); }
        catch (IOException expected) { if (!expected.getMessage().startsWith("Resolved artifact is absent")) throw expected; }
        Files.delete(cache.resolve(extra));
        Files.delete(repository.resolve(extra));
        Files.writeString(cache.resolve(relative), "changed bytes");
        try { verifyResolution(cache, repository, original); throw new IllegalStateException("Changed resolved bytes were accepted"); }
        catch (IOException expected) { if (!expected.getMessage().startsWith("Resolved artifact is absent")) throw expected; }
        System.out.println("PASS: inventory positive control, late additions and changed bytes rejected");
    }

    private static void browser(Path proof) throws Exception {
        Path consumer = proof.resolve("consumer");
        Path site = consumer.resolve("target/site").toRealPath();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                try (exchange) {
                    String path = exchange.getRequestURI().getPath();
                    String file = switch (path) { case "/" -> "index.html"; case "/application.js" -> "application.js";
                        case "/application.js.map" -> "application.js.map"; default -> null; };
                    if (file == null) { exchange.sendResponseHeaders(path.equals("/favicon.ico") ? 204 : 404, -1); return; }
                    Path asset = site.resolve(file);
                    exchange.getResponseHeaders().set("Content-Type", file.endsWith(".html") ? "text/html; charset=utf-8" : "text/javascript; charset=utf-8");
                    exchange.sendResponseHeaders(200, Files.size(asset));
                    Files.copy(asset, exchange.getResponseBody());
                }
            });
            server.start();
            try {
                run(List.of("node", consumer.resolve("node_modules/@playwright/test/cli.js").toString(), "test", "-c",
                        consumer.resolve("playwright.config.js").toString()), consumer,
                        Map.of("ARTIFACT_CONSUMER_URL", "http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofMinutes(2), true);
            } finally { server.stop(0); }
        }
        Path receipt = proof.resolve("receipt.txt");
        Files.writeString(receipt, Files.readString(receipt).replace("Browser qualification: pending", "Browser qualification: passed"));
        System.out.println("PASS: real Chrome browser consumer. " + receipt);
    }

    private static String manifest(List<Artifact> artifacts) {
        var text = new StringBuilder("path\tbytes\tsha256\n");
        for (Artifact artifact : artifacts) text.append(artifact.path().toString().replace('\\', '/')).append('\t')
                .append(artifact.bytes()).append('\t').append(artifact.sha256()).append('\n');
        return text.toString();
    }

    private static String digest(Path file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[16_384];
            for (int count; (count = input.read(buffer)) >= 0;) if (count != 0) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String readBounded(Path file) throws IOException {
        if (Files.size(file) > 16 * 1024 * 1024) throw new IOException("Proof log exceeds 16 MiB: " + file);
        return Files.readString(file);
    }

    private static void assertAbsent(Path... paths) throws IOException {
        for (Path path : paths) if (Files.exists(path)) throw new IOException("Expected a clean consumer input: " + path);
    }

    private static void copyTree(Path source, Path target, Set<Path> excluded) throws IOException {
        int count = 0;
        try (var paths = Files.walk(source)) {
            for (Iterator<Path> iterator = paths.iterator(); iterator.hasNext();) {
                Path input = iterator.next();
                if (++count > MAX_FILES * 4 || Files.isSymbolicLink(input)) throw new IOException("Unsafe/oversized proof input tree: " + source);
                Path relative = source.relativize(input);
                if (excluded.contains(relative)) continue;
                Path output = target.resolve(relative);
                if (Files.isDirectory(input)) Files.createDirectories(output);
                else if (Files.isRegularFile(input)) {
                    if (Files.size(input) > MAX_FILE_BYTES) throw new IOException("Proof input file exceeds bound: " + input);
                    Files.copy(input, output);
                } else throw new IOException("Unsupported proof input: " + input);
            }
        }
    }

    private static int run(List<String> command, Path directory, Map<String, String> environment, Duration timeout, boolean requireSuccess)
            throws IOException, InterruptedException {
        var builder = new ProcessBuilder(command).directory(directory.toFile()).inheritIO();
        // Preserve toolchain/proxy configuration, but no ambient Maven launch extensions or
        // npm settings may select another repository, inject a Java agent or reuse outputs.
        for (String key : new ArrayList<>(builder.environment().keySet()))
            if (key.startsWith("MAVEN_") || key.equals("M2_HOME") || key.equals("CLASSPATH")
                    || key.equals("JAVA_TOOL_OPTIONS") || key.equals("JDK_JAVA_OPTIONS") || key.equals("_JAVA_OPTIONS")
                    || key.equals("NODE_OPTIONS") || key.equals("NODE_PATH")
                    || key.toLowerCase(Locale.ROOT).startsWith("npm_config_")) builder.environment().remove(key);
        builder.environment().putAll(environment);
        Process process = builder.start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new IOException("Proof step timed out: " + command.getFirst());
            int exit = process.exitValue();
            if (requireSuccess && exit != 0) throw new IOException("Proof step failed with exit " + exit + ": " + command.getFirst());
            return exit;
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                if (!process.waitFor(10, TimeUnit.SECONDS)) throw new IOException("Proof process did not terminate: " + command.getFirst());
            }
        }
    }
}
