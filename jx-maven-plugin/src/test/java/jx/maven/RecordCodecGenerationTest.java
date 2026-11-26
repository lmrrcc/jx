package jx.maven;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordCodecGenerationTest {
    @TempDir Path project;

    @Test void outputOwnershipAndStaleClassCleanupDoNotTouchUserSources() throws Exception {
        record("public record Todo(String id) {}");
        var first = generate(List.of("sample.Todo"));
        assertEquals(1, first.written());
        Path codec = project.resolve("generated/sample/TodoJson.java");
        Path json = write("stub/jx/browser/Json.java", """
                package jx.browser;
                public class Json {
                    public Json get(String name) { throw new UnsupportedOperationException(); }
                    public String stringValue() { throw new UnsupportedOperationException(); }
                    public static Json object() { throw new UnsupportedOperationException(); }
                    public static Json string(String value) { throw new UnsupportedOperationException(); }
                    public Json put(String name, Json value) { throw new UnsupportedOperationException(); }
                }
                """);
        Path handwritten = write("source/sample/TodoJson$User.java", "package sample; public class TodoJson$User {}");
        compile(project.resolve("classes"), "-classpath", project.resolve("contract").toString(), codec.toString(), json.toString(), handwritten.toString());
        FileTime old = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(codec, old);
        assertEquals(0, generate(List.of("sample.Todo")).written());
        assertEquals(old, Files.getLastModifiedTime(codec));
        Path keep = write("generated/sample/Handwritten.java", "handwritten");
        assertEquals(1, generate(List.of()).removed());
        assertFalse(Files.exists(codec));
        assertFalse(Files.exists(project.resolve("classes/sample/TodoJson.class")));
        assertTrue(Files.exists(project.resolve("classes/sample/TodoJson$User.class")));
        assertEquals("handwritten", Files.readString(keep));
    }

    @Test void invalidMetadataUnownedAndEditedFilesFailBeforeReplacingSuccessfulOutputs() throws Exception {
        record("public record Todo(String id) {}");
        Path codec = write("generated/sample/TodoJson.java", "user file");
        assertThrows(IOException.class, () -> generate(List.of("sample.Todo")));
        assertEquals("user file", Files.readString(codec));
        Files.delete(codec);
        generate(List.of("sample.Todo"));
        String generated = Files.readString(codec);
        record("public record Todo(java.sql.Connection secret) {}");
        var unsupported = assertThrows(IllegalArgumentException.class, () -> generate(List.of("sample.Todo")));
        assertTrue(unsupported.getMessage().contains("java.sql.Connection"), unsupported.getMessage());
        assertEquals(generated, Files.readString(codec));
        Files.writeString(codec, generated + "// manual edit\n");
        assertThrows(IOException.class, () -> generate(List.of()));
        assertTrue(Files.readString(codec).endsWith("// manual edit\n"));
    }

    @Test void manifestTraversalAndOutputSymlinksAreRejected() throws Exception {
        record("public record Todo(String id) {}");
        Path outside = write("outside.java", "outside");
        Path link = project.resolve("generated/sample/TodoJson.java");
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, outside);
        assertThrows(IOException.class, () -> generate(List.of("sample.Todo")));
        assertEquals("outside", Files.readString(outside));
        Files.delete(link);
        write("metadata/record-codecs.tsv", "jx-record-codecs-v1\n../outside.java\t" + "0".repeat(64) + "\n");
        assertThrows(IOException.class, () -> generate(List.of()));
        assertEquals("outside", Files.readString(outside));
    }

    private RecordCodecGeneration.Result generate(List<String> types) throws Exception {
        return new RecordCodecGeneration().generate(types, List.of(project.resolve("contract")),
                project.resolve("generated"), project.resolve("metadata"), project.resolve("classes"));
    }
    private void record(String source) throws Exception {
        Path record = write("source/sample/Todo.java", "package sample; " + source);
        compile(project.resolve("contract"), record.toString());
    }
    private void compile(Path output, String... args) throws Exception {
        Files.createDirectories(output);
        var options = new java.util.ArrayList<>(List.of("-proc:none", "--release", "25", "-d", output.toString()));
        options.addAll(List.of(args));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, options.toArray(String[]::new)));
    }
    private Path write(String relative, String content) throws Exception {
        Path path = project.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }
}
