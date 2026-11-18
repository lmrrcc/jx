package jx.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JxCompilerTest {
    @TempDir Path directory;
    private final JxCompiler compiler = new JxCompiler();
    private static final String COUNTER = """
            package demo;
            import jx.*;
            @JxComponent
            public final class Counter {
                public record Props(int initial, String label) {}
                public static View render(Props props) {
                    State<Integer> count = Hooks.useState(props.initial());
                    return (
                        <button onClick={event -> count.update(previous -> previous + 1)}>
                            {props.label()}: {count.value()}
                        </button>
                    );
                }
            }
            """;

    @Test void compilesTypedComponentsAndCallbacksWithoutInvokingRender() throws Exception {
        var compilation = compiler.compile(List.of(source("Counter.jx", COUNTER), source("App.jx", """
                package demo;
                import jx.*;
                @JxComponent public final class App {
                    public static View render() {
                        return <main><h1>JX application</h1>
                            <Counter initial={0} label="First" />
                            <Counter label="Second" initial={10} />
                        </main>;
                    }
                }
                """)));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        assertEquals(List.of("initial", "label"), compilation.components().stream()
                .filter(c -> c.simpleName().equals("Counter")).findFirst().orElseThrow().props().stream().map(ComponentMetadata.Prop::name).toList());
        String app = compilation.generatedSources().getFirst().javaSource();
        assertTrue(app.contains("new Counter.Props(10, \"Second\")"));
        assertTrue(app.contains("jx.Views.component(Counter.JX"));
        assertFalse(app.contains("Counter.render("));
        assertTrue(compilation.generatedSources().stream().allMatch(s -> s.relativePath().startsWith("demo")));
        assertEquals(List.of(), check(compilation));
    }

    @Test void preservesJavaLexicalContextsAndSupportsNestedMarkupInJava() throws Exception {
        String app = """
                package demo;
                import jx.*;
                import java.util.*;
                @JxComponent public final class App {
                    public static View render() {
                        String string = "<Unknown initial={false}/>";
                        char less = '<';
                        // <Unknown />
                        /* <Wrong /> */
                        List<String> values = new ArrayList<>();
                        values.add("one");
                        boolean compare = values.size() < 2 && 3 > 2;
                        java.util.function.Function<Integer, View> item = value -> <b>{value}</b>;
                        java.util.function.Supplier<java.util.function.Function<String, String>> identity = java.util.function.Function::<String>identity;
                        String block = %s
                          <DoesNotExist />
                          %s;
                        return compare
                            ? <main title={Map.<String, String>of("a", "b").get("a")}>
                                  {values.stream().map(value -> <span key={value}>{value}</span>).toList()}
                                  {item.apply(7)}
                              </main>
                            : <><hr /><span>empty &amp; &#65; &#x42;</span></>;
                    }
                }
                """.formatted("\"\"\"", "\"\"\"");
        var compilation = compiler.compile(List.of(source("App.jx", app)));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        assertEquals(List.of(), check(compilation));
        String generated = compilation.generatedSources().getFirst().javaSource();
        assertTrue(generated.contains("values.size() < 2 && 3 > 2"));
        assertTrue(generated.contains("<DoesNotExist />"));
        assertTrue(generated.contains("empty & A B"));
    }

    @Test void checksMissingDuplicateAndUnknownPropsAtOriginalLocations() {
        for (var entry : Map.of(
                "<Counter initial={0} label=\"ok\" surprise={4}/>", "JX102",
                "<Counter initial={0} initial={1} label=\"ok\"/>", "JX101",
                "<Counter initial={0}/>", "JX103").entrySet()) {
            var source = source("App.jx", app(entry.getKey()));
            var compilation = compiler.compile(List.of(source("Counter.jx", COUNTER), source));
            Diagnostic error = compilation.diagnostics().stream().filter(d -> d.code().equals(entry.getValue())).findFirst().orElseThrow();
            assertEquals(source.path(), error.source());
            assertEquals(5, error.line());
            assertTrue(error.column() > 10);
            assertFalse(compilation.successful());
        }
    }

    @Test void mapsJavaTypeErrorsAndCallbackErrorsToAuthoredExpressions() throws Exception {
        for (String markup : List.of(
                "<Counter initial={\"wrong\"} label=\"ok\"/>",
                "<button onClick={event -> event.doesNotExist()}>Click</button>")) {
            SourceFile authored = source("App.jx", app(markup));
            var compilation = compiler.compile(List.of(source("Counter.jx", COUNTER), authored));
            assertTrue(compilation.successful(), compilation.diagnostics().toString());
            List<Diagnostic> errors = check(compilation).stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).toList();
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().allMatch(d -> d.source().equals(authored.path()) && d.line() == 5), errors.toString());
            assertTrue(errors.getFirst().column() > 25, errors.toString());
            assertFalse(errors.getFirst().toString().contains("generated-sources"));
        }
    }

    @Test void supportsTypedChildrenAndPackageImports() throws Exception {
        var panel = source("Panel.jx", """
                package widgets;
                import jx.*;
                @JxComponent public final class Panel {
                    public record Props(String title, View children) {}
                    public static View render(Props props) {
                        return <section><h2>{props.title()}</h2>{props.children()}</section>;
                    }
                }
                """);
        var app = source("App.jx", """
                package demo;
                import jx.*;
                import widgets.Panel;
                @JxComponent public final class App {
                    public static View render() {
                        return <Panel title="Nested"><span>one</span><span>two</span></Panel>;
                    }
                }
                """);
        var compilation = compiler.compile(List.of(panel, app));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        assertEquals(List.of(), check(compilation));
    }

    @Test void malformedAndIncompleteMarkupProducesBoundedOriginalDiagnostics() {
        for (String markup : List.of("<div></span>", "<Counter initial={0 />", "<div>", "<div title=\"oops>",
                "<div>&invalid;</div>", "<div>{}</div>")) {
            var parsed = compiler.parse(source("App.jx", app(markup)));
            assertEquals(1, parsed.diagnostics().size(), markup);
            Diagnostic error = parsed.diagnostics().getFirst();
            assertEquals(5, error.line(), error.toString());
        }
        var nested = compiler.parse(source("App.jx", app("<a>".repeat(140) + "</a>".repeat(140))));
        assertEquals("JX003", nested.diagnostics().getFirst().code());
    }

    @Test void repeatedGenerationAndSourceMappingsAreDeterministic() {
        SourceFile source = source("Counter.jx", COUNTER);
        Compilation first = compiler.compile(List.of(source));
        Compilation second = compiler.compile(List.of(source));
        assertEquals(first, second);
        GeneratedSource generated = first.generatedSources().getFirst();
        int position = generated.javaSource().indexOf("previous + 1");
        assertEquals(COUNTER.indexOf("previous + 1"), generated.sourceMap().sourceOffset(position));
        assertEquals(generated.sourceMap().serialize(), second.generatedSources().getFirst().sourceMap().serialize());
    }

    @Test void validatesTheComponentContractBeforeGeneratingDescriptors() {
        var source = source("Bad.jx", """
                import jx.*;
                @JxComponent public class Bad {
                    public View render() { return <span/>; }
                }
                """);
        assertEquals("JX100", compiler.compile(List.of(source)).diagnostics().getFirst().code());
        SourceFile reserved = source("Bad.jx", "import jx.*; @JxComponent public class Bad { public record Props(String key) {} public static View render(Props props) { return <span/>; } }");
        assertTrue(compiler.compile(List.of(reserved)).diagnostics().stream().anyMatch(d -> d.message().contains("reserved")));
        SourceFile generic = source("Bad.jx", "import jx.*; @JxComponent public class Bad { public record Props<T>(T value) {} public static View render(Props props) { return <span/>; } }");
        assertTrue(compiler.compile(List.of(generic)).diagnostics().stream().anyMatch(d -> d.message().contains("concrete record")));
    }

    @Test void explicitImportsResolveBeforeAnotherTypeInTheSamePackage() throws Exception {
        SourceFile local = source("demo/Counter.jx", "package demo; import jx.*; @JxComponent public class Counter { public record Props(int initial) {} public static View render(Props props) { return <span/>; } }");
        SourceFile imported = source("widgets/Counter.jx", "package widgets; import jx.*; @JxComponent public class Counter { public record Props(String label) {} public static View render(Props props) { return <span/>; } }");
        SourceFile consumer = source("demo/App.jx", "package demo; import widgets.Counter; import jx.*; @JxComponent public class App { public static View render() { return <Counter label=\"imported\"/>; } }");
        var compilation = compiler.compile(List.of(local, imported, consumer));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        assertEquals(List.of(), check(compilation));
        assertEquals("widgets.Counter", compiler.resolveComponent(compilation, compiler.context(consumer), "Counter").orElseThrow().qualifiedName());
        SourceFile incomplete = new SourceFile(consumer.path(), consumer.content().substring(0, consumer.content().indexOf("<Counter")) + "<Counter ");
        assertEquals("widgets.Counter", compiler.resolveComponent(compilation, compiler.context(incomplete), "Counter").orElseThrow().qualifiedName());
    }

    @Test void rejectsConditionalLoopAndCallbackHookPlacement() {
        for (String body : List.of(
                "if (true) { Hooks.useState(0); } return <span/>;",
                "if (Math.random() > .5) return null; Hooks.useState(0); return <span/>;",
                "for (int i = 0; i < 2; i++) { Hooks.useState(i); } return <span/>;",
                "return <button onClick={event -> Hooks.useState(0)}>bad</button>;",
                "return true ? <span>{Hooks.useState(0).value()}</span> : null;")) {
            SourceFile source = source("App.jx", "import jx.*; @JxComponent public class App { public static View render() { " + body + " } }");
            var compilation = compiler.compile(List.of(source));
            assertTrue(compilation.diagnostics().stream().anyMatch(d -> d.code().equals("JX200")), compilation.diagnostics().toString());
        }
    }

    private SourceFile source(String filename, String content) { return new SourceFile(directory.resolve(filename), content); }
    private static String app(String markup) {
        return """
                package demo;
                import jx.*;
                @JxComponent public final class App {
                    public static View render() {
                        return %s;
                    }
                }
                """.formatted(markup);
    }
    private List<Diagnostic> check(Compilation compilation) throws Exception {
        // Compile-signature fixtures isolate compiler behavior. The browser sample also compiles against the real core.
        List<Path> files = new ArrayList<>();
        Map<String, String> signatures = Map.of(
                "View", "public interface View {}",
                "JxComponent", "public @interface JxComponent {}",
                "Component", "public record Component<P>(String name, java.util.function.Function<P,View> render) {}",
                "Views", "public final class Views { public static View element(String tag, Attributes attrs, Object... children) { return null; } public static View fragment(Object... children) { return null; } public static <P> View component(Component<P> type, P props) { return null; } public static <P> View component(Component<P> type, P props, String key) { return null; } }",
                "Attributes", "public final class Attributes { public static Attributes create() { return new Attributes(); } public Attributes attr(String name, Object value) { return this; } public Attributes on(String name, java.util.function.Consumer<Event> handler) { return this; } }",
                "Event", "public interface Event { void preventDefault(); }",
                "Hooks", "public final class Hooks { public static <T> State<T> useState(T value) { return null; } }",
                "State", "public interface State<T> { T value(); void update(java.util.function.UnaryOperator<T> update); }");
        for (var signature : signatures.entrySet()) {
            Path path = directory.resolve("signatures/jx/" + signature.getKey() + ".java");
            Files.createDirectories(path.getParent());
            Files.writeString(path, "package jx; " + signature.getValue());
            files.add(path);
        }
        return compiler.check(compilation, files, List.of(), directory.resolve("classes"));
    }
}
