package jx.compiler;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ComponentLibraryClassShapeTest {
    @TempDir Path directory;
    private final JxCompiler compiler = new JxCompiler();
    private static final String PRODUCER = """
            package widgets;
            import jx.*;
            import java.util.function.Supplier;
            @Component @NullableState
            public final class Fancy extends ClassComponent<Fancy.Props, Fancy.Model, Void> {
                public record Props(String id, @OptionalProp String label) {}
                public record Model(Presence<Integer> count, Presence<String> note) {}
                public static final Context<String> contextType = new Context<>("day");
                public static final ClassProps<Props> defaultProps = propsInput().withLabel("fallback").build();
                private final Supplier<String> theme;
                public Fancy(Props props, Supplier<String> theme) { super(props, null); this.theme = theme; }
                public static StatePatch<Model> getDerivedStateFromProps(Props props, Model pending) { return null; }
                public static StatePatch<Model> getDerivedStateFromError(Throwable failure) { return statePatch().withNote("error").build(); }
                protected boolean shouldComponentUpdate(Props next, Model pending, String nextTheme) { return true; }
                protected void componentWillReceiveProps(Props next, String nextTheme) {}
                protected void UNSAFE_componentWillUpdate(Props next, Model pending, String nextTheme) {}
                protected Void getSnapshotBeforeUpdate(Props oldProps, Model oldState) { return null; }
                protected void componentDidUpdate(Props oldProps, Model oldState, Void snapshot) {}
                protected void componentDidCatch(Throwable failure, ComponentErrorInfo info) {}
                public View render() { return <p>{props().label()}: {theme.get()}</p>; }
            }
            """;
    private static final String CONSUMER = """
            package consumer;
            import jx.*;
            import widgets.Fancy;
            @Component public final class App {
                public static final ClassRef<Fancy> REF = new ClassRef<>(value -> {
                    if (value instanceof Fancy fancy) return fancy;
                    throw new IllegalArgumentException();
                });
                public static View render() { return <Fancy id="consumer" ref={REF} key="stable" />; }
            }
            """;

    @Test void sourceLessArchivePreservesTheCompleteAuthoredClassShape() throws Exception {
        Producer producer = producer();
        ComponentMetadata.ClassSpec expected = producer.metadata().classSpec();
        ComponentMetadata imported = ComponentLibrary.read(producer.jar()).orElseThrow().entries().getFirst().component();
        ComponentMetadata.ClassSpec actual = imported.classSpec();
        assertAll(
                () -> assertEquals(expected.kind(), actual.kind()),
                () -> assertEquals(expected.stateType(), actual.stateType()),
                () -> assertEquals(expected.lifecycles(), actual.lifecycles()),
                () -> assertEquals(expected.contextValueType(), actual.contextValueType()),
                () -> assertTrue(expected.derivedProps() && actual.derivedProps()),
                () -> assertTrue(expected.derivedError() && actual.derivedError()),
                () -> assertTrue(expected.contextShouldUpdate() && actual.contextShouldUpdate()),
                () -> assertTrue(expected.defaultProps() && actual.defaultProps(), "Native default declaration must survive the binary index"),
                () -> assertEquals(Set.of(ComponentMetadata.Lifecycle.WILL_RECEIVE_PROPS,
                        ComponentMetadata.Lifecycle.UNSAFE_WILL_UPDATE), actual.contextLifecycles()),
                () -> assertTrue(expected.nullableState() && actual.nullableState(), "Nullable aggregate is independent of sparse fields"),
                () -> assertEquals(List.of("id", "label"), imported.props().stream().map(ComponentMetadata.Prop::name).toList()),
                () -> assertEquals(List.of("count", "note"), actual.stateLayout().stream().map(field -> field.declaration().name()).toList()),
                () -> assertEquals(expected.stateFields().stream().map(ComponentMetadata.Prop::javaType).toList(),
                        actual.stateFields().stream().map(ComponentMetadata.Prop::javaType).toList()),
                () -> assertEquals(expected.stateLayout().stream().map(ComponentMetadata.StateField::presenceValueType).toList(),
                        actual.stateLayout().stream().map(ComponentMetadata.StateField::presenceValueType).toList()),
                () -> assertTrue(actual.stateLayout().stream().allMatch(ComponentMetadata.StateField::sparse)),
                () -> assertTrue(imported.props().stream().filter(field -> field.name().equals("label")).findFirst().orElseThrow().optional()));
        assertEquals(actual.stateFields(), actual.stateLayout().stream().map(ComponentMetadata.StateField::declaration).toList());
        var index = ComponentLibrary.read(producer.jar()).orElseThrow();
        assertArrayEquals(ComponentLibrary.encode(index), ComponentLibrary.encode(
                ComponentLibrary.parse(ComponentLibrary.encode(index), producer.jar())));
    }

    @Test void importedOptionalPropStaysAbsentForNativeDefaultsAndClassRefRemainsChecked() throws Exception {
        Producer producer = producer();
        SourceFile app = source("App.jx", CONSUMER);
        Compilation compiled = compiler.compile(List.of(app), List.of(producer.jar()));
        assertTrue(compiled.successful(), compiled.diagnostics().toString());
        Path probe = directory.resolve("Probe.java");
        Files.writeString(probe, """
                package consumer;
                import jx.*;
                import widgets.Fancy;
                public final class Probe {
                    public static boolean verify() {
                        View view = App.render();
                        if (!(view instanceof View.ClassPropsView<?,?,?> input)) return false;
                        Object reference = input.ref();
                        var defaults = Fancy.JX.defaultProps();
                        return input.component() == Fancy.JX && reference == App.REF
                            && input.key().equals("stable") && input.overrides() == null
                            && input.input().schema() == Fancy.JX.propsSchema()
                            && input.input().entries().size() == 1
                            && input.input().entries().getFirst().field().name().equals("id")
                            && input.input().entries().getFirst().value().equals("consumer")
                            && defaults == Fancy.defaultProps && defaults.entries().size() == 1
                            && defaults.entries().getFirst().field().name().equals("label")
                            && defaults.entries().getFirst().value().equals("fallback")
                            && Fancy.JX.nullableState();
                    }
                }
                """);
        Path classes = directory.resolve("consumer-classes");
        assertEquals(List.of(), compiler.check(compiled, List.of(probe), List.of(core(), producer.jar()), classes));
        // This inspects the real typed view and descriptor; actual React default
        // application is a separate browser consumer, not a simulated JVM renderer.
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL(),
                producer.jar().toUri().toURL(), core().toUri().toURL()}, null)) {
            assertEquals(true, loader.loadClass("consumer.Probe").getMethod("verify").invoke(null));
        }
        String badRef = CONSUMER.replace("public static final ClassRef<Fancy> REF = new ClassRef<>(value -> {\n"
                        + "        if (value instanceof Fancy fancy) return fancy;\n"
                        + "        throw new IllegalArgumentException();\n"
                        + "    });", "public static final Ref<Fancy> REF = new Ref<>(null);");
        assertNotEquals(CONSUMER, badRef, "The negative consumer must actually change the ref type");
        SourceFile invalid = source("App.jx", badRef);
        Compilation invalidCompiled = compiler.compile(List.of(invalid), List.of(producer.jar()));
        assertTrue(invalidCompiled.successful(), invalidCompiled.diagnostics().toString());
        List<Diagnostic> failures = compiler.check(invalidCompiled, List.of(), List.of(core(), producer.jar()), directory.resolve("invalid-classes"));
        assertTrue(failures.stream().anyMatch(value -> value.severity() == Diagnostic.Severity.ERROR
                && value.source().equals(invalid.path()) && value.line() == invalid.line(badRef.indexOf("<Fancy id="))), failures.toString());
    }

    private record Producer(Path jar, ComponentMetadata metadata) {}
    private Producer producer() throws Exception {
        Compilation compiled = compiler.compile(List.of(source("Fancy.jx", PRODUCER)));
        assertTrue(compiled.successful(), compiled.diagnostics().toString());
        Path classes = directory.resolve("producer-classes");
        assertEquals(List.of(), compiler.check(compiled, List.of(), List.of(core()), classes));
        byte[] index = ComponentLibrary.encode(ComponentLibrary.create(compiled.components(), classes));
        Path jar = directory.resolve("widgets.jar");
        try (var archive = new ZipOutputStream(Files.newOutputStream(jar)); var paths = Files.walk(classes)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".class")).sorted().toList()) {
                archive.putNextEntry(new ZipEntry(classes.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, archive); archive.closeEntry();
            }
            archive.putNextEntry(new ZipEntry(ComponentLibrary.RESOURCE));
            archive.write(index); archive.closeEntry();
        }
        try (var archive = new ZipFile(jar.toFile())) {
            assertTrue(archive.stream().allMatch(entry -> entry.getName().endsWith(".class")
                    || entry.getName().equals(ComponentLibrary.RESOURCE)));
        }
        return new Producer(jar, compiled.components().getFirst());
    }
    private Path core() throws Exception {
        return Path.of(jx.View.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
    private SourceFile source(String name, String content) { return new SourceFile(directory.resolve(name), content); }
}
