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
            @Component
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
                @Component public final class App {
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

    @Test void memoComponentsCompareEachPropsFieldOrUseTheirOwnComparison() throws Exception {
        var compilation = compiler.compile(List.of(source("Row.jx", """
                package demo;
                import jx.*;
                @Component @Memo public final class Row {
                    public record Props(String label, int tick) {}
                    public static View render(Props props) { return <p>{props.label()}</p>; }
                }
                """), source("Custom.jx", """
                package demo;
                @jx.Component @jx.Memo public final class Custom {
                    public record Props(String label) {}
                    public static boolean arePropsEqual(Props previous, Props next) { return true; }
                    public static jx.View render(Props props) { return <p>{props.label()}</p>; }
                }
                """), source("Still.jx", """
                package demo;
                import jx.*;
                @Component @Memo public final class Still {
                    public static View render() { return <p>still</p>; }
                }
                """)));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        String all = String.join("\n", compilation.generatedSources().stream().map(GeneratedSource::javaSource).toList());
        assertTrue(all.contains(".memo((previous, next) -> jx.ComponentType.sameValue(previous.label(), next.label())"
                + " && jx.ComponentType.sameValue(previous.tick(), next.tick()))"), all);
        assertTrue(all.contains(".memo(Custom::arePropsEqual)"), all);
        assertTrue(all.contains(".memo((previous, next) -> true)"), all);
        assertEquals(List.of(), check(compilation));
    }

    @Test void rejectsAPropsComparisonWithoutMemo() throws Exception {
        var compilation = compiler.compile(List.of(source("Plain.jx", """
                package demo;
                import jx.*;
                @Component public final class Plain {
                    public record Props(String label) {}
                    public static boolean arePropsEqual(Props previous, Props next) { return true; }
                    public static View render(Props props) { return <p>{props.label()}</p>; }
                }
                """)));
        assertFalse(compilation.successful());
        assertTrue(compilation.diagnostics().toString().contains("arePropsEqual"), compilation.diagnostics().toString());
    }

    @Test void optionalPropsSpreadsAndGenericPropsLowerToTypedJava() throws Exception {
        var compilation = compiler.compile(List.of(source("Badge.jx", """
                package demo;
                import jx.*;
                @Component public final class Badge {
                    public record Props(@OptionalProp String label, @OptionalProp int size, boolean strong) {
                        public Props { if (label == null) label = "default"; }
                    }
                    public static View render(Props props) { return <p>{props.label()}</p>; }
                }
                """), source("Rows.jx", """
                package demo;
                import jx.*;
                import java.util.List;
                import java.util.function.Function;
                @Component public final class Rows {
                    public record Props<T>(List<T> items, Function<T, String> label) {}
                    public static <T> View render(Props<T> props) {
                        return <ul>{props.items().stream().map(item -> <li>{props.label().apply(item)}</li>).toList()}</ul>;
                    }
                }
                """), source("Page.jx", """
                package demo;
                import jx.*;
                import java.util.List;
                @Component public final class Page {
                    public static View render() {
                        Attributes shared = Attributes.create().attr("title", "shared");
                        Badge.Props forwarded = new Badge.Props("forwarded", 2, true);
                        return <main>
                            <Badge strong />
                            <Badge {...forwarded} key="k" />
                            <a id="first" {...shared} title="last">link</a>
                            <Rows items={List.of("a", "b")} label={item -> item.toUpperCase()} />
                        </main>;
                    }
                }
                """)));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        String all = String.join("\n", compilation.generatedSources().stream().map(GeneratedSource::javaSource).toList());
        assertTrue(all.contains("new Badge.Props(null, 0, true)"), all);
        assertTrue(all.contains("jx.Views.component(Badge.JX, forwarded, \"k\")"), all);
        assertTrue(all.contains(".attr(\"id\", \"first\").merge(shared).attr(\"title\", \"last\")"), all);
        assertTrue(all.contains("jx.ComponentType<Props<?>> JX"), all);
        assertTrue(all.contains("props -> Rows.render(props)"), all);
        assertTrue(all.contains("new Rows.Props<>(List.of(\"a\", \"b\"), item -> item.toUpperCase())"), all);
        assertEquals(List.of(), check(compilation));
    }

    @Test void rejectsASpreadMixedWithOtherComponentProperties() throws Exception {
        var compilation = compiler.compile(List.of(source("Tag.jx", """
                package demo;
                import jx.*;
                @Component public final class Tag {
                    public record Props(String label, String tone) {}
                    public static View render(Props props) { return <p>{props.label()}</p>; }
                }
                """), source("Use.jx", """
                package demo;
                import jx.*;
                @Component public final class Use {
                    public static View render() {
                        Tag.Props props = new Tag.Props("a", "b");
                        return <Tag {...props} tone="c" />;
                    }
                }
                """)));
        assertFalse(compilation.successful());
        assertTrue(compilation.diagnostics().stream().anyMatch(d -> d.code().equals("JX105")), compilation.diagnostics().toString());
    }

    @Test void acceptsComponentAnnotationAndDeprecatedSourceAlias() throws Exception {
        for (String declaration : List.of(
                "import jx.Component; @Component",
                "import jx.*; @Component",
                "@jx.Component",
                "import jx.JxComponent; @JxComponent",
                "@jx.JxComponent")) {
            var compilation = compiler.compile(List.of(source("Example.jx", declaration
                    + " public final class Example { public static jx.View render() { return <span>Typed component</span>; } }")));
            assertTrue(compilation.successful(), compilation.diagnostics().toString());
            assertEquals(1, compilation.components().size(), declaration);
            assertTrue(compilation.generatedSources().getFirst().javaSource().contains("jx.ComponentType<Void> JX = new jx.ComponentType<>("));
            assertTrue(check(compilation).stream().noneMatch(diagnostic -> diagnostic.severity() == Diagnostic.Severity.ERROR), declaration);
        }
    }

    @Test void doesNotTreatSpringOrOtherComponentAnnotationsAsJx() {
        for (String declaration : List.of(
                "import org.springframework.stereotype.Component; @Component",
                "import org.springframework.stereotype.Component; import jx.*; @Component",
                "import org.springframework.stereotype.*; @Component",
                "@org.springframework.stereotype.Component",
                "import another.Component; @Component",
                "import jx.*; @interface Component {} @Component")) {
            var compilation = compiler.compile(List.of(source("Service.jx", declaration + " public class Service {}")));
            assertTrue(compilation.successful(), compilation.diagnostics().toString());
            assertTrue(compilation.components().isEmpty(), declaration);
            assertFalse(compilation.generatedSources().getFirst().javaSource().contains("ComponentType"));
        }
    }

    @Test void preservesJavaLexicalContextsAndSupportsNestedMarkupInJava() throws Exception {
        String app = """
                package demo;
                import jx.*;
                import java.util.*;
                @Component public final class App {
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
                @Component public final class Panel {
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
                @Component public final class App {
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
                @Component public class Bad {
                    public View render() { return <span/>; }
                }
                """);
        assertEquals("JX100", compiler.compile(List.of(source)).diagnostics().getFirst().code());
        SourceFile reserved = source("Bad.jx", "import jx.*; @Component public class Bad { public record Props(String key) {} public static View render(Props props) { return <span/>; } }");
        assertTrue(compiler.compile(List.of(reserved)).diagnostics().stream().anyMatch(d -> d.message().contains("reserved")));
        SourceFile generic = source("Bad.jx", "import jx.*; @Component public class Bad { public record Props<T>(T value) {} public static View render(Props props) { return <span/>; } }");
        assertTrue(compiler.compile(List.of(generic)).diagnostics().stream().anyMatch(d -> d.message().contains("same type parameters")));
    }

    @Test void explicitImportsResolveBeforeAnotherTypeInTheSamePackage() throws Exception {
        SourceFile local = source("demo/Counter.jx", "package demo; import jx.*; @Component public class Counter { public record Props(int initial) {} public static View render(Props props) { return <span/>; } }");
        SourceFile imported = source("widgets/Counter.jx", "package widgets; import jx.*; @Component public class Counter { public record Props(String label) {} public static View render(Props props) { return <span/>; } }");
        SourceFile consumer = source("demo/App.jx", "package demo; import widgets.Counter; import jx.*; @Component public class App { public static View render() { return <Counter label=\"imported\"/>; } }");
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
            SourceFile source = source("App.jx", "import jx.*; @Component public class App { public static View render() { " + body + " } }");
            var compilation = compiler.compile(List.of(source));
            assertTrue(compilation.diagnostics().stream().anyMatch(d -> d.code().equals("JX200")), compilation.diagnostics().toString());
        }
    }

    private SourceFile source(String filename, String content) { return new SourceFile(directory.resolve(filename), content); }
    private static String app(String markup) {
        return """
                package demo;
                import jx.*;
                @Component public final class App {
                    public static View render() {
                        return %s;
                    }
                }
                """.formatted(markup);
    }
    @Test void orderedEditorProjectionPreservesRealPropLambdaAndPrimitiveTypes() throws Exception {
        SourceFile pair = source("Pair.jx", """
                package demo;
                import jx.*;
                import java.util.function.Consumer;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE_USE) @interface Note {}
                @Component public final class Pair {
                    public record Props(@Note byte small, short count, char marker, boolean active,
                                        long total, float ratio, double amount, Consumer<String> onChange,
                                        String label, View children) {}
                    public static View render(Props props) { return <div/>; }
                }
                """);
        SourceFile app = source("App.jx", app("""
                <Pair key={"key"} label={"hello"} onChange={value -> value.length()}
                      amount={2D} ratio={1F} total={3L} active={true} marker={'a'} count={(short) 2} small={(byte) 1}>
                    <span>Child</span>
                </Pair>
                """));
        List<SourceFile> sources = List.of(pair, app);
        var projected = new Compilation(EditorProjection.project(sources), List.of(), List.of());
        List<Diagnostic> checked = check(projected);
        assertTrue(checked.isEmpty(), checked.toString());
        SourceFile invalid = source("App.jx", app.content().replace("value.length()", "value.missingMember()"));
        var broken = new Compilation(EditorProjection.project(List.of(pair, invalid)), List.of(), List.of());
        assertTrue(check(broken).stream().anyMatch(diagnostic -> diagnostic.source().equals(invalid.path())
                && diagnostic.message().contains("missingMember")), "Callback must still be attributed as java.lang.String");
    }

    private List<Diagnostic> check(Compilation compilation) throws Exception {
        // Compile-signature fixtures isolate compiler behavior. The browser sample also compiles against the real core.
        List<Path> files = new ArrayList<>();
        Map<String, String> signatures = new java.util.HashMap<>(Map.of(
                "View", "public interface View {}",
                "Component", "public @interface Component {}",
                "JxComponent", "@Deprecated public @interface JxComponent {}",
                "ComponentType", "public record ComponentType<P>(String name, String stateSignature, java.util.function.Function<P,View> render) { public ComponentType<P> memo(java.util.function.BiPredicate<P,P> propsEqual) { return this; } public static boolean sameValue(Object first, Object second) { return first == second; } }",
                "Memo", "public @interface Memo {}",
                "Views", "public final class Views { public static View element(String tag, Attributes attrs, Object... children) { return null; } public static View fragment(Object... children) { return null; } public static <P> View component(ComponentType<P> type, P props) { return null; } public static <P> View component(ComponentType<P> type, P props, String key) { return null; } }",
                "Attributes", "public final class Attributes { public static Attributes create() { return new Attributes(); } public Attributes attr(String name, Object value) { return this; } public Attributes merge(Attributes other) { return this; } public Attributes on(String name, java.util.function.Consumer<Event> handler) { return this; } }",
                "Event", "public interface Event { void preventDefault(); }",
                "Hooks", "public final class Hooks { public static <T> State<T> useState(T value) { return null; } }",
                "State", "public interface State<T> { T value(); void update(java.util.function.UnaryOperator<T> update); }"));
        signatures.put("OptionalProp", "@java.lang.annotation.Target(java.lang.annotation.ElementType.RECORD_COMPONENT) public @interface OptionalProp {}");
        for (var signature : signatures.entrySet()) {
            Path path = directory.resolve("signatures/jx/" + signature.getKey() + ".java");
            Files.createDirectories(path.getParent());
            Files.writeString(path, "package jx; " + signature.getValue());
            files.add(path);
        }
        return compiler.check(compilation, files, List.of(), directory.resolve("classes"));
    }
}
