package jx.maven;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Explicit development command: watch source/assets, run the project's package lifecycle, and reload a loopback page.
 * Requires a Maven wrapper in this project or an ancestor. It never preserves application state across rebuilds.
 */
@Mojo(name = "dev", threadSafe = false)
public final class DevMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}/site", property = "jx.browserDirectory", required = true)
    private File browserDirectory;

    @Parameter(defaultValue = "4173", property = "jx.port")
    private int port;

    @Parameter(defaultValue = "package", property = "jx.buildGoal")
    private String buildGoal;

    @Parameter(defaultValue = "180", property = "jx.buildTimeoutSeconds")
    private int buildTimeoutSeconds;

    /** Optional explicit Maven Wrapper executable; default searches this project and its ancestors. */
    @Parameter(property = "jx.mavenExecutable")
    private File mavenExecutable;

    /** Optional extra watched source/asset roots in addition to src, browser, public and pom.xml. */
    @Parameter
    private List<File> watchDirectories;

    private final AtomicReference<Process> running = new AtomicReference<>();

    @Override public void execute() throws MojoExecutionException {
        if (port < 1 || port > 65535) throw new MojoExecutionException("jx.port must be between 1 and 65535");
        if (!List.of("package", "verify").contains(buildGoal))
            throw new MojoExecutionException("jx.buildGoal must be package or verify, without recursive dev goals");
        if (buildTimeoutSeconds < 10 || buildTimeoutSeconds > 1800)
            throw new MojoExecutionException("jx.buildTimeoutSeconds must be between 10 and 1800");
        Path basedir = project.getBasedir().toPath().toAbsolutePath().normalize();
        List<Path> watched = new ArrayList<>(List.of(basedir.resolve("src"), basedir.resolve("browser"),
                basedir.resolve("public"), basedir.resolve("pom.xml")));
        if (watchDirectories != null) watchDirectories.forEach(file -> watched.add(file.toPath()));
        var status = new AtomicReference<>(new DevServer.Status(0, "building"));
        Thread owner = Thread.currentThread();
        Thread shutdown = new Thread(() -> {
            owner.interrupt();
            stopProcess();
        }, "jx-dev-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
        try (DevServer server = new DevServer(browserDirectory.toPath(), port, status)) {
            Path wrapper = mavenExecutable == null ? findWrapper(basedir) : mavenExecutable.toPath().toAbsolutePath();
            getLog().info("JX development server: http://127.0.0.1:" + server.port() + " (Ctrl+C stops the server and build)");
            String observed = fingerprint(watched);
            build(wrapper, status);
            String built = observed;
            long lastChange = System.nanoTime();
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(250);
                String current = fingerprint(watched);
                if (!current.equals(observed)) { observed = current; lastChange = System.nanoTime(); }
                if (!observed.equals(built) && System.nanoTime() - lastChange >= 300_000_000L) {
                    built = observed;
                    build(wrapper, status);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            getLog().info("JX development server stopped");
        } catch (IOException | IllegalArgumentException ex) {
            throw new MojoExecutionException("Cannot run JX development server", ex);
        } finally {
            stopProcess();
            try { Runtime.getRuntime().removeShutdownHook(shutdown); }
            catch (IllegalStateException ignored) { /* JVM shutdown already owns the hook. */ }
        }
    }

    private void build(Path wrapper, AtomicReference<DevServer.Status> status) throws IOException, InterruptedException {
        status.updateAndGet(previous -> new DevServer.Status(previous.version(), "building"));
        List<String> command = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        if (windows) command.addAll(List.of("cmd.exe", "/d", "/c"));
        command.add(wrapper.toString());
        command.addAll(List.of("-B", "-ntp", "-f", project.getFile().getAbsolutePath()));
        String repository = System.getProperty("maven.repo.local");
        if (repository != null) command.add("-Dmaven.repo.local=" + Path.of(repository).toAbsolutePath());
        command.add(buildGoal);
        getLog().info("Building JX browser assets with the project's " + buildGoal + " lifecycle");
        Process process = new ProcessBuilder(command).directory(project.getBasedir()).inheritIO().start();
        running.set(process);
        try {
            boolean exited = process.waitFor(buildTimeoutSeconds, TimeUnit.SECONDS);
            if (!exited) { stopProcess(); getLog().error("JX build timed out after " + buildTimeoutSeconds + " seconds"); }
            boolean success = exited && process.exitValue() == 0;
            status.updateAndGet(previous -> new DevServer.Status(previous.version() + (success ? 1 : 0), success ? "ready" : "failed"));
            if (!success) getLog().error("JX build failed; fix the source and save to retry");
        } finally {
            if (process.isAlive()) stopProcess();
            running.compareAndSet(process, null);
        }
    }
    private void stopProcess() {
        Process process = running.getAndSet(null);
        if (process == null) return;
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.reversed().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                descendants.reversed().stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            descendants.reversed().stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly(); Thread.currentThread().interrupt();
        }
    }
    static Path findWrapper(Path start) throws IOException {
        String filename = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "mvnw.cmd" : "mvnw";
        for (Path path = start; path != null; path = path.getParent()) {
            Path candidate = path.resolve(filename);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IOException("No Maven Wrapper found. Configure jx.mavenExecutable explicitly.");
    }

    /** Content hashes avoid missed same-size/same-timestamp edits. Work is bounded per watch iteration. */
    static String fingerprint(List<Path> roots) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (Files.isRegularFile(root)) files.add(root);
            else if (Files.isDirectory(root)) try (var paths = Files.walk(root)) {
                files.addAll(paths.filter(Files::isRegularFile).limit(10_001).toList());
            } catch (java.nio.file.NoSuchFileException ignored) { /* Atomic editor save; the next snapshot observes the new path. */ }
        }
        files = files.stream().map(p -> p.toAbsolutePath().normalize()).distinct().sorted().toList();
        if (files.size() > 10_000) throw new IOException("Development watch roots exceed 10,000 files; narrow watchDirectories");
        long total = 0;
        for (Path file : files) {
          try {
            long size = Files.size(file);
            total += size;
            if (size > 32L * 1024 * 1024 || total > 128L * 1024 * 1024)
                throw new IOException("Development watch input exceeds size limits: " + file);
            digest.update(file.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(file));
            digest.update((byte) 0xff);
          } catch (java.nio.file.NoSuchFileException ignored) { digest.update((byte) 0xfe); }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
