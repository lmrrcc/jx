package jx.compiler;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordCodecGeneratorTest {
    @TempDir Path directory;

    @Test void sourceLessJarProducesCompilableCodecWithoutInitializingApplicationClasses() throws Exception {
        Path record = write("source/sample/Task.java", """
                package sample;
                public record Task(String id, boolean done, int version) {
                    static { if (System.nanoTime() != 0) throw new AssertionError("must not initialize"); }
                }
                """);
        compile(directory.resolve("contract"), record.toString());
        Path jar = directory.resolve("contract.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("sample/Task.class"));
            out.write(Files.readAllBytes(directory.resolve("contract/sample/Task.class")));
            out.closeEntry();
        }
        Files.delete(record);
        var generated = new RecordCodecGenerator().generate(List.of("sample.Task"), List.of(jar)).getFirst();
        assertEquals(Path.of("sample/TaskJson.java"), generated.relativePath());
        assertTrue(generated.content().contains("json.get(\"version\").intValue()"));
        assertTrue(generated.content().contains("jx.browser.Json.bool(value.done())"));
        Path codec = write("generated/" + generated.relativePath(), generated.content());
        Path json = write("stub/jx/browser/Json.java", """
                package jx.browser;
                public class Json {
                    public Json get(String key) { throw new UnsupportedOperationException(); }
                    public String stringValue() { throw new UnsupportedOperationException(); }
                    public boolean booleanValue() { throw new UnsupportedOperationException(); }
                    public int intValue() { throw new UnsupportedOperationException(); }
                    public static Json object() { throw new UnsupportedOperationException(); }
                    public static Json string(String value) { throw new UnsupportedOperationException(); }
                    public static Json bool(boolean value) { throw new UnsupportedOperationException(); }
                    public static Json number(int value) { throw new UnsupportedOperationException(); }
                    public Json put(String key, Json value) { throw new UnsupportedOperationException(); }
                }
                """);
        compile(directory.resolve("browser"), "-classpath", jar.toString(), codec.toString(), json.toString());
    }

    @Test void rejectsUnsupportedFieldsPrivateShapesAndSerializationAnnotationsWithFieldDiagnostic() throws Exception {
        Path unsupported = write("source/sample/Unknown.java", "package sample; public record Unknown(java.sql.Connection database) {}");
        Path annotation = write("source/sample/Wire.java", """
                package sample;
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                @java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
                public @interface Wire {}
                """);
        Path annotated = write("source/sample/Annotated.java", """
                package sample;
                public record Annotated(String title) {
                    public Annotated(@Wire String title) { this.title = title; }
                }
                """);
        Path generic = write("source/sample/Generic.java", "package sample; public record Generic<T>(T value) {}");
        compile(directory.resolve("contract"), unsupported.toString(), annotation.toString(), annotated.toString(), generic.toString());
        var generator = new RecordCodecGenerator();
        var paths = List.of(directory.resolve("contract"));
        var badType = assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of("sample.Unknown"), paths));
        assertTrue(badType.getMessage().contains("database"), badType.getMessage());
        assertTrue(badType.getMessage().contains("java.sql.Connection"), badType.getMessage());
        var badAnnotation = assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of("sample.Annotated"), paths));
        assertTrue(badAnnotation.getMessage().contains("sample.Wire"), badAnnotation.getMessage());
        assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of("sample.Generic"), paths));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of("sample.Missing"), paths));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of("sample.Unknown", "sample.Unknown"), paths));
    }

    private void compile(Path output, String... args) throws Exception {
        Files.createDirectories(output);
        var options = new java.util.ArrayList<>(List.of("-proc:none", "--release", "25", "-d", output.toString()));
        options.addAll(List.of(args));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, options.toArray(String[]::new)));
    }
    private Path write(String relative, String content) throws Exception {
        Path path = directory.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }
}
