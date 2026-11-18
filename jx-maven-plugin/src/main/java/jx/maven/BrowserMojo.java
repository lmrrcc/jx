package jx.maven;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Bundle TeaVM output and web assets. Development generations share one React/React DOM vendor
 * runtime: unmounting a root does not remove React's delegated native event listeners, so rebundling
 * the renderer per update would retain old renderer generations. Production remains a standalone ES
 * module. Run npm ci first; changing the vendor dependency versions requires a new browser session. */
@Mojo(name = "browser", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class BrowserMojo extends AbstractMojo {
    private static final Set<String> RESERVED = Set.of("application.js", "application.js.map", "jx-vendor.js", "jx-vendor.js.map");
    private static final List<String> VENDOR_FILES = List.of("jx-vendor.js", "jx-vendor.js.map");
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
    @Parameter(defaultValue = "${project.build.directory}/browser/application.js", property = "jx.compiledJavaScript", required = true)
    private File compiledJavaScript;
    @Parameter(defaultValue = "${project.basedir}/src/main/web", property = "jx.webDirectory", required = true)
    private File webDirectory;
    @Parameter(defaultValue = "${project.build.directory}/site", property = "jx.browserDirectory", required = true)
    private File browserDirectory;
    @Parameter(defaultValue = "${project.build.directory}/jx/browser", property = "jx.browserMetadataDirectory", required = true)
    private File browserMetadataDirectory;
    @Parameter(defaultValue = "node", property = "jx.nodeExecutable")
    private String nodeExecutable;
    @Parameter(defaultValue = "false", property = "jx.hotReload")
    private boolean hotReload;

    @Override public void execute() throws MojoExecutionException {
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
            String bootstrap = hotReload
                    ? "export { main };\nconst hot = globalThis.__JX_HOT__;\nif (!hot?.defer && !hot?.initialized) { if (hot?.boot) hot.boot(main).catch(console.error); else main(); }\n"
                    : "main();\n";
            Files.writeString(entry, "// Generated framework bootstrap; application behavior is Java.\nimport { main } from "
                    + quote(imported) + ";\n" + bootstrap, StandardCharsets.UTF_8);
            Path bundle = metadata.resolve("bundle/application.js");
            Files.createDirectories(bundle.getParent());
            List<String> aliases = new ArrayList<>();
            if (hotReload) {
                Path vendorEntry = metadata.resolve("vendor.mjs");
                Files.writeString(vendorEntry, "// Generated shared development renderer.\n"
                        + "import * as React from 'react';\nimport * as ReactDOM from 'react-dom/client';\n"
                        + "import * as ReactDOMCore from 'react-dom';\nexport { React, ReactDOM, ReactDOMCore };\n", StandardCharsets.UTF_8);
                Path vendor = metadata.resolve("bundle/jx-vendor.js");
                runEsbuild(esbuild, npmRoot, List.of(vendorEntry.toString(), "--bundle", "--platform=browser", "--format=iife",
                        "--global-name=__JX_VENDOR__", "--footer:js=globalThis.__JX_VENDOR__ = __JX_VENDOR__;", "--sourcemap", "--outfile=" + vendor));
                Path reactShim = metadata.resolve("react.cjs");
                Path clientShim = metadata.resolve("react-dom-client.cjs");
                Path domShim = metadata.resolve("react-dom.cjs");
                Files.writeString(reactShim, "module.exports = globalThis.__JX_VENDOR__.React;\n", StandardCharsets.UTF_8);
                Files.writeString(clientShim, "module.exports = globalThis.__JX_VENDOR__.ReactDOM;\n", StandardCharsets.UTF_8);
                Files.writeString(domShim, "module.exports = globalThis.__JX_VENDOR__.ReactDOMCore;\n", StandardCharsets.UTF_8);
                aliases.addAll(List.of("--alias:react=" + reactShim, "--alias:react-dom/client=" + clientShim, "--alias:react-dom=" + domShim));
            }
            List<String> command = new ArrayList<>(List.of(entry.toString(), "--bundle", "--platform=browser",
                    hotReload ? "--format=iife" : "--format=esm", "--sourcemap", "--outfile=" + bundle, "--metafile=" + metadata.resolve("metafile.json")));
            command.addAll(aliases);
            if (hotReload) command.addAll(List.of("--global-name=__JX_BUNDLE__", "--footer:js=globalThis.__JX_BUNDLE__ = __JX_BUNDLE__;"));
            runEsbuild(esbuild, npmRoot, command);
            // Validate/copy source ownership before installing generated files with reserved names.
            copyAssets(web, site, metadata.resolve("assets.txt"));
            if (hotReload) publishVendor(metadata.resolve("bundle"), site, metadata.resolve("vendor-assets.txt"));
            else removeVendor(site, metadata.resolve("vendor-assets.txt"));
            Files.copy(bundle, safe(site, Path.of("application.js")), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(bundle.resolveSibling("application.js.map"), safe(site, Path.of("application.js.map")), StandardCopyOption.REPLACE_EXISTING);
            getLog().info("JX browser assets: " + site);
        } catch (IOException | IllegalArgumentException ex) {
            throw new MojoExecutionException("Cannot build JX browser assets", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("JX browser bundling interrupted", ex);
        }
    }

    private void runEsbuild(Path esbuild, Path cwd, List<String> arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        // npm may replace the JavaScript launcher with the platform's native binary.
        try (var input = Files.newInputStream(esbuild)) {
            if (input.read() == '#' && input.read() == '!') command.add(nodeExecutable);
        }
        command.add(esbuild.toString()); command.addAll(arguments);
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).inheritIO().start();
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) throw new IOException("Browser bundling exceeded 120 seconds");
            if (process.exitValue() != 0) throw new IOException("esbuild failed with exit code " + process.exitValue());
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }

    /** Generated vendor ownership is separate from copied application assets and fixed to two files. */
    static void publishVendor(Path input, Path output, Path manifest) throws IOException {
        boolean owned = Files.exists(manifest);
        if (owned && !Files.readAllLines(manifest).equals(VENDOR_FILES)) throw new IOException("Invalid vendor ownership manifest");
        for (String name : VENDOR_FILES) {
            Path target = safe(output, Path.of(name));
            if (Files.exists(target) && !owned) throw new IOException("Refusing to overwrite an unowned browser vendor asset: " + target);
            if (!Files.isRegularFile(input.resolve(name))) throw new IOException("Missing generated vendor asset: " + name);
        }
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, String.join("\n", VENDOR_FILES) + "\n", StandardCharsets.UTF_8);
        for (String name : VENDOR_FILES) Files.copy(input.resolve(name), safe(output, Path.of(name)), StandardCopyOption.REPLACE_EXISTING);
    }

    static void removeVendor(Path output, Path manifest) throws IOException {
        if (!Files.exists(manifest)) return;
        if (!Files.readAllLines(manifest).equals(VENDOR_FILES)) throw new IOException("Invalid vendor ownership manifest");
        for (String name : VENDOR_FILES) Files.deleteIfExists(safe(output, Path.of(name)));
        Files.delete(manifest);
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
                    if (RESERVED.contains(relative.toString()))
                        throw new IOException("Application and shared-vendor JavaScript/maps are reserved generated browser artifacts");
                    current.add(relative);
                }
            }
        }
        List<Path> previous = Files.exists(manifest) ? Files.readAllLines(manifest).stream().filter(s -> !s.isBlank()).map(Path::of).toList() : List.of();
        for (Path relative : previous) {
            if (RESERVED.contains(relative.toString()))
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
