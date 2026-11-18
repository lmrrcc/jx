package jx.maven;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Bundle TeaVM's ES module with the locked React runtime and copy application web assets. Run npm ci first. */
@Mojo(name = "browser", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class BrowserMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
    @Parameter(defaultValue = "${project.build.directory}/browser/application.js", property = "jx.compiledJavaScript", required = true)
    private File compiledJavaScript;
    @Parameter(defaultValue = "${project.basedir}/src/main/web", property = "jx.webDirectory", required = true)
    private File webDirectory;
    @Parameter(defaultValue = "${project.build.directory}/site", property = "jx.browserDirectory", required = true)
    private File browserDirectory;
    @Parameter(defaultValue = "${project.build.directory}/jx/browser", required = true)
    private File browserMetadataDirectory;
    @Parameter(defaultValue = "node", property = "jx.nodeExecutable")
    private String nodeExecutable;

    @Override public void execute() throws MojoExecutionException {
        Process process = null;
        try {
            Path compiled = compiledJavaScript.toPath().toAbsolutePath().normalize();
            if (!Files.isRegularFile(compiled)) throw new IOException("TeaVM output missing: " + compiled + ". Bind TeaVM compile to process-classes.");
            Path npmRoot = npmRoot(project.getBasedir().toPath());
            Path esbuild = npmRoot.resolve("node_modules/esbuild/bin/esbuild");
            if (!Files.isRegularFile(esbuild)) throw new IOException("Locked browser tools missing. Run npm ci in " + npmRoot);
            Path metadata = browserMetadataDirectory.toPath().toAbsolutePath().normalize();
            Path site = browserDirectory.toPath().toAbsolutePath().normalize();
            Path web = webDirectory.toPath().toAbsolutePath().normalize();
            if (site.startsWith(web) || web.startsWith(site)) throw new IOException("Browser output and web input directories must not overlap");
            Files.createDirectories(metadata);
            Files.createDirectories(site);
            Path entry = metadata.resolve("entry.mjs");
            String imported = metadata.relativize(compiled).toString().replace('\\', '/');
            if (!imported.startsWith(".")) imported = "./" + imported;
            Files.writeString(entry, "// Generated framework bootstrap; application behavior is Java.\nimport { main } from "
                    + quote(imported) + ";\nmain();\n", StandardCharsets.UTF_8);
            Path bundle = metadata.resolve("bundle/application.js");
            Files.createDirectories(bundle.getParent());
            List<String> command = new ArrayList<>();
            // npm may replace the JS launcher with the platform's native esbuild binary.
            try (var input = Files.newInputStream(esbuild)) {
                if (input.read() == '#' && input.read() == '!') command.add(nodeExecutable);
            }
            command.addAll(List.of(esbuild.toString(), entry.toString(), "--bundle", "--platform=browser",
                    "--format=esm", "--sourcemap", "--outfile=" + bundle, "--metafile=" + metadata.resolve("metafile.json")));
            process = new ProcessBuilder(command).directory(npmRoot.toFile()).inheritIO().start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) throw new IOException("Browser bundling exceeded 120 seconds");
            if (process.exitValue() != 0) throw new IOException("esbuild failed with exit code " + process.exitValue());
            Files.copy(bundle, safe(site, Path.of("application.js")), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(bundle.resolveSibling("application.js.map"), safe(site, Path.of("application.js.map")), StandardCopyOption.REPLACE_EXISTING);
            copyAssets(web, site, metadata.resolve("assets.txt"));
            getLog().info("JX browser assets: " + site);
        } catch (IOException | IllegalArgumentException ex) {
            throw new MojoExecutionException("Cannot build JX browser assets", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("JX browser bundling interrupted", ex);
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }

    static Path npmRoot(Path start) throws IOException {
        for (Path path = start.toAbsolutePath().normalize(); path != null; path = path.getParent())
            if (Files.isRegularFile(path.resolve("package-lock.json"))) return path;
        throw new IOException("No package-lock.json found. Provide pinned React, React DOM and esbuild dependencies and run npm ci.");
    }
    static void copyAssets(Path input, Path output, Path manifest) throws IOException {
        List<Path> current = new ArrayList<>();
        if (Files.exists(input)) {
            if (Files.isSymbolicLink(input)) throw new IOException("Web source root cannot be a symlink: " + input);
            try (var paths = Files.walk(input)) {
                for (Path source : paths.sorted().toList()) {
                    if (Files.isSymbolicLink(source)) throw new IOException("Web assets cannot be symlinks: " + source);
                    if (!Files.isRegularFile(source)) continue;
                    Path relative = input.relativize(source);
                    if (relative.toString().equals("application.js") || relative.toString().equals("application.js.map"))
                        throw new IOException("application.js and its map are reserved generated browser artifacts");
                    current.add(relative);
                }
            }
        }
        List<Path> previous = Files.exists(manifest) ? Files.readAllLines(manifest).stream().filter(s -> !s.isBlank()).map(Path::of).toList() : List.of();
        for (Path relative : previous) {
            if (relative.toString().equals("application.js") || relative.toString().equals("application.js.map"))
                throw new IOException("Invalid asset ownership manifest");
            safe(output, relative);
        }
        for (Path relative : current) {
            Path target = safe(output, relative);
            if (Files.exists(target) && !previous.contains(relative))
                throw new IOException("Refusing to overwrite an unowned browser asset: " + target);
        }
        for (Path relative : previous) if (!current.contains(relative)) Files.deleteIfExists(safe(output, relative));
        for (Path relative : current) {
            Path target = safe(output, relative);
            Files.createDirectories(target.getParent());
            if (!Files.exists(target) || Files.mismatch(input.resolve(relative), target) != -1)
                Files.copy(input.resolve(relative), target, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, String.join("\n", current.stream().map(p -> p.toString().replace('\\', '/')).toList()) + "\n");
    }
    private static Path safe(Path root, Path relative) throws IOException {
        if (relative.isAbsolute() || !relative.equals(relative.normalize()) || relative.startsWith("..") || relative.toString().isEmpty())
            throw new IOException("Unsafe browser asset path: " + relative);
        if (Files.isSymbolicLink(root)) throw new IOException("Browser output cannot be a symlink: " + root);
        Path target = root;
        for (Path part : relative) {
            target = target.resolve(part);
            if (Files.isSymbolicLink(target)) throw new IOException("Browser output cannot contain symlinks: " + target);
        }
        return target;
    }
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
