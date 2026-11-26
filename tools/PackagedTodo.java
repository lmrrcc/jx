import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

/** Starts the real executable artifact from a fresh directory containing only its copied JAR.
 * Playwright owns this launcher; the shutdown hook owns its process and temporary directory. */
public final class PackagedTodo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: java tools/PackagedTodo.java port");
        int port = Integer.parseInt(args[0]);
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Invalid port");
        Path source = Path.of("examples/todo-api/target/todo-api-0.1.0-SNAPSHOT.jar").toAbsolutePath();
        inspect(source);
        try (var run = new Run(source, port)) {
            var shutdown = new Thread(run::close);
            Runtime.getRuntime().addShutdownHook(shutdown);
            try {
                System.out.println("Packaged Todo launched from an isolated directory with only the executable JAR");
                int exit = run.application.waitFor();
                if (exit != 0) throw new IllegalStateException("Packaged application exited " + exit);
            } finally {
                try { Runtime.getRuntime().removeShutdownHook(shutdown); }
                catch (IllegalStateException shuttingDown) { /* The running hook owns cleanup. */ }
            }
        }
    }
    private static void inspect(Path artifact) throws Exception {
        try (var jar = new JarFile(artifact.toFile())) {
            var names = jar.stream().map(entry -> entry.getName()).toList();
            if (names.stream().anyMatch(name -> name.startsWith("BOOT-INF/lib/jx-") || name.startsWith("BOOT-INF/lib/teavm-"))) {
                throw new IllegalStateException("Backend must not package browser framework dependencies");
            }
            var web = jar.getJarEntry("BOOT-INF/lib/todo-app-0.1.0-SNAPSHOT-web.jar");
            if (web == null) throw new IllegalStateException("Missing attached web artifact");
            boolean index = false, application = false;
            try (var assets = new JarInputStream(jar.getInputStream(web))) {
                for (var entry = assets.getNextJarEntry(); entry != null; entry = assets.getNextJarEntry()) {
                    String name = entry.getName();
                    if (!name.startsWith("META-INF/resources/") || name.endsWith(".class") || name.endsWith(".map")) {
                        throw new IllegalStateException("Unexpected web artifact entry: " + name);
                    }
                    index |= name.equals("META-INF/resources/index.html");
                    application |= name.equals("META-INF/resources/application.js");
                }
            }
            if (!index || !application) throw new IllegalStateException("Incomplete web artifact");
        }
    }
    private static final class Run implements AutoCloseable {
        private final Path working = Files.createTempDirectory("jx-packaged-todo-");
        private final AtomicBoolean closed = new AtomicBoolean();
        private Process application;
        Run(Path source, int port) throws Exception {
            try {
                Path artifact = working.resolve("application.jar");
                Files.copy(source, artifact, StandardCopyOption.REPLACE_EXISTING);
                try (var files = Files.list(working)) {
                    if (!files.toList().equals(java.util.List.of(artifact))) throw new IllegalStateException("Launch directory is not isolated");
                }
                application = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-jar", artifact.toString(), "--server.port=" + port, "--spring.datasource.url=jdbc:h2:mem:packaged-todo",
                        "--jx.demo.password-hash={noop}test-only-password", "--jx.demo.other-password-hash={noop}test-only-password",
                        "--spring.main.banner-mode=off", "--logging.level.root=WARN")
                        .directory(working.toFile()).inheritIO().start();
            } catch (Exception failure) { close(); throw failure; }
        }
        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            try {
                if (application != null) {
                    application.destroy();
                    if (!application.waitFor(5, TimeUnit.SECONDS)) {
                        application.destroyForcibly();
                        if (!application.waitFor(5, TimeUnit.SECONDS)) throw new IllegalStateException("Packaged process did not stop");
                    }
                }
                try (var paths = Files.walk(working)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                System.err.println("Cannot finish packaged Todo cleanup: " + failure.getMessage());
            }
        }
    }
}
