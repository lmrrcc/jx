package jx.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/** Attach browser assets as a web classifier JAR for ordinary server classpath serving.
 * Only the built site is packaged; source maps, Java/classes and dot files are excluded. */
@Mojo(name = "web-jar", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class WebJarMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
    @Component
    private MavenProjectHelper helper;
    @Parameter(defaultValue = "${project.build.directory}/site", property = "jx.browserDirectory", required = true)
    private File browserDirectory;

    @Override public void execute() throws MojoExecutionException {
        try {
            Path output = Path.of(project.getBuild().getDirectory(), project.getBuild().getFinalName() + "-web.jar");
            write(browserDirectory.toPath(), output);
            helper.attachArtifact(project, "jar", "web", output.toFile());
            getLog().info("JX web artifact: " + output);
        } catch (IOException failure) { throw new MojoExecutionException("Cannot package JX browser assets", failure); }
    }

    static void write(Path site, Path output) throws IOException {
        Path root = site.toAbsolutePath().normalize();
        output = output.toAbsolutePath().normalize();
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root) || output.startsWith(root)) {
            throw new IOException("Web JAR requires a separate, built, nonsymlink site directory");
        }
        List<Path> files;
        try (var entries = Files.walk(root)) {
            var all = entries.limit(16_385).sorted().toList();
            if (all.size() > 16_384) throw new IOException("Too many web artifact entries");
            for (Path file : all) {
                if (Files.isSymbolicLink(file)) throw new IOException("Web artifact cannot contain symlinks: " + file);
                for (Path part : root.relativize(file)) {
                    String name = part.toString();
                    if (name.indexOf('\\') >= 0 || name.chars().anyMatch(Character::isISOControl)) {
                        throw new IOException("Noncanonical web artifact path: " + file);
                    }
                }
            }
            files = all.stream().filter(Files::isRegularFile).filter(file -> included(root.relativize(file))).toList();
        }
        if (!files.contains(root.resolve("index.html")) || !files.contains(root.resolve("application.js"))) {
            throw new IOException("Built web artifact requires index.html and application.js");
        }
        Files.createDirectories(output.getParent());
        if (Files.isSymbolicLink(output)) throw new IOException("Web artifact output cannot be a symlink");
        Path temporary = Files.createTempFile(output.getParent(), ".jx-web-", ".jar");
        try {
            try (var jar = new JarOutputStream(Files.newOutputStream(temporary))) {
                long size = 0;
                for (Path file : files) {
                    size += Files.size(file);
                    if (size > 268_435_456) throw new IOException("Web artifact exceeds 256 MiB");
                    var entry = new JarEntry("META-INF/resources/" + root.relativize(file).toString().replace('\\', '/'));
                    entry.setTime(0);
                    jar.putNextEntry(entry);
                    Files.copy(file, jar);
                    jar.closeEntry();
                }
            }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static boolean included(Path path) {
        for (Path part : path) if (part.toString().startsWith(".")) return false;
        String name = path.getFileName().toString();
        return !name.endsWith(".map") && !name.endsWith(".java") && !name.endsWith(".jx") && !name.endsWith(".class");
    }
}
