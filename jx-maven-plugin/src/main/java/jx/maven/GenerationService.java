package jx.maven;

import jx.compiler.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-invocation output ownership. Always reanalyzes inputs; unchanged outputs retain timestamps. */
final class GenerationService {
    record Result(Compilation compilation, int written, int deleted) {}
    static final class InvalidSources extends Exception {
        final List<Diagnostic> diagnostics;
        InvalidSources(List<Diagnostic> diagnostics) { super("JX source validation failed"); this.diagnostics = diagnostics; }
    }

    Result generate(Path sourceDirectory, List<Path> indexDirectories, Path outputDirectory,
                    Path metadataDirectory, Path classDirectory) throws IOException, InvalidSources {
        Path root = sourceDirectory.toAbsolutePath().normalize();
        List<SourceFile> authored = readSources(root);
        Map<Path, SourceFile> indexed = new LinkedHashMap<>();
        for (Path directory : indexDirectories) for (SourceFile source : readSources(directory)) indexed.put(source.path(), source);
        for (SourceFile source : authored) indexed.put(source.path(), source);
        Compilation compilation = new JxCompiler().compile(List.copyOf(indexed.values()));
        if (!compilation.successful()) throw new InvalidSources(compilation.diagnostics());
        List<GeneratedSource> generated = compilation.generatedSources().stream().filter(s -> s.source().path().startsWith(root)).toList();
        outputDirectory = outputDirectory.toAbsolutePath().normalize();
        metadataDirectory = metadataDirectory.toAbsolutePath().normalize();
        classDirectory = classDirectory.toAbsolutePath().normalize();
        Path manifest = safe(metadataDirectory, Path.of("outputs.tsv"));
        Map<Path, List<String>> previous = readManifest(manifest);
        Map<Path, List<String>> current = new LinkedHashMap<>();
        for (GeneratedSource source : generated) current.put(source.relativePath(), source.topLevelTypes());

        // Preflight ownership before any mutation: never overwrite handwritten files in a generated root.
        for (GeneratedSource source : generated) {
            Path output = safe(outputDirectory, source.relativePath());
            Path mapping = safe(metadataDirectory, mapping(source.relativePath()));
            if (!previous.containsKey(source.relativePath()) && (Files.exists(output) || Files.exists(mapping)))
                throw new IOException("Refusing to overwrite an unowned generated path: " + output);
        }

        int written = 0, deleted = 0;
        for (var owned : previous.entrySet()) {
            Path output = safe(outputDirectory, owned.getKey());
            if (!current.containsKey(owned.getKey())) {
                if (Files.deleteIfExists(output)) deleted++;
                Files.deleteIfExists(safe(metadataDirectory, mapping(owned.getKey())));
                deleteClasses(classDirectory, owned.getValue(), owned.getKey().getFileName().toString());
            }
        }
        for (GeneratedSource source : generated) {
            Path output = safe(outputDirectory, source.relativePath());
            boolean changed = !Files.exists(output) || !Files.readString(output).equals(source.javaSource());
            if (changed) {
                deleteClasses(classDirectory, previous.getOrDefault(source.relativePath(), List.of()), source.relativePath().getFileName().toString());
                write(output, source.javaSource()); written++;
            }
            String map = source.source().path().toUri() + "\n" + source.sourceMap().serialize();
            write(safe(metadataDirectory, mapping(source.relativePath())), map);
        }
        StringBuilder ownership = new StringBuilder("jx-outputs-v1\n");
        for (var entry : current.entrySet()) ownership.append(entry.getKey().toString().replace('\\', '/')).append('\t')
                .append(String.join(",", entry.getValue())).append('\n');
        write(manifest, ownership.toString());
        return new Result(compilation, written, deleted);
    }

    static List<SourceFile> readSources(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) return List.of();
        try (var paths = Files.walk(normalized)) {
            List<SourceFile> sources = new ArrayList<>();
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".jx")).sorted().toList())
                sources.add(new SourceFile(path, Files.readString(path, StandardCharsets.UTF_8)));
            return List.copyOf(sources);
        }
    }

    private static Map<Path, List<String>> readManifest(Path path) throws IOException {
        Map<Path, List<String>> result = new LinkedHashMap<>();
        if (!Files.exists(path)) return result;
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty() || !lines.getFirst().equals("jx-outputs-v1")) throw new IOException("Unsupported JX ownership manifest: " + path);
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 2) throw new IOException("Malformed JX ownership manifest: " + path);
            Path relative = Path.of(fields[0]);
            validateRelative(relative);
            if (!relative.toString().endsWith(".java")) throw new IOException("Unexpected generated output " + relative);
            List<String> types = fields[1].isEmpty() ? List.of() : List.of(fields[1].split(","));
            for (String type : types) validateType(type);
            if (result.putIfAbsent(relative, types) != null) throw new IOException("Duplicate manifest output " + relative);
        }
        return result;
    }
    private static Path mapping(Path java) { return Path.of(java.toString().substring(0, java.toString().length() - 5) + ".jxmap"); }
    private static void validateType(String type) throws IOException {
        for (String segment : type.split("\\.", -1)) {
            if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.charAt(0))
                    || segment.chars().skip(1).anyMatch(c -> !Character.isJavaIdentifierPart(c)))
                throw new IOException("Invalid owned Java type " + type);
        }
    }
    private static void validateRelative(Path path) throws IOException {
        if (path.isAbsolute() || !path.equals(path.normalize()) || path.startsWith("..") || path.toString().isEmpty())
            throw new IOException("Unsafe generated path " + path);
    }
    static Path safe(Path root, Path relative) throws IOException {
        validateRelative(relative);
        if (Files.isSymbolicLink(root)) throw new IOException("Generated root must not be a symlink: " + root);
        Path path = root;
        for (Path part : relative) {
            path = path.resolve(part);
            if (Files.isSymbolicLink(path)) throw new IOException("Generated path must not contain symlinks: " + path);
        }
        return path;
    }
    static void deleteClasses(Path directory, List<String> types, String sourceFile) throws IOException {
        for (String type : types) {
            validateType(type);
            Path relative = Path.of(type.replace('.', '/'));
            Path base = safe(directory, relative);
            Path parent = base.getParent();
            if (!Files.isDirectory(parent)) continue;
            String name = base.getFileName().toString();
            try (var files = Files.list(parent)) {
                for (Path file : files.filter(p -> {
                    String candidate = p.getFileName().toString();
                    return candidate.equals(name + ".class") || candidate.startsWith(name + "$") && candidate.endsWith(".class");
                }).toList()) {
                    if (Files.isSymbolicLink(file)) throw new IOException("Owned class cannot be a symlink: " + file);
                    try {
                        var parsed = java.lang.classfile.ClassFile.of().parse(file);
                        String authored = parsed.findAttribute(java.lang.classfile.Attributes.sourceFile())
                                .map(attribute -> attribute.sourceFile().stringValue())
                                .orElseThrow(() -> new IOException("Cannot prove generated class ownership without SourceFile metadata: " + file + ". Run a clean build."));
                        // A handwritten top-level Foo$Bar is not an inner class owned by generated Foo.java.
                        if (authored.equals(sourceFile)) Files.delete(file);
                    } catch (IllegalArgumentException ex) {
                        throw new IOException("Cannot inspect generated class ownership: " + file + ". Run a clean build.", ex);
                    }
                }
            }
        }
    }
    static void write(Path path, String content) throws IOException {
        if (Files.exists(path) && Files.readString(path).equals(content)) return;
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".jx-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
