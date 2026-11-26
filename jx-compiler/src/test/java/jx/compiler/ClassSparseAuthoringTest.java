package jx.compiler;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClassSparseAuthoringTest {
    @TempDir Path directory;
    private final JxCompiler compiler = new JxCompiler();
    private static final String PANEL = """
            package demo;
            import jx.*;
            @Component @NullableState
            public final class Panel extends ClassComponent<Panel.Props, Panel.Model, Void> {
                public record Props(String required) {}
                public record Model(Presence<Integer> count, Presence<String> note) {}
                public Panel(Props props) { super(props, null); }
                public void change() { setState(statePatch().withCount(7).undefinedNote().build()); }
                protected Void getSnapshotBeforeUpdate(Props oldProps, Model oldState) { return null; }
                protected void componentDidUpdate(Props oldProps, Model oldState, Void snapshot) {}
                public View render() { return <p>{state() == null ? "null" : state().note().toString()}</p>; }
            }
            """;

    @Test void compilesPresenceSchemasAndTypedPartialUpdatesAgainstTheRealCore() throws Exception {
        for (boolean pure : List.of(false, true)) {
            String text = pure ? PANEL.replace("extends ClassComponent", "extends PureComponent") : PANEL;
            SourceFile source = source(text);
            Compilation compilation = compiler.compile(List.of(source));
            assertTrue(compilation.successful(), compilation.diagnostics().toString());
            var spec = compilation.components().getFirst().classSpec();
            assertTrue(spec.nullableState());
            assertEquals(List.of("Integer", "String"), spec.stateLayout().stream().map(ComponentMetadata.StateField::valueType).toList());
            assertTrue(spec.stateLayout().stream().allMatch(ComponentMetadata.StateField::sparse));
            assertTrue(spec.stateFields().stream().noneMatch(ComponentMetadata.Prop::optional));
            Path probe = directory.resolve("Probe.java");
            Files.writeString(probe, """
                    package demo;
                    import jx.*;
                    public final class Probe {
                        public static boolean verify() {
                            Panel peer = Panel.JX.create(new Panel.Props("required"));
                            Panel.Model absent = Panel.JX.stateSchema().readPresence(field -> Presence.absent());
                            Panel.Model undefined = Panel.JX.stateSchema().readPresence(field -> Presence.undefined());
                            Panel.Model definedNull = Panel.JX.stateSchema().readPresence(field -> Presence.present(null));
                            var empty = Panel.statePatch();
                            var first = empty.withCount(7);
                            var next = first.undefinedNote().build();
                            boolean duplicate = false;
                            try { first.undefinedCount(); } catch (IllegalArgumentException expected) { duplicate = true; }
                            boolean required = false;
                            try { Panel.JX.propsSchema().readPresence(field -> Presence.absent()); }
                            catch (IllegalArgumentException expected) { required = true; }
                            StateCodec<Panel.Model> codec = new StateCodec<>() {
                                public String schema() { return "sparse:1"; }
                                public String encode(Panel.Model state) { throw new AssertionError("admission must not encode"); }
                                public Panel.Model decode(String text) { throw new AssertionError("admission must not decode"); }
                            };
                            boolean checkpointContract;
                            if (Panel.JX.pure()) {
                                try {
                                    Panel.JX.withStateCheckpoint("sparse", codec);
                                    checkpointContract = false;
                                } catch (IllegalArgumentException unsupportedPure) {
                                    checkpointContract = unsupportedPure.getMessage().equals("Pure class checkpoints are not qualified")
                                        && Panel.JX.checkpoint() == null;
                                }
                            } else {
                                var checkpoint = Panel.JX.withStateCheckpoint("sparse", codec);
                                checkpoint.validateState(null);
                                checkpoint.validateState(absent);
                                checkpoint.validateState(undefined);
                                checkpoint.validateState(definedNull);
                                checkpointContract = checkpoint != Panel.JX && Panel.JX.checkpoint() == null
                                    && checkpoint.nullableState() && checkpoint.stateSchema() == Panel.JX.stateSchema()
                                    && checkpoint.propsSchema() == Panel.JX.propsSchema() && checkpoint.checkpoint().codec() == codec;
                            }
                            return peer.state() == null && absent.count() instanceof Presence.Absent<Integer>
                                && undefined.note() instanceof Presence.Undefined<String>
                                && definedNull.note() instanceof Presence.Present<String> present && present.value() == null
                                && empty.build().entries().isEmpty() && first.build().entries().size() == 1
                                && next.entries().getFirst().value().equals(7) && next.entries().getLast().undefined()
                                && !Panel.statePatch().withNote(null).build().entries().getFirst().undefined()
                                && duplicate && required && checkpointContract;
                        }
                    }
                    """);
            String output = "schema-" + pure;
            assertEquals(List.of(), check(compilation, List.of(probe), output));
            try (var loader = new URLClassLoader(new java.net.URL[]{directory.resolve(output).toUri().toURL()}, null)) {
                assertEquals(true, loader.loadClass("demo.Probe").getMethod("verify").invoke(null));
            }
            Compilation projection = new Compilation(EditorProjection.project(List.of(source)), List.of(), List.of());
            assertEquals(List.of(), check(projection, List.of(), output + "-editor"));
            String expression = "statePatch().withCount(7).undefinedNote().build()";
            var generated = projection.generatedSources().getFirst();
            int original = text.indexOf(expression), java = generated.sourceMap().generatedOffset(original).orElseThrow();
            assertEquals(expression, generated.javaSource().substring(java, java + expression.length()));
            assertEquals(new SourceSpan(original, original + expression.length()),
                    generated.sourceMap().editableSourceSpan(new SourceSpan(java, java + expression.length())).orElseThrow());
        }
    }

    @Test void explicitMarkerAndSparseFieldsDoNotRelaxRequiredFieldsOrActivateForeignAnnotations() throws Exception {
        String strict = PANEL.replace("@Component @NullableState", "@Component")
                .replace("public record Model(Presence<Integer> count, Presence<String> note)",
                        "public record Model(int count, Presence<String> note)")
                .replace("super(props, null)", "super(props, new Model(1, Presence.absent()))");
        Compilation result = compiler.compile(List.of(source(strict)));
        assertTrue(result.successful(), result.diagnostics().toString());
        assertFalse(result.components().getFirst().classSpec().nullableState());
        assertFalse(result.components().getFirst().classSpec().stateLayout().getFirst().sparse());
        assertTrue(result.components().getFirst().classSpec().stateLayout().getLast().sparse());
        Path probe = directory.resolve("StrictProbe.java");
        Files.writeString(probe, """
                package demo;
                import jx.*;
                public final class StrictProbe {
                    public static boolean verify() {
                        try { Panel.JX.stateSchema().readPresence(field -> Presence.absent()); return false; }
                        catch (IllegalArgumentException expected) {
                            try { Panel.JX.validateState(null); return false; }
                            catch (NullPointerException rejected) {
                                StateCodec<Panel.Model> codec = new StateCodec<>() {
                                    public String schema() { return "strict-sparse:1"; }
                                    public String encode(Panel.Model value) { throw new AssertionError("admission must not encode"); }
                                    public Panel.Model decode(String value) { throw new AssertionError("admission must not decode"); }
                                };
                                var checkpoint = Panel.JX.withStateCheckpoint("strict-sparse", codec);
                                try { checkpoint.validateState(null); return false; }
                                catch (NullPointerException strictState) {
                                    Panel.Model state = checkpoint.create(new Panel.Props("required")).state();
                                    checkpoint.validateState(state);
                                    return state.count() == 1 && state.note() instanceof Presence.Absent<String>
                                        && !checkpoint.nullableState() && checkpoint.stateSchema() == Panel.JX.stateSchema()
                                        && checkpoint.propsSchema() == Panel.JX.propsSchema() && checkpoint.checkpoint().codec() == codec;
                                }
                            }
                        }
                    }
                }
                """);
        assertEquals(List.of(), check(result, List.of(probe), "strict"));
        try (var loader = new URLClassLoader(new java.net.URL[]{directory.resolve("strict").toUri().toURL()}, null)) {
            assertEquals(true, loader.loadClass("demo.StrictProbe").getMethod("verify").invoke(null));
        }
        for (String foreign : List.of(PANEL.replace("import jx.*;", "import jx.*; import foreign.NullableState;"),
                PANEL + "\n@interface NullableState {}")) {
            Compilation metadata = compiler.compile(List.of(source(foreign)));
            assertTrue(metadata.successful(), metadata.diagnostics().toString());
            assertFalse(metadata.components().getFirst().classSpec().nullableState());
        }
    }

    @Test void fullyQualifiedPresenceSupportsReferenceAndArrayValuesInBothProjections() throws Exception {
        String text = """
                package demo;
                @jx.Component @jx.NullableState
                public final class Panel extends jx.PureComponent<Panel.Props, Panel.Model, Void> {
                    public record Props(String required) {}
                    public record Item(String label) {}
                    public record Model(jx.Presence<int[]> values, jx.Presence<Item> item) {}
                    public Panel(Props props) { super(props, null); }
                    public void change() {
                        setState(statePatch().withValues(new int[]{1, 2}).withItem(new Item("ready")).build());
                        setState(statePatch().undefinedValues().withItem(null).build());
                    }
                    public jx.View render() { return <p>{state() == null ? "null" : state().item().toString()}</p>; }
                }
                """;
        SourceFile source = source(text);
        for (boolean editor : List.of(false, true)) {
            Compilation result = editor ? new Compilation(EditorProjection.project(List.of(source)), List.of(), List.of())
                    : compiler.compile(List.of(source));
            assertTrue(result.successful(), result.diagnostics().toString());
            assertEquals(List.of(), check(result, List.of(), "references-" + editor));
        }
    }

    @Test void rejectsInvalidPlacementUnsupportedPresenceAndGeneratedCollisions() {
        for (String invalid : List.of(
                PANEL.replace("@Component @NullableState", "@NullableState"),
                "import jx.*; @NullableState @Component public class Panel { public static View render() { return <p/>; } }",
                PANEL.replace("public record Props", "@NullableState public record Props"),
                PANEL.replace("Presence<Integer>", "Presence<?>"),
                PANEL.replace("Presence<Integer>", "Presence<Presence<Integer>>"),
                PANEL.replace("Presence<Integer>", "Presence<java.util.List<Integer>>"),
                PANEL.replace("Presence<Integer>", "Presence"),
                PANEL.replace("Presence<String> note", "Presence<String> Count"),
                PANEL.replace("String required", "Presence<String> required"),
                PANEL.replace("import jx.*;", "import jx.*; import foreign.Presence;"))) {
            SourceFile source = source(invalid);
            Compilation compilation = compiler.compile(List.of(source));
            assertTrue(compilation.diagnostics().stream().anyMatch(value -> value.source().equals(source.path())
                    && (value.code().equals("JX110") || value.code().equals("JX111"))), compilation.diagnostics().toString());
        }
    }

    @Test void wrongTypedBuilderArgumentsRemainMappedJavaErrors() throws Exception {
        String text = PANEL.replace("withCount(7)", "withCount(\"wrong\")");
        SourceFile source = source(text);
        for (boolean editor : List.of(false, true)) {
            Compilation compiled = editor ? new Compilation(EditorProjection.project(List.of(source)), List.of(), List.of())
                    : compiler.compile(List.of(source));
            assertTrue(compiled.successful(), compiled.diagnostics().toString());
            var errors = check(compiled, List.of(), "wrong-" + editor);
            assertTrue(errors.stream().anyMatch(value -> value.severity() == Diagnostic.Severity.ERROR
                    && value.source().equals(source.path()) && value.line() == source.line(text.indexOf("withCount(\"wrong\")"))), errors.toString());
        }
    }

    private SourceFile source(String text) { return new SourceFile(directory.resolve("Panel.jx"), text); }
    private List<Diagnostic> check(Compilation compiled, List<Path> extra, String output) throws Exception {
        List<Path> java = new ArrayList<>(extra);
        try (var files = Files.walk(Path.of("..", "jx-core", "src", "main", "java"))) {
            java.addAll(files.filter(value -> value.toString().endsWith(".java")).sorted().toList());
        }
        return compiler.check(compiled, java, List.of(), directory.resolve(output));
    }
}
