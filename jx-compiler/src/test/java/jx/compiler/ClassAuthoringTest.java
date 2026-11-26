package jx.compiler;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClassAuthoringTest {
    @TempDir Path directory;
    private final JxCompiler compiler = new JxCompiler();
    private static final String COUNTER = """
            package demo;
            import jx.*;
            @Component public final class Counter extends ClassComponent<Counter.Props, Counter.Model, String> {
                public record Props(int initial, String label) {}
                public record Model(int count, String note) {}
                public static final StatePatch<Model> EARLY = statePatch().withCount(7).build();
                public Counter(Props props) { super(props, new Model(props.initial(), "ready")); }
                public void increment() { setState((pending, next) -> statePatch().withCount(pending.count() + 1).build()); }
                @Override public View render() {
                    return <button onClick={event -> increment()}>{props().label()}: {state().count()}</button>;
                }
                @Override protected String getSnapshotBeforeUpdate(Props previous, Model state) { return state.note(); }
                @Override protected void componentDidUpdate(Props previous, Model state, String snapshot) {}
            }
            """;
    private static final String PURE = """
            package demo;
            import jx.*;
            @Component public final class PureCounter extends PureComponent<PureCounter.Props, PureCounter.Model, Void> {
                public record Props(String label) {}
                public record Model(int count) {}
                public PureCounter(Props props) { super(props, new Model(0)); }
                @Override public View render() { return <p>{props().label()}: {state().count()}</p>; }
            }
            """;
    private static final String APP = """
            package demo;
            import jx.*;
            @Component public final class App {
                public static View render() {
                    ClassRef<Counter> ref = new ClassRef<>(value -> {
                        if (value instanceof Counter counter) return counter;
                        throw new IllegalArgumentException();
                    });
                    return <main><Counter ref={ref} label="Count" key="stable" initial={3} /><PureCounter label="Pure" /></main>;
                }
            }
            """;

    @Test void compilesActualNativeClassDescriptorsAndRunsTypedPatchConstruction() throws Exception {
        Compilation compilation = compiler.compile(sources(COUNTER, APP));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        Path probe = directory.resolve("Probe.java");
        Files.writeString(probe, """
                package demo;
                public final class Probe {
                    public static boolean verify() {
                        Counter peer = Counter.JX.create(new Counter.Props(4, "four"));
                        var empty = Counter.statePatch();
                        var changed = empty.withCount(5).withNote(null).build();
                        boolean rejected = false;
                        try { empty.withCount(1).withCount(2); } catch (IllegalArgumentException expected) { rejected = true; }
                        return peer.initialState().count() == 4 && peer.props().label().equals("four")
                            && Counter.EARLY.entries().getFirst().value().equals(7)
                            && empty.build().entries().isEmpty() && changed.entries().size() == 2
                            && changed.entries().get(1).value() == null && rejected
                            && PureCounter.JX.pure() && !Counter.JX.pure();
                    }
                }
                """);
        List<Diagnostic> diagnostics = check(compilation, List.of(probe), "consumer");
        assertEquals(List.of(), diagnostics);
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{directory.resolve("consumer").toUri().toURL()}, null)) {
            assertEquals(true, loader.loadClass("demo.Probe").getMethod("verify").invoke(null));
        }
        ComponentMetadata counter = compilation.components().stream().filter(value -> value.simpleName().equals("Counter")).findFirst().orElseThrow();
        assertNotNull(counter.classSpec());
        assertEquals(List.of("count", "note"), counter.classSpec().stateFields().stream().map(ComponentMetadata.Prop::name).toList());
        assertTrue(compilation.generatedSources().stream().filter(value -> value.relativePath().endsWith("Counter.java"))
                .noneMatch(value -> value.javaSource().contains("jx-hooks-v1:")), "Native classes must not receive functional hot signatures");
    }

    @Test void mapsInvalidPropAndClassRefTypesBackToTheAuthoredTag() throws Exception {
        for (String app : List.of(APP.replace("initial={3}", "initial={\"wrong\"}"),
                APP.replace("ClassRef<Counter>", "ClassRef<PureCounter>")
                        .replace("instanceof Counter counter", "instanceof PureCounter counter"))) {
            Compilation compilation = compiler.compile(sources(COUNTER, app));
            assertTrue(compilation.successful(), compilation.diagnostics().toString());
            List<Diagnostic> diagnostics = check(compilation, List.of(), "bad-" + Math.abs(app.hashCode()));
            int tagLine = source("App.jx", app).line(app.indexOf("<Counter ref="));
            assertTrue(diagnostics.stream().anyMatch(value -> value.severity() == Diagnostic.Severity.ERROR
                    && value.source().getFileName().toString().equals("App.jx")
                    && value.line() == tagLine), diagnostics.toString());
        }
    }

    @Test void classSpreadAndOrderedEditorProjectionKeepTheRealRefAndPropTypes() throws Exception {
        String spread = APP.replace("<Counter ref={ref} label=\"Count\" key=\"stable\" initial={3} />",
                "<Counter ref={ref} {...new Counter.Props(3, \"Count\")} key=\"stable\" />");
        Compilation compilation = compiler.compile(sources(COUNTER, spread));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        assertEquals(List.of(), check(compilation, List.of(), "spread"));
        Compilation projection = new Compilation(EditorProjection.project(sources(COUNTER, APP)), List.of(), List.of());
        assertEquals(List.of(), check(projection, List.of(), "editor"));
        String badRef = APP.replace("ref={ref}", "ref={new Ref<Counter>(null)}");
        Compilation invalid = new Compilation(EditorProjection.project(sources(COUNTER, badRef)), List.of(), List.of());
        assertTrue(check(invalid, List.of(), "editor-bad").stream().anyMatch(value ->
                value.severity() == Diagnostic.Severity.ERROR && value.source().getFileName().toString().equals("App.jx")));
    }

    @Test void rejectsUnsupportedSchemasGeneratedNamesAndLifecycleFormsAtSource() {
        for (String source : List.of(
                COUNTER.replace("int count, String note", "byte count, String note"),
                COUNTER.replace("String note", "java.util.List<String> note"),
                COUNTER.replace("int count, String note", "int key, String note"),
                COUNTER.replace("int count, String note", "int count, int Count"),
                COUNTER.replace("public static final StatePatch<Model> EARLY", "public static final StatePatch<Model> JX"),
                COUNTER.replace("@Component public", "@Component @Memo public"),
                COUNTER.replace("public Counter(Props props)", "private Counter(Props props)"))) {
            Compilation result = compiler.compile(List.of(source("Counter.jx", source)));
            assertTrue(result.diagnostics().stream().anyMatch(value -> value.code().equals("JX110")), result.diagnostics().toString());
        }
        for (String method : List.of("public void getChildContext() {}", "public static Model getDerivedStateFromProps(Props p, Model s) { return s; }")) {
            Compilation result = compiler.compile(List.of(source("Counter.jx", COUNTER.replace("public void increment()", method + " public void increment()"))));
            assertTrue(result.diagnostics().stream().anyMatch(value -> value.code().equals("JX111")), result.diagnostics().toString());
        }
    }

    @Test void nativeClassesRejectHooksEvenAtTheTopLevelOfRender() {
        Compilation result = compiler.compile(List.of(source("Counter.jx", COUNTER.replace("return <button", "Hooks.useState(0); return <button"))));
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.code().equals("JX201")
                && value.source().getFileName().toString().equals("Counter.jx")), result.diagnostics().toString());
    }

    @Test void qualifiedBasesAndReferenceFieldsCompileWithoutScalarNameConfusion() throws Exception {
        String source = """
                package demo;
                @jx.Component public final class References extends jx.ClassComponent<References.Props, References.Model, Void> {
                    public record Integer(int value) {}
                    public record Props(byte[] bytes, Integer tag) {}
                    public record Model(Object equals, char[] letters, Integer tag) {}
                    public References(Props props) { super(props, new Model(props.bytes(), new char[]{'a'}, props.tag())); }
                    @Override public jx.View render() { return <span>{state().tag().value()}</span>; }
                }
                """;
        Compilation compilation = compiler.compile(List.of(source("References.jx", source)));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        assertEquals(List.of(), check(compilation, List.of(), "references"));
        String generated = compilation.generatedSources().getFirst().javaSource();
        assertTrue(generated.contains("jx.ClassSchema.<demo.References.Integer>reference"));
        assertTrue(generated.contains("withEquals(java.lang.Object") || generated.contains("withEquals(Object"));
        assertFalse(generated.contains("jx.ClassSchema.INTEGER"), "A nested Integer is a reference, not a boxed number");
    }

    @Test void foreignBaseNamesAndUnsupportedLifecycleSignaturesDoNotGainNativeSemantics() {
        Compilation foreign = compiler.compile(List.of(source("Counter.jx",
                COUNTER.replace("import jx.*;", "import jx.*; import foreign.ClassComponent;"))));
        assertTrue(foreign.components().stream().noneMatch(value -> value.classSpec() != null));
        assertFalse(foreign.successful());
        for (String source : List.of(
                COUNTER.replace("protected void componentDidUpdate(Props previous, Model state, String snapshot)",
                        "protected void componentDidUpdate(Props previous, Model state, Integer snapshot)"),
                COUNTER.replace("@Override protected void componentDidUpdate(Props previous, Model state, String snapshot) {}", ""),
                PURE.replace("@Override public View render()", "protected String shouldComponentUpdate(Props p, Model s) { return \"invalid\"; } @Override public View render()"))) {
            Compilation result = compiler.compile(List.of(source(source.contains("class PureCounter") ? "PureCounter.jx" : "Counter.jx", source)));
            assertTrue(result.diagnostics().stream().anyMatch(value -> value.code().equals("JX111")), result.diagnostics().toString());
        }
    }

    @Test void pureClassesAcceptATypedTwoArgumentPolicyWithRealJavaAttribution() throws Exception {
        String text = PURE.replace("@Override public View render()",
                "@Override protected boolean shouldComponentUpdate(Props p, Model s) { return false; } @Override public View render()");
        SourceFile source = source("PureCounter.jx", text);
        Compilation compiled = compiler.compile(List.of(source));
        assertTrue(compiled.successful(), compiled.diagnostics().toString());
        assertTrue(compiled.components().getFirst().classSpec().lifecycles().contains(ComponentMetadata.Lifecycle.SHOULD_UPDATE));
        assertEquals(List.of(), check(compiled, List.of(), "pure-policy"));
        Compilation projection = new Compilation(EditorProjection.project(List.of(source)), List.of(), List.of());
        assertEquals(List.of(), check(projection, List.of(), "pure-policy-editor"));
    }

    private List<SourceFile> sources(String counter, String app) {
        return List.of(source("Counter.jx", counter), source("PureCounter.jx", PURE), source("App.jx", app));
    }
    private SourceFile source(String name, String value) { return new SourceFile(directory.resolve(name), value); }
    private List<Diagnostic> check(Compilation compilation, List<Path> extra, String output) throws Exception {
        List<Path> java = new ArrayList<>(extra);
        // Compile the real public core API; no signature doubles can hide an invalid generated contract.
        try (var files = Files.walk(Path.of("..", "jx-core", "src", "main", "java"))) {
            java.addAll(files.filter(value -> value.toString().endsWith(".java")).sorted().toList());
        }
        return compiler.check(compilation, java, List.of(), directory.resolve(output));
    }
}
