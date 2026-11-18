package jx.compiler;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditorProjectionTest {
    private static final String PREFIX = """
            package sample;
            import jx.*;
            @Component public final class Screen {
                private static final String label = "Hello";
                public static View render() {
                    String local = label;
                    return \
            """;

    @Test void retainsIncompleteEventMemberInItsTypedCallbackAndLexicalScope() {
        for (String tail : List.of(
                "<button onClick={event -> event.",
                "<button onClick={event -> event./>; } }",
                "<button onClick={event -> event.; } }",
                "(<button onClick={event -> event.); } }",
                "<button onClick={event -> event.>Click</button>; } }")) {
            SourceFile authored = source(PREFIX + tail);
            GeneratedSource projection = project(authored);
            assertTrue(projection.javaSource().contains("String local = label;"), projection.javaSource());
            assertTrue(projection.javaSource().contains(".on(\"onClick\", event -> event.)"), projection.javaSource());
            assertCopied(projection, "event -> event.");
            assertCopied(projection, "String local = label;");
            if (tail.endsWith("; } }")) assertCopied(projection, "; } ");
            assertStrictlyRejected(authored);
        }
    }

    @Test void keepsJavaAfterAnUnterminatedChildElement() {
        SourceFile authored = source(PREFIX + "<main>{local}</main\n; } String after() { return label; } }");
        GeneratedSource projection = project(authored);
        assertTrue(projection.javaSource().contains("jx.Views.element(\"main\", jx.Attributes.create(), local)"), projection.javaSource());
        assertCopied(projection, "String after() { return label; }");
        assertStrictlyRejected(authored);
    }

    @Test void balancesNestedJavaCallsArraysAndBlockLambdasBeforeRecoveringMarkup() {
        String callback = "event -> { String punctuation = \";)}\"; int[] values = {1, 2}; consume(values[0], punctuation); }";
        SourceFile authored = source(PREFIX + "<button onClick={" + callback + "/>; } }");
        GeneratedSource projection = project(authored);
        assertCopied(projection, callback);
        assertTrue(projection.javaSource().contains(".on(\"onClick\", " + callback + "))"), projection.javaSource());
        assertStrictlyRejected(authored);
    }

    @Test void keepsPartialChildExpressionsAndAncestorClosingTags() {
        SourceFile authored = source(PREFIX + "<main><button>{local.</main>; } }");
        GeneratedSource projection = project(authored);
        assertTrue(projection.javaSource().contains("jx.Views.element(\"main\""), projection.javaSource());
        assertTrue(projection.javaSource().contains("jx.Views.element(\"button\", jx.Attributes.create(), local.)"), projection.javaSource());
        assertCopied(projection, "local.");
        assertStrictlyRejected(authored);
    }

    @Test void usesSiblingPropConstructorForIncompleteTypedCallback() {
        SourceFile sibling = new SourceFile(Path.of("Picker.jx"), """
                package sample;
                import jx.*;
                @Component public final class Picker {
                    public record Props(EventHandler onSelect) {}
                    public static View render(Props props) { return <button onClick={props.onSelect()} />; }
                }
                """);
        SourceFile authored = source(PREFIX + "<Picker onSelect={event -> event.");
        GeneratedSource projection = EditorProjection.project(List.of(authored, sibling)).stream()
                .filter(value -> value.source().equals(authored)).findFirst().orElseThrow();
        assertTrue(projection.javaSource().contains("new Picker.Props(event -> event.)"), projection.javaSource());
        assertCopied(projection, "event -> event.");
        assertMappings(projection);
        assertStrictlyRejected(authored);
    }

    @Test void leavesCompleteMarkupPunctuationAndJavaComparisonsUnchanged() {
        SourceFile authored = source(PREFIX + "<button title={local.length() > 0 ? local : \"empty\"}>Hello ); } &amp;</button>; } }");
        GeneratedSource projection = project(authored);
        assertTrue(projection.javaSource().contains("\"Hello ); } &\""), projection.javaSource());
        assertCopied(projection, "local.length() > 0 ? local : \"empty\"");
        assertTrue(new JxCompiler().compile(List.of(authored)).successful());
    }

    @Test void preservesUnicodeUtf16OffsetsAndRejectsSyntheticEditorEdits() {
        SourceFile authored = source(PREFIX.replace("Hello", "Hello 🧭") + "<button onClick={event -> event.");
        GeneratedSource projection = project(authored);
        assertSame(authored, projection.source());
        int generated = projection.javaSource().indexOf(".on(\"onClick\", ");
        assertTrue(projection.sourceMap().editableSourceSpan(new SourceSpan(generated, generated + 3)).isEmpty());
        int finalParenthesis = projection.javaSource().length() - 1;
        assertEquals(')', projection.javaSource().charAt(finalParenthesis));
        assertTrue(projection.sourceMap().editableSourceSpan(new SourceSpan(finalParenthesis, finalParenthesis + 1)).isEmpty());
        assertCopied(projection, "event -> event.");
    }

    @Test void recoversEveryPrefixOfAPropertyAndChildWithoutInventingAuthoredText() {
        String markup = "<a onClick={event -> event.preventDefault()} title=\"Hi\">{local}</a>";
        for (int end = 2; end <= markup.length(); end++) {
            SourceFile authored = source(PREFIX + markup.substring(0, end));
            GeneratedSource projection = assertDoesNotThrow(() -> project(authored), "Prefix " + end);
            assertSame(authored, projection.source());
        }
    }

    @Test void retainsCompilerResourceLimits() {
        SourceFile excessiveDepth = source(PREFIX + "<a>".repeat(140));
        assertTrue(EditorProjection.project(List.of(excessiveDepth)).isEmpty());
        SourceFile excessiveLength = source(" ".repeat(4 * 1024 * 1024 + 1));
        assertTrue(EditorProjection.project(List.of(excessiveLength)).isEmpty());
        assertTrue(EditorProjection.project(List.of(new SourceFile(Path.of("Other.java"), "class Other {}"))).isEmpty());
    }

    @Test void keepsReorderedPropsKeyAndChildrenInAuthoredOrder() {
        SourceFile pair = new SourceFile(Path.of("Pair.jx"), """
                package sample;
                import jx.*;
                @Component public class Pair {
                    public record Props(String first, String second, View children) {}
                    public static View render(Props props) { return <div/>; }
                }
                """);
        SourceFile authored = source(PREFIX + """
                <Pair key={local + "key"} second={local + "second"} first={local + "first"}>
                    <span>{local + "child"}</span>
                </Pair>; } }
                """);
        GeneratedSource projection = EditorProjection.project(List.of(authored, pair)).stream()
                .filter(value -> value.source().equals(authored)).findFirst().orElseThrow();
        assertMappings(projection);
        for (String suffix : List.of("key", "second", "first", "child")) assertCopied(projection, "local + \"" + suffix + "\"");
        assertTrue(projection.javaSource().contains("new Pair.Props(null, local + \"second\", null)"), projection.javaSource());
        assertTrue(projection.javaSource().contains("new Pair.Props(local + \"first\", null, null)"), projection.javaSource());
        GeneratedSource executable = new JxCompiler().compile(List.of(authored, pair)).generatedSources().stream()
                .filter(value -> value.source().equals(authored)).findFirst().orElseThrow();
        assertTrue(executable.javaSource().contains("new Pair.Props(local + \"first\", local + \"second\", jx.Views.fragment("), executable.javaSource());
    }

    @Test void boundsConstructorExpansionWithoutDiscardingOtherProjectSources() {
        assertExpansionUnavailable("Wide", 65, 17);
        // A long, valid type name can exhaust generated text before the expanded prop-slot limit.
        assertExpansionUnavailable("Wide" + "x".repeat(2044), 8, 600);
    }

    private static void assertExpansionUnavailable(String name, int propCount, int repetitions) {
        String props = java.util.stream.IntStream.range(0, propCount).mapToObj(index -> "String p" + index)
                .collect(java.util.stream.Collectors.joining(", "));
        String attributes = java.util.stream.IntStream.range(0, propCount).mapToObj(index -> " p" + index + "=\"value\"")
                .collect(java.util.stream.Collectors.joining());
        SourceFile component = new SourceFile(Path.of(name + ".jx"), "package sample; import jx.*; @Component public class "
                + name + " { public record Props(" + props + ") {} public static View render(Props props) { return <div/>; } }");
        SourceFile authored = source(PREFIX + "<>" + ("<" + name + attributes + "/>").repeat(repetitions) + "</>; } }");
        List<GeneratedSource> projections = EditorProjection.project(List.of(authored, component));
        assertEquals(List.of(component.path()), projections.stream().map(value -> value.source().path()).toList(),
                "Only the over-budget projection must be unavailable");
        Compilation ordinary = new JxCompiler().compile(List.of(authored, component));
        assertTrue(ordinary.successful(), ordinary.diagnostics().toString());
        assertEquals(2, ordinary.generatedSources().size(), "Editor-only budgets must not restrict ordinary compilation");
    }

    private static SourceFile source(String text) { return new SourceFile(Path.of("Screen.jx"), text); }

    private static GeneratedSource project(SourceFile authored) {
        List<GeneratedSource> projections = EditorProjection.project(List.of(authored));
        assertEquals(1, projections.size(), authored.content());
        GeneratedSource projection = projections.getFirst();
        assertMappings(projection);
        return projection;
    }

    private static void assertStrictlyRejected(SourceFile authored) {
        Compilation result = new JxCompiler().compile(List.of(authored));
        assertFalse(result.successful(), authored.content());
        assertTrue(result.generatedSources().isEmpty(), "Build compilation must not use editor recovery");
    }

    private static void assertCopied(GeneratedSource projection, String text) {
        int sourceStart = projection.source().content().indexOf(text);
        assertTrue(sourceStart >= 0, text);
        int generatedStart = projection.sourceMap().generatedOffset(sourceStart).orElseThrow();
        assertEquals(text, projection.javaSource().substring(generatedStart, generatedStart + text.length()));
        assertEquals(new SourceSpan(sourceStart, sourceStart + text.length()),
                projection.sourceMap().editableSourceSpan(new SourceSpan(generatedStart, generatedStart + text.length())).orElseThrow());
    }

    private static void assertMappings(GeneratedSource projection) {
        int sourceEnd = 0;
        for (SourceMap.Segment segment : projection.sourceMap().segments()) {
            if (!segment.linear()) continue;
            assertTrue(segment.sourceStart() >= sourceEnd, "Editor Java slices must follow authored order for safe native refactoring");
            sourceEnd = segment.sourceStart() + segment.length();
            assertEquals(projection.source().content().substring(segment.sourceStart(), segment.sourceStart() + segment.length()),
                    projection.javaSource().substring(segment.generatedStart(), segment.generatedStart() + segment.length()));
        }
    }
}
