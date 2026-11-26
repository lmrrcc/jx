package jx.compiler;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClassLegacyAuthoringTest {
    @TempDir Path directory;
    private final JxCompiler compiler = new JxCompiler();
    private static final String BASE = """
            package demo;
            import jx.*;
            import java.util.function.Supplier;
            @Component public final class Panel extends ClassComponent<Panel.Props, Panel.Model, Void> {
                public record Props(int revision, String label) {}
                public record Model(int count) {}
                public final java.util.List<String> calls = new java.util.ArrayList<>();
                public Panel(Props props) { super(props, new Model(1)); }
                protected void componentWillMount() { calls.add("mount"); }
                protected void UNSAFE_componentWillMount() { calls.add("unsafe-mount"); }
                protected void componentWillReceiveProps(Props next) { calls.add("receive"); }
                protected void UNSAFE_componentWillReceiveProps(Props next) { calls.add("unsafe-receive"); }
                protected void componentWillUpdate(Props next, Model pending) { calls.add("update"); }
                protected void UNSAFE_componentWillUpdate(Props next, Model pending) { calls.add("unsafe-update"); }
                public View render() { return <p>{state().count()}</p>; }
            }
            """;
    private static final String POLICIES = """
                protected void componentWillReceiveProps(Props next, String nextContext) {
                    calls.add("typed-receive:" + props().revision() + ":" + next.revision() + ":" + nextContext);
                }
                protected void UNSAFE_componentWillReceiveProps(Props next, String nextContext) { calls.add("typed-unsafe-receive"); }
                protected void componentWillUpdate(Props next, Model pending, String nextContext) { calls.add("typed-update"); }
                protected void UNSAFE_componentWillUpdate(Props next, Model pending, String nextContext) {
                    calls.add("typed-unsafe-update:" + state().count() + ":" + pending.count() + ":" + nextContext.length());
                }
            """;
    private static String contextual() {
        return BASE.replace("public Panel(Props props)", "public Panel(Props props, Supplier<String> theme)")
                .replace("public View render()", """
                        public static final Context<String> contextType = new Context<>("day");
                        public static final ClassProps<Props> defaultProps = propsInput().withLabel("default").build();
                        """ + POLICIES + "public View render()");
    }

    @Test void generatedDescriptorsCompileAndDispatchBothVariantsWithDefaultsAndTypedPrecedence() throws Exception {
        for (boolean context : List.of(false, true)) for (boolean pure : List.of(false, true)) {
            String text = context ? contextual() : BASE;
            if (pure) text = text.replace("extends ClassComponent", "extends PureComponent");
            SourceFile source = source(text);
            Compilation compiled = compiler.compile(List.of(source));
            assertTrue(compiled.successful(), compiled.diagnostics().toString());
            var metadata = compiled.components().getFirst().classSpec();
            assertEquals(6, metadata.lifecycles().size());
            assertEquals(context ? 4 : 0, metadata.contextLifecycles().size());
            assertEquals(context, metadata.defaultProps());
            String expected = context
                    ? "mount,unsafe-mount,typed-receive:1:2:day,typed-unsafe-receive,typed-update,typed-unsafe-update:1:3:3"
                    : "mount,unsafe-mount,receive,unsafe-receive,update,unsafe-update";
            Path probe = directory.resolve("Probe.java");
            Files.writeString(probe, """
                    package demo;
                    import jx.*;
                    import static jx.ClassComponentType.LegacyVariant.*;
                    public final class Probe {
                        public static boolean verify() {
                            var access = new ClassComponentType.ContextAccess() {
                                public <T> java.util.function.Supplier<T> current(Context<T> context) { return context::defaultValue; }
                            };
                            Panel peer = Panel.JX.create(new Panel.Props(1, "given"), access);
                            peer.beforeMount(DEPRECATED); peer.beforeMount(UNSAFE);
                            Panel.JX.beforeReceiveProps(peer, DEPRECATED, new Panel.Props(2, "next"), access);
                            Panel.JX.beforeReceiveProps(peer, UNSAFE, new Panel.Props(2, "next"), access);
                            Panel.JX.beforeUpdate(peer, DEPRECATED, new Panel.Props(2, "next"), new Panel.Model(3), access);
                            Panel.JX.beforeUpdate(peer, UNSAFE, new Panel.Props(2, "next"), new Panel.Model(3), access);
                            return String.join(",", peer.calls).equals("EXPECTED") COMPOSITION;
                        }
                    }
                    """.replace("EXPECTED", expected).replace("COMPOSITION", context ? """
                            && Panel.JX.defaultProps() == Panel.defaultProps
                            && Panel.JX.withDefaultProps(Panel.defaultProps)
                                .withContextWillUpdate(Panel.contextType, UNSAFE, (p, props, state, theme) -> {})
                                .defaultProps() == Panel.defaultProps
                            && Panel.JX.withContextWillUpdate(Panel.contextType, UNSAFE, (p, props, state, theme) -> {})
                                .withDefaultProps(Panel.defaultProps).hasContextWillUpdate(UNSAFE)
                            && Panel.JX.withDefaultProps(Panel.defaultProps).hasContextWillReceiveProps(DEPRECATED)
                            """ : ""));
            String output = "case-" + context + "-" + pure;
            assertEquals(List.of(), check(compiled, List.of(probe), output));
            try (var loader = new URLClassLoader(new java.net.URL[]{directory.resolve(output).toUri().toURL()}, null)) {
                assertEquals(true, loader.loadClass("demo.Probe").getMethod("verify").invoke(null));
            }
            Compilation editor = new Compilation(EditorProjection.project(List.of(source)), List.of(), List.of());
            assertEquals(List.of(), check(editor, List.of(), output + "-editor"));
        }
    }

    @Test void invalidMethodsRemainSourceLocatedDiagnostics() {
        for (String invalid : List.of(
                BASE.replace("componentWillMount()", "componentWillMount(int unexpected)"),
                BASE.replace("protected void componentWillMount", "private void componentWillMount"),
                BASE.replace("protected void UNSAFE_componentWillMount", "protected static void UNSAFE_componentWillMount"),
                BASE.replace("componentWillReceiveProps(Props next)", "componentWillReceiveProps(Model next)"),
                BASE.replace("componentWillUpdate(Props next, Model pending)", "componentWillUpdate(Props next)"),
                BASE.replace("protected void componentWillMount() { calls.add(\"mount\"); }", "public int componentWillMount = 1;"),
                BASE.replace("UNSAFE_componentWillMount", "UNSAFE_unknown"),
                BASE.replace("public View render()", POLICIES + "public View render()"),
                contextual().replace("String nextContext", "Integer nextContext"),
                contextual().replace("protected void componentWillReceiveProps(Props next, String nextContext)",
                        "protected <T> void componentWillReceiveProps(Props next, String nextContext)"))) {
            SourceFile source = source(invalid);
            Compilation result = compiler.compile(List.of(source));
            assertTrue(result.diagnostics().stream().anyMatch(value -> value.code().equals("JX111")
                    && value.source().equals(source.path()) && value.line() > 1), result.diagnostics().toString());
        }
    }

    @Test void callbackBodiesAndJavaFailuresMapToOriginalSourceInBothProjections() throws Exception {
        String text = contextual();
        for (boolean editor : List.of(false, true)) {
            SourceFile source = source(text);
            Compilation result = editor ? new Compilation(EditorProjection.project(List.of(source)), List.of(), List.of())
                    : compiler.compile(List.of(source));
            GeneratedSource generated = result.generatedSources().getFirst();
            String expression = "nextContext.length()";
            int authored = text.indexOf(expression);
            int java = generated.sourceMap().generatedOffset(authored).orElseThrow();
            assertEquals(expression, generated.javaSource().substring(java, java + expression.length()));
            assertEquals(new SourceSpan(authored, authored + expression.length()),
                    generated.sourceMap().editableSourceSpan(new SourceSpan(java, java + expression.length())).orElseThrow());
            SourceFile invalid = source(text.replace(expression, "nextContext.noSuchMethod()"));
            Compilation broken = editor ? new Compilation(EditorProjection.project(List.of(invalid)), List.of(), List.of())
                    : compiler.compile(List.of(invalid));
            var diagnostics = check(broken, List.of(), "invalid-" + editor);
            assertTrue(diagnostics.stream().anyMatch(value -> value.severity() == Diagnostic.Severity.ERROR
                    && value.source().equals(invalid.path()) && value.line() == invalid.line(authored)), diagnostics.toString());
        }
        var legacy = new ComponentMetadata.ClassSpec(ComponentMetadata.ClassKind.COMPONENT, "Model", List.of(),
                Set.of(), "String", false, false, false, true);
        assertTrue(legacy.defaultProps()); assertEquals(Set.of(), legacy.contextLifecycles());
        SourceFile throwing = source(text.replace("componentWillReceiveProps(Props next, String nextContext) {",
                "componentWillReceiveProps(Props next, String nextContext) throws java.io.IOException {"));
        Compilation checked = compiler.compile(List.of(throwing));
        assertTrue(checked.successful(), checked.diagnostics().toString());
        assertTrue(check(checked, List.of(), "checked-callback").stream().anyMatch(value ->
                value.severity() == Diagnostic.Severity.ERROR && value.source().equals(throwing.path())),
                "Generated context adapters must not discard checked Java failures");
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
