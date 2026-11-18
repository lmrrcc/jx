package jx.compiler;

import java.util.List;
import java.util.Objects;

/** Lossless Java slices and parsed markup shared by compiler and editor integrations. */
public final class Syntax {
    private Syntax() {}
    public sealed interface Node permits JavaText, Element, Interpolation, Text {
        SourceSpan span();
    }
    /** Unmodified Java lexical content. */
    public record JavaText(SourceSpan span) implements Node {}
    /** A Java expression between braces, whose nested markup is parsed recursively. */
    public record Interpolation(SourceSpan span, List<Node> expression) implements Node {
        public Interpolation { expression = List.copyOf(expression); }
    }
    /** Text is decoded for named XML and numeric entities, then whitespace-normalized during lowering. */
    public record Text(SourceSpan span, String value) implements Node {}
    /** Empty name denotes a fragment. Attributes retain authored order. */
    public record Element(SourceSpan span, String name, SourceSpan nameSpan,
                          List<Attribute> attributes, List<Node> children) implements Node {
        public Element {
            Objects.requireNonNull(name); attributes = List.copyOf(attributes); children = List.copyOf(children);
        }
    }
    /** Value is either a decoded quoted literal, a Java interpolation, or omitted (boolean true). */
    public record Attribute(String name, SourceSpan span, SourceSpan nameSpan,
                            String literal, Interpolation expression) {}
    public record ParsedSource(SourceFile source, List<Node> nodes, List<Diagnostic> diagnostics) {
        public ParsedSource { nodes = List.copyOf(nodes); diagnostics = List.copyOf(diagnostics); }
    }
}
