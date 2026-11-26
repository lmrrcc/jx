package jx.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jx.compiler.RecordCodecGenerator;

/** Owns only the codec sources named and hashed in this goal's bounded manifest. */
final class RecordCodecGeneration {
    record Result(int written, int removed) {}

    Result generate(List<String> types, List<Path> classPath, Path output, Path metadata, Path classes) throws IOException {
        var sources = new RecordCodecGenerator().generate(types, classPath);
        output = output.toAbsolutePath().normalize();
        metadata = metadata.toAbsolutePath().normalize();
        classes = classes.toAbsolutePath().normalize();
        Path manifest = GenerationService.safe(metadata, Path.of("record-codecs.tsv"));
        var previous = read(manifest);
        var current = new LinkedHashMap<Path, RecordCodecGenerator.Source>();
        for (var source : sources) current.put(source.relativePath(), source);

        // Validate ownership for both deletion and replacement before the first write.
        for (var entry : previous.entrySet()) {
            Path file = GenerationService.safe(output, entry.getKey());
            if (Files.exists(file) && !hash(Files.readString(file)).equals(entry.getValue())) {
                throw new IOException("Refusing to modify an edited generated codec: " + file);
            }
        }
        for (var path : current.keySet()) {
            Path file = GenerationService.safe(output, path);
            if (!previous.containsKey(path) && Files.exists(file)) {
                throw new IOException("Refusing to overwrite an unowned codec: " + file);
            }
        }
        int written = 0;
        int removed = 0;
        for (Path path : previous.keySet()) if (!current.containsKey(path)) {
            GenerationService.deleteClasses(classes, List.of(type(path)), path.getFileName().toString());
            if (Files.deleteIfExists(GenerationService.safe(output, path))) removed++;
        }
        var manifestText = new StringBuilder("jx-record-codecs-v1\n");
        for (var source : sources) {
            Path file = GenerationService.safe(output, source.relativePath());
            if (!Files.exists(file) || !Files.readString(file).equals(source.content())) {
                GenerationService.deleteClasses(classes, List.of(source.qualifiedName()), file.getFileName().toString());
                GenerationService.write(file, source.content());
                written++;
            }
            manifestText.append(source.relativePath().toString().replace('\\', '/')).append('\t')
                    .append(hash(source.content())).append('\n');
        }
        GenerationService.write(manifest, manifestText.toString());
        return new Result(written, removed);
    }

    private static Map<Path,String> read(Path manifest) throws IOException {
        var result = new LinkedHashMap<Path,String>();
        if (!Files.exists(manifest)) return result;
        if (Files.size(manifest) > 1_048_576) throw new IOException("Codec ownership manifest exceeds limit");
        var lines = Files.readAllLines(manifest);
        if (lines.isEmpty() || !lines.getFirst().equals("jx-record-codecs-v1") || lines.size() > 65) {
            throw new IOException("Invalid codec ownership manifest: " + manifest);
        }
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 2 || !fields[1].matches("[a-f0-9]{64}")) throw new IOException("Invalid codec manifest row");
            Path path = Path.of(fields[0]);
            GenerationService.safe(manifest.getParent(), path);
            if (!path.toString().endsWith(".java") || !javax.lang.model.SourceVersion.isName(type(path))) {
                throw new IOException("Invalid codec source path: " + path);
            }
            if (result.putIfAbsent(path, fields[1]) != null) throw new IOException("Duplicate codec output: " + path);
        }
        return result;
    }
    private static String type(Path source) {
        String name = source.toString().replace('\\', '.').replace('/', '.');
        return name.substring(0, name.length() - ".java".length());
    }
    private static String hash(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
