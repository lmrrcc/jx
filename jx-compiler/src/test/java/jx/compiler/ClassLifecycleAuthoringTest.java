package jx.compiler;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClassLifecycleAuthoringTest {
    @TempDir Path directory;
    private final JxCompiler compiler = new JxCompiler();
    private static final String PANEL = """
            package demo;
            import jx.*;
            import java.util.function.Supplier;
            @Component public final class Panel extends ClassComponent<Panel.Props, Panel.Model, Void> {
                public record Props(int revision) {}
                public record Model(int count, Throwable error) {}
                public static final Context<String> contextType = new Context<>("day");
                private final Supplier<String> theme;
                public Throwable reported;
                public Panel(Props props, Supplier<String> theme) {
                    super(props, new Model(props.revision(), null));
                    this.theme = theme;
                }
                public static StatePatch<Model> getDerivedStateFromProps(Props props, Model pending) {
                    return props.revision() == pending.count() ? null : statePatch().withCount(props.revision()).build();
                }
                public static StatePatch<Model> getDerivedStateFromError(Throwable error) {
                    return statePatch().withError(error).build();
                }
                @Override protected void componentDidCatch(Throwable error, ComponentErrorInfo info) { reported = error; }
                public String theme() { return theme.get(); }
                @Override public View render() { return <p>{theme.get()}: {state().count()}</p>; }
            }
            """;

    @Test void compilesTypedContextAndBothStaticDerivationsWithTheRealCoreAndEditorProjection() throws Exception {
        for (boolean pure : List.of(false, true)) {
            String text = pure ? PANEL.replace("extends ClassComponent", "extends PureComponent") : PANEL;
            SourceFile source = source(text);
            Compilation compilation = compiler.compile(List.of(source));
            assertTrue(compilation.successful(), compilation.diagnostics().toString());
            ComponentMetadata.ClassSpec spec = compilation.components().getFirst().classSpec();
            assertEquals("String", spec.contextValueType());
            assertTrue(spec.derivedProps());
            assertTrue(spec.derivedError());
            assertTrue(spec.lifecycles().contains(ComponentMetadata.Lifecycle.CATCH));
            Path probe = directory.resolve("Probe.java");
            Files.writeString(probe, """
                    package demo;
                    import jx.*;
                    public final class Probe {
                        public static boolean verify() {
                            ClassComponentType.ContextAccess access = new ClassComponentType.ContextAccess() {
                                public <T> java.util.function.Supplier<T> current(Context<T> context) { return context::defaultValue; }
                            };
                            Panel peer = Panel.JX.create(new Panel.Props(1), access);
                            Throwable error = new IllegalArgumentException("original");
                            StatePatch<Panel.Model> patch = Panel.JX.deriveStateFromProps(new Panel.Props(2), peer.initialState());
                            StatePatch<Panel.Model> failed = Panel.JX.deriveStateFromError(error);
                            peer.caught(error, new ComponentErrorInfo("native stack"));
                            return peer.theme().equals("day") && Panel.JX.contextType() == Panel.contextType
                                && patch.entries().size() == 1 && patch.entries().getFirst().value().equals(2)
                                && failed.entries().size() == 1 && failed.entries().getFirst().value() == error
                                && peer.reported == error && Panel.JX.lifecycle(ClassComponentType.Lifecycle.CATCH)
                                && Panel.JX.deriveStateFromProps(new Panel.Props(1), peer.initialState()) == null;
                        }
                    }
                    """);
            String output = pure ? "pure" : "normal";
            assertEquals(List.of(), check(compilation, List.of(probe), output));
            try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{directory.resolve(output).toUri().toURL()}, null)) {
                assertEquals(true, loader.loadClass("demo.Probe").getMethod("verify").invoke(null));
            }
            Compilation projection = new Compilation(EditorProjection.project(List.of(source)), List.of(), List.of());
            assertEquals(List.of(), check(projection, List.of(), output + "-editor"));
            GeneratedSource generated = projection.generatedSources().getFirst();
            String expression = "statePatch().withError(error).build()";
            int authored = text.indexOf(expression);
            int java = generated.sourceMap().generatedOffset(authored).orElseThrow();
            assertEquals(expression, generated.javaSource().substring(java, java + expression.length()));
            assertEquals(new SourceSpan(authored, authored + expression.length()),
                    generated.sourceMap().editableSourceSpan(new SourceSpan(java, java + expression.length())).orElseThrow());
        }
    }

    @Test void rejectsWrongLifecycleAndContextContractsBeforeGeneratingDescriptors() {
        for (String text : List.of(
                PANEL.replace("public static final Context", "public static Context"),
                PANEL.replace("Context<String> contextType", "Context<?> contextType"),
                PANEL.replace("import jx.*;", "import jx.*; import foreign.Context;"),
                PANEL.replace("import jx.*;", "import jx.*; import foreign.StatePatch;"),
                PANEL.replace("public static StatePatch<Model> getDerivedStateFromProps", "public StatePatch<Model> getDerivedStateFromProps"),
                PANEL.replace("getDerivedStateFromProps(Props props, Model pending)", "getDerivedStateFromProps(Model props, Model pending)"),
                PANEL.replace("StatePatch<Model> getDerivedStateFromError", "StatePatch<Props> getDerivedStateFromError"),
                PANEL.replace("getDerivedStateFromError(Throwable error)", "getDerivedStateFromError(Exception error)"),
                PANEL.replace("componentDidCatch(Throwable error, ComponentErrorInfo info)", "componentDidCatch(Throwable error, String info)"))) {
            Compilation result = compiler.compile(List.of(source(text)));
            assertTrue(result.diagnostics().stream().anyMatch(value -> value.code().equals("JX111")), result.diagnostics().toString());
        }
        for (String text : List.of(PANEL.replace("Panel(Props props, Supplier<String> theme)", "Panel(Props props, Supplier<Integer> theme)"),
                PANEL.replace("import java.util.function.Supplier;", "import foreign.Supplier;"))) {
            Compilation result = compiler.compile(List.of(source(text)));
            assertTrue(result.diagnostics().stream().anyMatch(value -> value.code().equals("JX110")), result.diagnostics().toString());
        }
    }

    @Test void mapsDerivedMethodTypeErrorsToTheAuthoredBodyInBothProjections() throws Exception {
        String text = PANEL.replace("withCount(props.revision())", "withCount(\"wrong\")");
        SourceFile source = source(text);
        for (boolean editor : List.of(false, true)) {
            Compilation compilation = editor ? new Compilation(EditorProjection.project(List.of(source)), List.of(), List.of())
                    : compiler.compile(List.of(source));
            assertTrue(compilation.successful(), compilation.diagnostics().toString());
            List<Diagnostic> errors = check(compilation, List.of(), "invalid-" + editor);
            assertTrue(errors.stream().anyMatch(value -> value.severity() == Diagnostic.Severity.ERROR
                    && value.source().equals(source.path()) && value.line() == source.line(text.indexOf("withCount(\"wrong\")"))), errors.toString());
        }
    }

    @Test void qualifiedTypesCompileAndCheckedFailuresInCallbacksStayJavaErrors() throws Exception {
        String qualified = PANEL.replace("Context<String>", "jx.Context<java.lang.String>")
                .replace("Supplier<String>", "java.util.function.Supplier<java.lang.String>")
                .replace("StatePatch<Model>", "jx.StatePatch<Panel.Model>")
                .replace("Throwable error", "java.lang.Throwable error")
                .replace("ComponentErrorInfo info", "jx.ComponentErrorInfo info");
        Compilation compilation = compiler.compile(List.of(source(qualified)));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        assertEquals(List.of(), check(compilation, List.of(), "qualified"));
        for (String text : List.of(
                PANEL.replace("getDerivedStateFromError(Throwable error) {", "getDerivedStateFromError(Throwable error) throws java.io.IOException {"),
                PANEL.replace("Panel(Props props, Supplier<String> theme) {", "Panel(Props props, Supplier<String> theme) throws java.io.IOException {"))) {
            Compilation invalid = compiler.compile(List.of(source(text)));
            assertTrue(invalid.successful(), invalid.diagnostics().toString());
            assertTrue(check(invalid, List.of(), "checked-" + Math.abs(text.hashCode())).stream()
                    .anyMatch(value -> value.severity() == Diagnostic.Severity.ERROR && value.source().equals(source(text).path())),
                    "Checked constructor/callback exceptions must not disappear behind generated adapters");
        }
    }

    @Test void typedThreeArgumentOverloadWinsOverLegacyAndPreservesEditableJava() throws Exception {
        String legacy = "@Override protected boolean shouldComponentUpdate(Props next, Model pending) { return false; }";
        String policy = """
                protected boolean shouldComponentUpdate(Props next, Model pending, String nextContext) {
                    return next.revision() == 2 && pending.count() == 3 && nextContext.equals("day")
                        && props().revision() == 1 && state().count() == 1 && theme.get().equals("day");
                }
                """;
        for (boolean pure : List.of(false, true)) for (boolean includeLegacy : List.of(false, true)) {
            String text = PANEL.replace("public String theme()", (includeLegacy ? legacy : "") + policy + "public String theme()");
            if (pure) text = text.replace("extends ClassComponent", "extends PureComponent");
            SourceFile source = source(text);
            Compilation compilation = compiler.compile(List.of(source));
            assertTrue(compilation.successful(), compilation.diagnostics().toString());
            assertTrue(compilation.components().getFirst().classSpec().contextShouldUpdate());
            Path probe = directory.resolve("PolicyProbe.java");
            Files.writeString(probe, """
                    package demo;
                    import jx.*;
                    public final class PolicyProbe {
                        public static boolean verify() {
                            ClassComponentType.ContextAccess access = new ClassComponentType.ContextAccess() {
                                public <T> java.util.function.Supplier<T> current(Context<T> context) { return context::defaultValue; }
                            };
                            Panel peer = Panel.JX.create(new Panel.Props(1), access);
                            return Panel.JX.hasContextShouldUpdate()
                                && Panel.JX.shouldUpdate(peer, new Panel.Props(2), new Panel.Model(3, null), access)
                                && Panel.JX.hasDerivedStateFromProps() && Panel.JX.hasDerivedStateFromError();
                        }
                    }
                    """);
            String output = "policy-" + pure + "-" + includeLegacy;
            assertEquals(List.of(), check(compilation, List.of(probe), output));
            try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{directory.resolve(output).toUri().toURL()}, null)) {
                assertEquals(true, loader.loadClass("demo.PolicyProbe").getMethod("verify").invoke(null));
            }
            Compilation projection = new Compilation(EditorProjection.project(List.of(source)), List.of(), List.of());
            assertEquals(List.of(), check(projection, List.of(), output + "-editor"));
            GeneratedSource generated = projection.generatedSources().getFirst();
            String expression = "nextContext.equals(\"day\")";
            int authored = text.indexOf(expression);
            int java = generated.sourceMap().generatedOffset(authored).orElseThrow();
            assertEquals(expression, generated.javaSource().substring(java, java + expression.length()));
            assertEquals(new SourceSpan(authored, authored + expression.length()),
                    generated.sourceMap().editableSourceSpan(new SourceSpan(java, java + expression.length())).orElseThrow());
        }
        String contextual = PANEL.replace("public String theme()", policy + "public String theme()");
        for (String text : List.of(
                contextual.replace("String nextContext", "Integer nextContext"),
                contextual.replace("protected boolean shouldComponentUpdate", "protected static boolean shouldComponentUpdate"),
                contextual.replace("protected boolean shouldComponentUpdate", "private boolean shouldComponentUpdate"),
                contextual.replace("public static final Context<String> contextType = new Context<>(\"day\");", ""))) {
            Compilation invalid = compiler.compile(List.of(source(text)));
            assertTrue(invalid.diagnostics().stream().anyMatch(value -> value.code().equals("JX111")), invalid.diagnostics().toString());
        }
    }

    private SourceFile source(String text) { return new SourceFile(directory.resolve("Panel.jx"), text); }
    private List<Diagnostic> check(Compilation compilation, List<Path> extra, String output) throws Exception {
        List<Path> java = new ArrayList<>(extra);
        try (var files = Files.walk(Path.of("..", "jx-core", "src", "main", "java"))) {
            java.addAll(files.filter(value -> value.toString().endsWith(".java")).sorted().toList());
        }
        return compiler.check(compilation, java, List.of(), directory.resolve(output));
    }
}
