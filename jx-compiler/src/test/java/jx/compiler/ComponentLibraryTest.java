package jx.compiler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ComponentLibraryTest {
    @TempDir Path directory;
    private final JxCompiler compiler = new JxCompiler();
    private int output;
    private static final String INITIALIZED = "jx.component.library.initialized";
    private static final String PANEL = """
            package widgets;
            import jx.*;
            @Component public final class Panel {
                static {
                    System.setProperty("jx.component.library.initialized", "unexpected");
                    if (System.getProperties() != null) throw new AssertionError("Application class initialized");
                }
                public record Props(String title, int count, View children) {}
                public static View render(Props props) {
                    return <section><h2>{props.title()}</h2>{props.count()}{props.children()}</section>;
                }
            }
            """;
    private static final String COUNTER = """
            package widgets;
            import jx.*;
            @Component public final class Counter extends ClassComponent<Counter.Props, Counter.Model, String> {
                public record Props(int initial) {}
                public record Model(int count, String note) {}
                public Counter(Props props) { super(props, new Model(props.initial(), "ready")); }
                @Override public View render() { return <p>{state().count()}</p>; }
                @Override protected String getSnapshotBeforeUpdate(Props previous, Model state) { return state.note(); }
                @Override protected void componentDidUpdate(Props previous, Model state, String snapshot) {}
            }
            """;
    private static final String APP = """
            package consumer;
            import jx.*;
            import widgets.Panel;
            import widgets.Counter;
            @Component public final class App {
                public static View render() {
                    ClassRef<Counter> ref = new ClassRef<>(value -> {
                        if (value instanceof Counter counter) return counter;
                        throw new IllegalArgumentException();
                    });
                    return <Panel title="Library" count={2}><Counter initial={3} ref={ref} /></Panel>;
                }
            }
            """;

    @Test void sourceLessJarCompilesTypedPropsChildrenAndNativeClassRefsWithoutInitialization() throws Exception {
        assertNull(System.getProperty(INITIALIZED));
        Producer producer = producer();
        Path jar = jar(producer, "widgets.jar", ComponentLibrary.encode(producer.index()));
        try (var archive = new ZipFile(jar.toFile())) {
            assertTrue(archive.stream().allMatch(entry -> entry.getName().endsWith(".class")
                    || entry.getName().equals(ComponentLibrary.RESOURCE)));
            assertNotNull(archive.getEntry("widgets/Counter$Model.class"));
        }
        var loaded = ComponentLibrary.read(jar).orElseThrow();
        assertEquals(List.of("widgets.Counter", "widgets.Panel"), names(loaded));
        Path realJar = jar.toRealPath();
        assertTrue(loaded.entries().stream().allMatch(entry -> entry.component().source().equals(realJar)));
        SourceFile app = source("App.jx", APP);
        Compilation compiled = compiler.compile(List.of(app), List.of(jar));
        assertTrue(compiled.successful(), compiled.diagnostics().toString());
        assertEquals(List.of(), check(compiled, List.of(jar)));
        assertEquals(1, compiled.generatedSources().size(), "Binary dependencies must not become generated sources");
        assertNull(System.getProperty(INITIALIZED), "Indexing and javac must never initialize application classes");
    }

    @Test void importedJavaTypeErrorsAndUnknownPropsStillPointToTheAuthoredTag() throws Exception {
        Producer producer = producer();
        Path jar = jar(producer, "widgets.jar", ComponentLibrary.encode(producer.index()));
        SourceFile app = source("App.jx", APP.replace("count={2}", "count={\"wrong\"}"));
        Compilation compiled = compiler.compile(List.of(app), List.of(jar));
        assertTrue(compiled.successful(), compiled.diagnostics().toString());
        List<Diagnostic> errors = check(compiled, List.of(jar));
        assertTrue(errors.stream().anyMatch(error -> error.severity() == Diagnostic.Severity.ERROR
                && error.source().equals(app.path()) && error.line() == app.line(app.content().indexOf("<Panel"))), errors.toString());
        Compilation unknown = compiler.compile(List.of(source("App.jx", APP.replace("count={2}", "unknown={2}"))), List.of(jar));
        assertFalse(unknown.successful());
        assertTrue(unknown.diagnostics().stream().allMatch(error -> error.source().getFileName().toString().equals("App.jx")));
    }

    @Test void indexIsCanonicalImmutableAndPreservesFunctionalAndClassContracts() throws Exception {
        Producer producer = producer();
        List<ComponentLibrary.Entry> reversed = new ArrayList<>(producer.index().entries());
        Collections.reverse(reversed);
        var copy = new ComponentLibrary.Index(reversed);
        reversed.clear();
        byte[] encoded = ComponentLibrary.encode(copy);
        assertArrayEquals(ComponentLibrary.encode(producer.index()), encoded);
        assertThrows(UnsupportedOperationException.class, () -> copy.entries().clear());
        var parsed = ComponentLibrary.parse(encoded, directory.resolve("artifact.jar"));
        assertArrayEquals(encoded, ComponentLibrary.encode(parsed));
        var panel = component(parsed, "widgets.Panel");
        assertNull(panel.classSpec());
        assertEquals(List.of("title", "count", "children"), panel.props().stream().map(ComponentMetadata.Prop::name).toList());
        var counter = component(parsed, "widgets.Counter");
        assertEquals(ComponentMetadata.ClassKind.COMPONENT, counter.classSpec().kind());
        assertEquals(List.of("count", "note"), counter.classSpec().stateFields().stream().map(ComponentMetadata.Prop::name).toList());
        assertEquals(component(producer.index(), "widgets.Counter").classSpec().lifecycles(), counter.classSpec().lifecycles());
        assertTrue(counter.classSpec().lifecycles().contains(ComponentMetadata.Lifecycle.SNAPSHOT));
        assertThrows(IllegalArgumentException.class, () -> new ComponentLibrary.Index(List.of(copy.entries().getFirst(), copy.entries().getFirst())));
        assertThrows(IllegalArgumentException.class, () -> new ComponentLibrary.Index(Collections.nCopies(ComponentLibrary.MAX_COMPONENTS + 1, copy.entries().getFirst())));
    }

    @Test void binaryGenericPropsRetainJavacInferenceAndNoPropsComponentsKeepTheirDescriptor() throws Exception {
        var compilation = compiler.compile(List.of(source("Rows.jx", """
                package widgets;
                import jx.*;
                import java.util.List;
                import java.util.function.Function;
                @Component public final class Rows {
                    public record Props<T>(List<T> values, Function<T,String> label) {}
                    public static <T> View render(Props<T> props) { return <p>{props.label().apply(props.values().getFirst())}</p>; }
                }
                """), source("Empty.jx", """
                package widgets;
                @jx.Component public final class Empty {
                    public static jx.View render() { return <span>Empty</span>; }
                }
                """)));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        Path classes = directory.resolve("generic-producer");
        assertEquals(List.of(), compiler.check(compilation, List.of(), coreClasspath(), classes));
        var producer = new Producer(classes, ComponentLibrary.create(compilation.components(), classes));
        Path jar = jar(producer, "generic.jar", ComponentLibrary.encode(producer.index()));
        var consumer = compiler.compile(List.of(source("App.jx", """
                package consumer;
                import widgets.Rows;
                import widgets.Empty;
                import java.util.List;
                @jx.Component public class App {
                    public static jx.View render() { return <main><Empty/><Rows values={List.of("one")} label={value -> value.toUpperCase()}/></main>; }
                }
                """)), List.of(jar));
        assertTrue(consumer.successful(), consumer.diagnostics().toString());
        assertEquals(List.of(), check(consumer, List.of(jar)));
        var index = ComponentLibrary.read(jar).orElseThrow();
        assertEquals(1, component(index, "widgets.Rows").propsTypeParameters());
        assertFalse(component(index, "widgets.Empty").hasProps());
    }

    @Test void missingIncompatibleAndDuplicateArtifactsHaveLocatedLibraryDiagnostics() throws Exception {
        Producer producer = producer();
        byte[] encoded = ComponentLibrary.encode(producer.index());
        Path missing = jar(producer, "missing-index.jar", null);
        assertTrue(ComponentLibrary.read(missing).isEmpty());
        assertLibraryProblem(List.of(missing), "Missing component descriptor");
        byte[] future = encoded.clone();
        ByteBuffer.wrap(future).putInt(4, ComponentLibrary.VERSION + 1);
        Path incompatible = jar(producer, "future.jar", future);
        assertLibraryProblem(List.of(incompatible), "Unsupported component descriptor version");
        byte[] prototype = encoded.clone(); ByteBuffer.wrap(prototype).putInt(4, 1);
        assertLibraryProblem(List.of(jar(producer, "prototype.jar", prototype)), "Unsupported component descriptor version 1; expected 2");
        Path first = jar(producer, "first.jar", encoded);
        Path second = jar(producer, "second.jar", encoded);
        assertLibraryProblem(List.of(first, second), "Duplicate component binary");
        Compilation repeatedPath = compiler.compile(List.of(source("App.jx", APP)), List.of(first, first.toAbsolutePath()));
        assertTrue(repeatedPath.successful(), repeatedPath.diagnostics().toString());
    }

    @Test void staleOwnerOrPropsBytesAreRejectedBeforeUseAtTheImportingTag() throws Exception {
        Producer producer = producer();
        byte[] encoded = ComponentLibrary.encode(producer.index());
        for (String name : List.of("Panel.class", "Panel$Props.class")) {
            Path file = producer.classes().resolve("widgets").resolve(name);
            byte[] original = Files.readAllBytes(file);
            byte[] changed = Arrays.copyOf(original, original.length + 1);
            Files.write(file, changed);
            Path jar = jar(producer, "stale-" + name + ".jar", encoded);
            Files.write(file, original);
            IOException failure = assertThrows(IOException.class, () -> ComponentLibrary.read(jar));
            assertTrue(failure.getMessage().contains("Stale component descriptor"), failure.toString());
            assertLibraryProblem(List.of(jar), "Stale component descriptor");
        }
    }

    @Test void sourceDeclarationsOverrideStaleOutputsWhileTheOriginalSourceOnlyApiKeepsWorking() throws Exception {
        Producer producer = producer();
        Path noIndex = jar(producer, "unindexed.jar", null);
        List<SourceFile> sources = List.of(source("Panel.jx", PANEL), source("Counter.jx", COUNTER), source("App.jx", APP));
        Compilation original = compiler.compile(sources);
        assertTrue(original.successful(), original.diagnostics().toString());
        Compilation mixed = compiler.compile(sources, List.of(noIndex));
        assertTrue(mixed.successful(), mixed.diagnostics().toString());
        assertEquals(3, mixed.components().size());
        assertEquals(List.of(), check(mixed, List.of(noIndex)));
        assertFalse(compiler.compile(List.of(source("App.jx", APP))).successful(), "The old overload must not discover ambient binary components");
    }

    @Test void metadataCannotForgeRecordOrderOrReplaceThePublicFinalTypedDescriptor() throws Exception {
        Producer producer = producer();
        var entry = producer.index().entries().stream().filter(value -> value.component().qualifiedName().equals("widgets.Panel")).findFirst().orElseThrow();
        ComponentMetadata panel = entry.component();
        List<ComponentMetadata.Prop> swapped = new ArrayList<>(panel.props());
        Collections.swap(swapped, 0, 1);
        var forged = new ComponentMetadata(panel.qualifiedName(), panel.simpleName(), panel.packageName(), panel.imports(),
                panel.hasProps(), swapped, panel.source(), panel.declaration(), panel.descriptorOffset(), panel.memo(), panel.propsTypeParameters());
        var index = new ComponentLibrary.Index(List.of(new ComponentLibrary.Entry(forged, entry.ownerSha256(), entry.propsSha256())));
        Path jar = jar(producer, "forged-order.jar", ComponentLibrary.encode(index));
        assertTrue(assertThrows(IOException.class, () -> ComponentLibrary.read(jar)).getMessage().contains("Props order"));
        Path raw = directory.resolve("Panel.java");
        for (String descriptor : List.of("public static jx.ComponentType<Props> JX;", "public static final jx.ComponentType<String> JX = null;")) {
            Files.writeString(raw, "package widgets; public final class Panel { public record Props(String title, int count, jx.View children) {} " + descriptor + " }");
            Path classes = directory.resolve("forged-classes-" + output++);
            assertEquals(List.of(), compiler.check(new Compilation(List.of(), List.of(), List.of()), List.of(raw), coreClasspath(), classes));
            assertThrows(IOException.class, () -> ComponentLibrary.create(List.of(panel), classes));
        }
    }

    @Test void binaryDecoderRejectsOversizeTruncationInvalidVersionCountsAndTrailingBytes() throws Exception {
        byte[] empty = ComponentLibrary.encode(new ComponentLibrary.Index(List.of()));
        Path artifact = directory.resolve("invalid.jar");
        List<byte[]> invalid = new ArrayList<>();
        invalid.add(new byte[ComponentLibrary.MAX_BYTES + 1]);
        invalid.add(Arrays.copyOf(empty, 3));
        invalid.add(Arrays.copyOf(empty, empty.length - 1));
        invalid.add(Arrays.copyOf(empty, empty.length + 1));
        for (int[] change : List.of(new int[]{0, 0}, new int[]{4, ComponentLibrary.VERSION + 1}, new int[]{8, -1}, new int[]{8, ComponentLibrary.MAX_COMPONENTS + 1})) {
            byte[] bytes = empty.clone();
            ByteBuffer.wrap(bytes).putInt(change[0], change[1]);
            invalid.add(bytes);
        }
        for (byte[] bytes : invalid) assertThrows(IOException.class, () -> ComponentLibrary.parse(bytes, artifact));
        assertEquals(List.of(), ComponentLibrary.parse(empty, artifact).entries());
    }

    private record Producer(Path classes, ComponentLibrary.Index index) {}
    private Producer producer() throws Exception {
        Compilation compiled = compiler.compile(List.of(source("Panel.jx", PANEL), source("Counter.jx", COUNTER)));
        assertTrue(compiled.successful(), compiled.diagnostics().toString());
        Path classes = directory.resolve("producer-" + output++);
        assertEquals(List.of(), compiler.check(compiled, List.of(), coreClasspath(), classes));
        return new Producer(classes, ComponentLibrary.create(compiled.components(), classes));
    }
    private List<Path> coreClasspath() throws Exception {
        return List.of(Path.of(jx.View.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
    }
    private List<Diagnostic> check(Compilation compiled, List<Path> dependencies) throws Exception {
        List<Path> classpath = new ArrayList<>(coreClasspath());
        classpath.addAll(dependencies);
        return compiler.check(compiled, List.of(), classpath, directory.resolve("consumer-" + output++));
    }
    private SourceFile source(String name, String content) { return new SourceFile(directory.resolve(name), content); }
    private static List<String> names(ComponentLibrary.Index index) {
        return index.entries().stream().map(entry -> entry.component().qualifiedName()).toList();
    }
    private static ComponentMetadata component(ComponentLibrary.Index index, String name) {
        return index.entries().stream().map(ComponentLibrary.Entry::component).filter(value -> value.qualifiedName().equals(name)).findFirst().orElseThrow();
    }
    private Path jar(Producer producer, String name, byte[] index) throws IOException {
        Path jar = directory.resolve(name);
        try (var archive = new ZipOutputStream(Files.newOutputStream(jar)); var paths = Files.walk(producer.classes())) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".class")).sorted().toList()) {
                archive.putNextEntry(new ZipEntry(producer.classes().relativize(path).toString().replace('\\', '/')));
                Files.copy(path, archive); archive.closeEntry();
            }
            if (index != null) {
                archive.putNextEntry(new ZipEntry(ComponentLibrary.RESOURCE));
                archive.write(index); archive.closeEntry();
            }
        }
        return jar;
    }
    private void assertLibraryProblem(List<Path> classpath, String message) throws IOException {
        SourceFile app = source("App.jx", APP);
        Compilation compiled = compiler.compile(List.of(app), classpath);
        assertFalse(compiled.successful());
        assertTrue(compiled.diagnostics().stream().anyMatch(error -> error.code().equals("JX120")
                && error.source().equals(app.path()) && error.line() == app.line(APP.indexOf("<Panel"))
                && error.message().contains(message)), compiled.diagnostics().toString());
    }
}
