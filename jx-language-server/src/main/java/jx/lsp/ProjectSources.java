package jx.lsp;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jx.compiler.SourceFile;

/** Read-only Maven-convention discovery. Never invokes Maven, processors, or project scripts. */
final class ProjectSources {
    static final int MAX_DOCUMENT_LENGTH = 1_048_576;
    private static final int MAX_FILES = 2_000;
    private static final Set<String> IGNORED = Set.of("target", "build", "dist", "node_modules", ".git", ".cache", ".idea", ".gradle");

    record Snapshot(List<SourceFile> sources, List<Path> javaSources, List<Path> classpath) {}

    static Path projectRoot(Path file, List<Path> roots) {
        Path boundary = roots.stream().filter(file::startsWith).max(java.util.Comparator.comparingInt(Path::getNameCount))
                .orElse(file.getParent());
        for (Path at = file.getParent(); at != null && at.startsWith(boundary); at = at.getParent()) {
            if (Files.isRegularFile(at.resolve("pom.xml"))) return at;
        }
        return boundary;
    }

    static Snapshot discover(Path project, Map<Path, String> overlays, List<Path> configuredClasspath,
                             List<Path> workspaceRoots) throws IOException {
        Map<Path, SourceFile> sources = new LinkedHashMap<>();
        List<Path> javaSources = new ArrayList<>();
        Set<Path> classpath = new LinkedHashSet<>(configuredClasspath);
        addOwnClasses(classpath, jx.View.class);
        if (Files.isDirectory(project)) {
            Files.walkFileTree(project, new SimpleFileVisitor<>() {
                int count;
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (Thread.currentThread().isInterrupted()) return FileVisitResult.TERMINATE;
                    if (!dir.equals(project) && IGNORED.contains(dir.getFileName().toString())) return FileVisitResult.SKIP_SUBTREE;
                    if (!dir.equals(project) && Files.isRegularFile(dir.resolve("pom.xml"))) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".jx") && !name.endsWith(".java")) return FileVisitResult.CONTINUE;
                    if (++count > MAX_FILES) throw new IOException("Project exceeds " + MAX_FILES + " source files; open an individual Maven module");
                    if (attrs.size() > MAX_DOCUMENT_LENGTH) throw new IOException("Source exceeds 1 MiB: " + file);
                    if (name.endsWith(".jx")) sources.put(file, new SourceFile(file,
                            overlays.containsKey(file) ? overlays.get(file) : Files.readString(file)));
                    else if (file.startsWith(project.resolve("src/main/java")) || file.startsWith(project.resolve("src/test/java"))) javaSources.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        for (var entry : overlays.entrySet()) {
            if (projectRoot(entry.getKey(), workspaceRoots).equals(project)) sources.put(entry.getKey(), new SourceFile(entry.getKey(), entry.getValue()));
        }
        for (Path root : workspaceRoots) {
            if (!Files.isDirectory(root)) continue;
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                int directories;
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (++directories > 10_000 || Thread.currentThread().isInterrupted()) return FileVisitResult.TERMINATE;
                    if (dir.getFileName().toString().equals("target")) {
                        for (String kind : List.of("classes", "test-classes")) {
                            Path classes = dir.resolve(kind);
                            if (Files.isDirectory(classes)) classpath.add(classes);
                        }
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return IGNORED.contains(dir.getFileName().toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }
            });
        }
        return new Snapshot(sources.values().stream().sorted(java.util.Comparator.comparing(s -> s.path().toString())).toList(),
                List.copyOf(javaSources), List.copyOf(classpath));
    }

    private static void addOwnClasses(Set<Path> paths, Class<?> type) {
        try { paths.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI())); }
        catch (java.net.URISyntaxException e) { throw new IllegalStateException("Invalid language-server installation URI", e); }
    }
}
