package jx.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GenerationServiceTest {
    @TempDir Path project;
    private final GenerationService service = new GenerationService();

    @Test void repeatedBuildPreservesUnchangedTimestampsAndEditRemovesNestedClasses() throws Exception {
        Path source = write("src/main/jx/Thing.jx", """
                package demo;
                public class Thing { public static class Removed {} }
                class Auxiliary {}
                """);
        var initial = generate();
        assertEquals(1, initial.written());
        Path generated = project.resolve("target/generated-sources/jx/demo/Thing.java");
        compile(generated);
        assertTrue(Files.exists(project.resolve("target/classes/demo/Thing$Removed.class")));
        assertTrue(Files.exists(project.resolve("target/classes/demo/Auxiliary.class")));
        FileTime old = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(generated, old);
        assertEquals(0, generate().written());
        assertEquals(old, Files.getLastModifiedTime(generated));

        Files.writeString(source, "package demo; public class Thing { public int answer() { return 42; } }");
        assertEquals(1, generate().written());
        assertFalse(Files.exists(project.resolve("target/classes/demo/Thing$Removed.class")));
        assertFalse(Files.exists(project.resolve("target/classes/demo/Auxiliary.class")));
        compile(generated);
        assertTrue(Files.exists(project.resolve("target/classes/demo/Thing.class")));
    }

    @Test void sourceRenameAndDeletionRemoveOnlyOwnedOutputsAndClasses() throws Exception {
        Path source = write("src/main/jx/Thing.jx", "package demo; public class Thing { public static class Nested {} }");
        generate();
        compile(project.resolve("target/generated-sources/jx/demo/Thing.java"));
        Path similarlyNamed = write("src/main/java/demo/Thing$Handwritten.java", "package demo; public class Thing$Handwritten {}");
        compile(similarlyNamed);
        Path keepJava = write("target/generated-sources/jx/demo/Keep.java", "package demo; class Keep {}");
        Path keepClass = write("target/classes/demo/Keep.class", "unrelated class bytes");
        Files.delete(source);
        Path replacement = write("src/main/jx/Renamed.jx", "package demo; public class Renamed {}");
        var rename = generate();
        assertEquals(1, rename.deleted());
        assertEquals(1, rename.written());
        assertFalse(Files.exists(project.resolve("target/classes/demo/Thing.class")));
        assertFalse(Files.exists(project.resolve("target/classes/demo/Thing$Nested.class")));
        assertTrue(Files.exists(project.resolve("target/classes/demo/Thing$Handwritten.class")));
        assertFalse(Files.exists(project.resolve("target/jx/main/demo/Thing.jxmap")));
        assertTrue(Files.exists(keepJava));
        assertTrue(Files.exists(keepClass));
        Files.delete(replacement);
        assertEquals(1, generate().deleted());
        assertTrue(Files.exists(keepJava));
    }

    @Test void invalidInputDoesNotReplaceLastSuccessfulGeneratedSources() throws Exception {
        Path source = write("src/main/jx/Thing.jx", "public class Thing { String value() { return \"ok\"; } }");
        generate();
        Path generated = project.resolve("target/generated-sources/jx/Thing.java");
        String before = Files.readString(generated);
        Files.writeString(source, "public class Thing { Object value() { return <div></span>; } }");
        var error = assertThrows(GenerationService.InvalidSources.class, this::generate);
        assertTrue(error.diagnostics.getFirst().source().toString().endsWith("Thing.jx"));
        assertEquals(before, Files.readString(generated));
    }

    @Test void testSourcesResolveMainComponentsWithoutCopyingThem() throws Exception {
        write("src/main/jx/Counter.jx", """
                package demo;
                @jx.JxComponent public class Counter {
                    public record Props(int initial) {}
                    public static jx.View render(Props props) { return <span>{props.initial()}</span>; }
                }
                """);
        write("src/test/jx/Fixture.jx", """
                package demo;
                @jx.JxComponent public class Fixture {
                    public static jx.View render() { return <Counter initial={2}/>; }
                }
                """);
        var result = service.generate(project.resolve("src/test/jx"), List.of(project.resolve("src/main/jx")),
                project.resolve("target/generated-test-sources/jx"), project.resolve("target/jx/test"),
                project.resolve("target/test-classes"));
        assertEquals(1, result.written());
        assertTrue(Files.exists(project.resolve("target/generated-test-sources/jx/demo/Fixture.java")));
        assertFalse(Files.exists(project.resolve("target/generated-test-sources/jx/demo/Counter.java")));
    }

    @Test void refusesUnownedOutputManifestTraversalAndSymlinkTargets() throws Exception {
        write("src/main/jx/Thing.jx", "public class Thing {}");
        Path existing = write("target/generated-sources/jx/Thing.java", "handwritten");
        assertThrows(java.io.IOException.class, this::generate);
        assertEquals("handwritten", Files.readString(existing));
        Files.delete(existing);
        Path manifest = write("target/jx/main/outputs.tsv", "jx-outputs-v1\n../outside.java\tOutside\n");
        assertThrows(java.io.IOException.class, this::generate);
        Files.delete(manifest);
        Path outside = write("outside.java", "unrelated");
        Files.createSymbolicLink(existing, outside);
        assertThrows(java.io.IOException.class, this::generate);
        assertEquals("unrelated", Files.readString(outside));
    }

    private GenerationService.Result generate() throws Exception {
        return service.generate(project.resolve("src/main/jx"), List.of(), project.resolve("target/generated-sources/jx"),
                project.resolve("target/jx/main"), project.resolve("target/classes"));
    }
    private Path write(String relative, String content) throws Exception {
        Path path = project.resolve(relative); Files.createDirectories(path.getParent()); Files.writeString(path, content); return path;
    }
    private void compile(Path path) throws Exception {
        Files.createDirectories(project.resolve("target/classes"));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "25", "-d",
                project.resolve("target/classes").toString(), path.toString()));
    }
}
