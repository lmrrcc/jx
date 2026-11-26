package jx.maven;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import jx.compiler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LibraryGenerationTest {
    @TempDir Path root;
    private final JxCompiler compiler = new JxCompiler();
    private Path core() throws Exception { return Path.of(jx.Component.class.getProtectionDomain().getCodeSource().getLocation().toURI()); }
    private record Producer(Path classes, Path metadata, Compilation compilation) {}
    private Producer producer() throws Exception {
        Path source = root.resolve("producer/Widget.jx");
        var compilation = compiler.compile(List.of(new SourceFile(source, """
                package library;
                @jx.Component public class Widget {
                    public record Props(String label, @jx.OptionalProp int initial) {}
                    public static jx.View render(Props props) { return null; }
                }
                """)));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        Path classes = root.resolve("producer/classes"), metadata = root.resolve("producer/metadata");
        assertEquals(List.of(), compiler.check(compilation, List.of(), List.of(core()), classes));
        GenerationService.library(compilation.components(), classes, metadata);
        return new Producer(classes, metadata, compilation);
    }
    private Path jar(Producer producer, byte[] descriptor, String name) throws Exception {
        Path jar = root.resolve(name + ".jar");
        try (var archive = new JarOutputStream(Files.newOutputStream(jar)); var paths = Files.walk(producer.classes())) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                String entry = producer.classes().relativize(path).toString().replace('\\', '/');
                if (entry.equals(ComponentLibrary.RESOURCE)) continue;
                archive.putNextEntry(new JarEntry(entry)); archive.write(Files.readAllBytes(path)); archive.closeEntry();
            }
            if (descriptor != null) {
                archive.putNextEntry(new JarEntry(ComponentLibrary.RESOURCE)); archive.write(descriptor); archive.closeEntry();
            }
        }
        return jar;
    }
    private Path consumer() throws Exception {
        Path source = root.resolve("consumer/src/App.jx"); Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package consumer;
                import library.Widget;
                @jx.Component public class App {
                    public static jx.View render() { return <Widget label="from JAR" />; }
                }
                """);
        return source;
    }
    private GenerationService.Result generate(Path dependency) throws Exception {
        return new GenerationService().generate(root.resolve("consumer/src"), List.of(), root.resolve("consumer/generated"),
                root.resolve("consumer/metadata"), root.resolve("consumer/classes"), List.of(dependency, core()));
    }

    @Test void sourceLessLibraryGeneratesOnlyConsumerAndFailuresPreserveLastGoodOutput() throws Exception {
        Producer producer = producer(); Path source = consumer();
        byte[] descriptor = Files.readAllBytes(producer.classes().resolve(ComponentLibrary.RESOURCE));
        var result = generate(jar(producer, descriptor, "valid"));
        assertEquals(1, result.written());
        Path output = root.resolve("consumer/generated/consumer/App.java"); String before = Files.readString(output);
        assertTrue(before.contains("new Widget.Props(\"from JAR\", 0)"));
        assertFalse(Files.exists(root.resolve("consumer/generated/library/Widget.java")));
        assertEquals(List.of(), compiler.check(result.compilation(), List.of(), List.of(core(), root.resolve("valid.jar")), root.resolve("checked")));
        byte[] future = descriptor.clone(); ByteBuffer.wrap(future).putInt(4, ComponentLibrary.VERSION + 1);
        for (Path invalid : List.of(jar(producer, null, "missing"), jar(producer, future, "future"))) {
            var failure = assertThrows(GenerationService.InvalidSources.class, () -> generate(invalid));
            var diagnostic = failure.diagnostics.stream().filter(value -> value.code().equals("JX120")).findFirst().orElseThrow();
            assertEquals(source, diagnostic.source());
            assertEquals(Files.readString(source).indexOf("<Widget") + 1, diagnostic.span().start());
            assertEquals(before, Files.readString(output));
        }
    }

    @Test void libraryOutputIsStableAndRemovingDeclarationsDeletesOnlyTheOwnedIndex() throws Exception {
        Producer producer = producer();
        Path index = producer.classes().resolve(ComponentLibrary.RESOURCE);
        FileTime time = FileTime.fromMillis(1_700_000_000_000L); Files.setLastModifiedTime(index, time);
        GenerationService.library(producer.compilation().components(), producer.classes(), producer.metadata());
        assertEquals(time, Files.getLastModifiedTime(index));
        Path other = producer.classes().resolve("META-INF/other.txt"); Files.writeString(other, "handwritten");
        GenerationService.library(List.of(), producer.classes(), producer.metadata());
        assertFalse(Files.exists(index)); assertEquals("handwritten", Files.readString(other));
        Files.writeString(index, "unowned");
        assertThrows(java.io.IOException.class, () -> GenerationService.library(producer.compilation().components(), producer.classes(), producer.metadata()));
        assertEquals("unowned", Files.readString(index));
        assertThrows(java.io.IOException.class, () -> GenerationService.library(List.of(), producer.classes(), producer.metadata()));
        Files.writeString(producer.metadata().resolve("library-owned.txt"), "x".repeat(1024));
        var invalidMarker = assertThrows(java.io.IOException.class,
                () -> GenerationService.library(producer.compilation().components(), producer.classes(), producer.metadata()));
        assertEquals("Invalid component library ownership marker", invalidMarker.getMessage());
        assertEquals("unowned", Files.readString(index));
    }
}
