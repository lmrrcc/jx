package jx.compiler;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkupCommentsTest {
    private static final String PREFIX = "class Screen { Object render(String label) { return ";

    @Test void ignoresCommentContentsButPreservesSurroundingTextAndJavaMappings() {
        SourceFile source = source(PREFIX
                + "<main>before <!-- ignored </main><Invalid /> {broken &raw --> after {label}</main>; } }");
        var parsed = new JxCompiler().parse(source);
        assertTrue(parsed.diagnostics().isEmpty(), parsed.diagnostics().toString());
        var element = (Syntax.Element) parsed.nodes().get(1);
        assertEquals(3, element.children().size());
        assertEquals("before ", ((Syntax.Text) element.children().get(0)).value());
        assertEquals(" after ", ((Syntax.Text) element.children().get(1)).value());
        var compilation = new JxCompiler().compile(List.of(source));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        GeneratedSource generated = compilation.generatedSources().getFirst();
        assertTrue(generated.javaSource().contains("\"before \", \" after \", label"), generated.javaSource());
        assertFalse(generated.javaSource().contains("ignored"));
        int expression = source.content().lastIndexOf("label");
        int projected = generated.sourceMap().generatedOffset(expression).orElseThrow();
        assertEquals(new SourceSpan(expression, expression + 5),
                generated.sourceMap().editableSourceSpan(new SourceSpan(projected, projected + 5)).orElseThrow());
        assertTrue(generated.sourceMap().generatedOffset(source.content().indexOf("ignored")).isEmpty());
    }

    @Test void acceptsCommentsInsideFragmentsAndBetweenElementsWithoutRenderingThem() {
        SourceFile source = source(PREFIX + "<><!-- one --><span/><!-- two --></>; } }");
        var compilation = new JxCompiler().compile(List.of(source));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        assertTrue(compilation.generatedSources().getFirst().javaSource()
                .contains("jx.Views.fragment(jx.Views.element(\"span\", jx.Attributes.create()))"));
    }

    @Test void doesNotInterpretCommentMarkersInJavaOrQuotedAttributes() {
        SourceFile source = source("class Screen { String note = \"<!-- Java -->\"; Object render() { return "
                + "<span title=\"&lt;!-- Attribute --&gt;\"/>; } }");
        var compilation = new JxCompiler().compile(List.of(source));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        String generated = compilation.generatedSources().getFirst().javaSource();
        assertTrue(generated.contains("String note = \"<!-- Java -->\""));
        assertTrue(generated.contains(".attr(\"title\", \"<!-- Attribute -->\")"));
    }

    @Test void reportsUnterminatedCommentAtItsOpeningAndDoesNotGenerateBuildOutput() {
        SourceFile source = source(PREFIX + "<main>{label}<!-- unfinished");
        var compilation = new JxCompiler().compile(List.of(source));
        assertFalse(compilation.successful());
        assertTrue(compilation.generatedSources().isEmpty());
        assertEquals(1, compilation.diagnostics().size());
        var diagnostic = compilation.diagnostics().getFirst();
        assertEquals("JX015", diagnostic.code());
        assertEquals(source.content().indexOf("<!--") + 1, diagnostic.column());
    }

    @Test void editorRecoversUnterminatedCommentsWithoutTreatingTheirContentsAsJava() {
        SourceFile source = source(PREFIX + "<main>{label}<!-- unfinished {notJava}");
        var parsed = new MarkupParser(source, true).parse();
        assertTrue(parsed.diagnostics().stream().anyMatch(d -> d.code().equals("JX015")));
        var projections = EditorProjection.project(List.of(source));
        assertEquals(1, projections.size());
        GeneratedSource generated = projections.getFirst();
        assertTrue(generated.javaSource().contains("jx.Views.element(\"main\", jx.Attributes.create(), label)"));
        assertFalse(generated.javaSource().contains("notJava"));
        int expression = source.content().lastIndexOf("label");
        assertTrue(generated.sourceMap().generatedOffset(expression).isPresent());
    }

    private static SourceFile source(String content) { return new SourceFile(Path.of("Screen.jx"), content); }
}
