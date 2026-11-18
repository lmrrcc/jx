package jx.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static jx.compiler.Syntax.*;

final class Lowerer {
    private static final int MAX_EDITOR_CHARS = 16 * 1024 * 1024;
    private static final long MAX_EDITOR_PROP_SLOTS = 65_536;
    private final JavaModel model;
    private final List<ComponentMetadata> components;
    private final List<Diagnostic> diagnostics;
    private final SourceFile source;
    private final MappedBuilder out;
    private final Set<Integer> inserted = new HashSet<>();
    private final Map<String, String> signatures;
    private final boolean orderedEditor;
    private long editorPropSlots;

    Lowerer(JavaModel model, List<ComponentMetadata> components, List<Diagnostic> diagnostics) {
        this(model, components, diagnostics, Map.of());
    }

    Lowerer(JavaModel model, List<ComponentMetadata> components, List<Diagnostic> diagnostics, Map<String, String> signatures) {
        this(model, components, diagnostics, signatures, false);
    }

    static Lowerer editor(JavaModel model, List<ComponentMetadata> components) {
        return new Lowerer(model, components, new ArrayList<>(), Map.of(), true);
    }

    private Lowerer(JavaModel model, List<ComponentMetadata> components, List<Diagnostic> diagnostics,
                    Map<String, String> signatures, boolean orderedEditor) {
        this.model = model; this.components = components; this.diagnostics = diagnostics;
        this.signatures = signatures;
        this.orderedEditor = orderedEditor;
        this.source = model.parsed().source(); this.out = new MappedBuilder(source, orderedEditor);
    }

    GeneratedSource lower() {
        nodes(model.parsed().nodes());
        String name = source.path().getFileName().toString();
        name = name.substring(0, name.length() - 3) + ".java";
        Path path = model.packageName().isEmpty() ? Path.of(name) : Path.of(model.packageName().replace('.', '/'), name);
        return new GeneratedSource(source, path, out.text.toString(), new SourceMap(out.segments, source.content().length()), model.topLevelTypes());
    }

    private void nodes(List<Node> nodes) {
        for (Node node : nodes) {
            switch (node) {
                case JavaText java -> copy(java.span());
                case Element element -> element(element);
                case Interpolation interpolation -> nodes(interpolation.expression());
                case Text text -> out.synthetic(quote(whitespace(text.value())), text.span().start());
            }
        }
    }

    private void copy(SourceSpan span) {
        int start = span.start();
        for (ComponentMetadata component : model.components()) {
            int offset = component.descriptorOffset();
            if (offset < start || offset >= span.end() || !inserted.add(offset)) continue;
            out.copy(start, offset);
            String type = component.hasProps() ? "Props" : "Void";
            String function = component.hasProps() ? component.simpleName() + "::render" : "ignored -> " + component.simpleName() + ".render()";
            out.synthetic("\n    /** Stable React component identity generated from this declaration. */\n"
                    + "    public static final jx.ComponentType<" + type + "> JX = new jx.ComponentType<>("
                    + quote(component.qualifiedName()) + ", " + quote(signatures.getOrDefault(component.qualifiedName(), ""))
                    + ", " + function + ");\n", component.declaration().start());
            start = offset;
        }
        out.copy(start, span.end());
    }

    private void element(Element element) {
        int anchor = element.nameSpan().start();
        if (element.name().isEmpty()) {
            out.synthetic("jx.Views.fragment(", anchor);
            children(element.children());
            out.synthetic(")", anchor);
            return;
        }
        boolean isComponent = Character.isUpperCase(element.name().charAt(0)) || element.name().contains(".");
        Map<String, Attribute> attributes = new LinkedHashMap<>();
        for (Attribute attribute : element.attributes()) {
            String name = isComponent ? attribute.name() : canonicalAttribute(attribute.name());
            if (attributes.putIfAbsent(name, attribute) != null)
                error("JX101", "Duplicate property '" + attribute.name() + "'", attribute.nameSpan());
        }
        if (isComponent) {
            component(element, attributes);
            return;
        }
        out.synthetic("jx.Views.element(" + quote(element.name()) + ", jx.Attributes.create()", anchor);
        for (Attribute attribute : attributes.values()) {
            String name = canonicalAttribute(attribute.name());
            boolean handler = name.length() > 2 && name.startsWith("on") && Character.isUpperCase(name.charAt(2));
            out.synthetic((handler ? ".on(" : ".attr(") + quote(name) + ", ", attribute.nameSpan().start());
            value(attribute);
            out.synthetic(")", attribute.span().end() - 1);
        }
        List<Node> children = meaningfulChildren(element.children());
        if (!children.isEmpty()) {
            out.synthetic(", ", anchor); children(children);
        }
        out.synthetic(")", anchor);
    }

    private static String canonicalAttribute(String name) {
        return switch (name) { case "class" -> "className"; case "for" -> "htmlFor"; default -> name; };
    }

    private void component(Element element, Map<String, Attribute> attributes) {
        int anchor = element.nameSpan().start();
        ComponentMetadata component = resolve(element.name(), element.nameSpan());
        if (component == null) { out.synthetic("null", anchor); return; }
        if (orderedEditor) {
            editorComponent(element, component);
            return;
        }
        Set<String> declared = new HashSet<>();
        for (ComponentMetadata.Prop prop : component.props()) declared.add(prop.name());
        for (Attribute attribute : attributes.values())
            if (!declared.contains(attribute.name()) && !attribute.name().equals("key"))
                error("JX102", "Unknown property '" + attribute.name() + "' on " + component.simpleName(), attribute.nameSpan());
        List<Node> children = meaningfulChildren(element.children());
        if (!children.isEmpty() && !declared.contains("children"))
            error("JX102", component.simpleName() + " does not declare a children property", element.nameSpan());
        if (!children.isEmpty() && attributes.containsKey("children"))
            error("JX101", "Children are specified both as a property and nested content", attributes.get("children").nameSpan());
        out.synthetic("jx.Views.component(" + element.name() + ".JX, ", anchor);
        if (!component.hasProps()) out.synthetic("null", anchor);
        else {
            out.synthetic("new " + element.name() + ".Props(", anchor);
            boolean first = true;
            for (ComponentMetadata.Prop prop : component.props()) {
                if (!first) out.synthetic(", ", anchor);
                first = false;
                Attribute attribute = attributes.get(prop.name());
                if (attribute != null) value(attribute);
                else if (prop.name().equals("children") && !children.isEmpty()) {
                    out.synthetic("jx.Views.fragment(", anchor); children(children); out.synthetic(")", anchor);
                } else {
                    error("JX103", "Missing required property '" + prop.name() + "' on " + component.simpleName(), element.nameSpan());
                    out.synthetic("null", anchor);
                }
            }
            out.synthetic(")", anchor);
        }
        Attribute key = attributes.get("key");
        if (key != null) { out.synthetic(", ", key.nameSpan().start()); value(key); }
        out.synthetic(")", anchor);
    }

    /** Native IDE injection edits require monotonically ordered authored slices. Build lowering
     * orders props by constructor slot, which can reverse their source order. For analysis only,
     * each attribute gets its own real Props constructor context, with synthetic defaults in other
     * slots. This preserves exact lambda target types without introducing fake public helper APIs.
     * This projection is never executed; ordinary compilation retains its original semantics. */
    private void editorComponent(Element element, ComponentMetadata component) {
        List<Node> children = meaningfulChildren(element.children());
        long calls = Math.max(1L, element.attributes().size() + (children.isEmpty() ? 0 : 1));
        long slots = calls * Math.max(1, component.props().size());
        if (slots > MAX_EDITOR_PROP_SLOTS - editorPropSlots) throw new EditorLimitException();
        editorPropSlots += slots;
        int anchor = element.nameSpan().start();
        out.synthetic("jx.Views.fragment(", anchor);
        boolean first = true;
        for (Attribute attribute : element.attributes()) {
            if (!first) out.synthetic(", ", attribute.nameSpan().start());
            first = false;
            boolean declared = component.props().stream().anyMatch(prop -> prop.name().equals(attribute.name()));
            if (!declared && !attribute.name().equals("key")) {
                // Keep invalid-property expressions available to Java editing; the shared JX
                // diagnostics still report the unknown property against the authored tag.
                out.synthetic("jx.Views.fragment(", attribute.nameSpan().start());
                value(attribute);
                out.synthetic(")", attribute.span().end());
                continue;
            }
            editorComponentCall(element, component, attribute, List.of());
        }
        if (!children.isEmpty()) {
            if (!first) out.synthetic(", ", anchor);
            first = false;
            boolean declared = component.props().stream().anyMatch(prop -> prop.name().equals("children"));
            if (declared) editorComponentCall(element, component, null, children);
            else {
                out.synthetic("jx.Views.fragment(", anchor);
                children(children);
                out.synthetic(")", anchor);
            }
        }
        if (first) editorComponentCall(element, component, null, List.of());
        out.synthetic(")", anchor);
    }

    private void editorComponentCall(Element element, ComponentMetadata component, Attribute active, List<Node> children) {
        int anchor = element.nameSpan().start();
        out.synthetic("jx.Views.component(" + element.name() + ".JX, ", anchor);
        if (!component.hasProps()) out.synthetic("null", anchor);
        else {
            out.synthetic("new " + element.name() + ".Props(", anchor);
            boolean first = true;
            boolean copied = false;
            for (ComponentMetadata.Prop prop : component.props()) {
                if (!first) out.synthetic(", ", anchor);
                first = false;
                if (!copied && active != null && !active.name().equals("key") && prop.name().equals(active.name())) {
                    value(active); copied = true;
                } else if (!copied && active == null && prop.name().equals("children") && !children.isEmpty()) {
                    out.synthetic("jx.Views.fragment(", anchor); children(children); out.synthetic(")", anchor);
                    copied = true;
                } else out.synthetic(editorDefault(prop.javaType()), anchor);
            }
            out.synthetic(")", anchor);
        }
        if (active != null && active.name().equals("key")) {
            out.synthetic(", ", active.nameSpan().start()); value(active);
        }
        out.synthetic(")", anchor);
    }

    private static String editorDefault(String type) {
        String stripped = type.stripTrailing();
        // javac's type spelling may start with type-use annotations. Array and reference spellings
        // never end in a standalone primitive token; their constructor-compatible default is null.
        for (String primitive : List.of("boolean", "byte", "short", "char", "int", "long", "float", "double")) {
            int start = stripped.length() - primitive.length();
            if (start < 0 || !stripped.endsWith(primitive) || (start > 0 && !Character.isWhitespace(stripped.charAt(start - 1)))) continue;
            return switch (primitive) {
                case "boolean" -> "false"; case "byte" -> "(byte) 0"; case "short" -> "(short) 0";
                case "char" -> "'\\0'"; case "long" -> "0L"; case "float" -> "0F"; case "double" -> "0D";
                default -> "0";
            };
        }
        return "null";
    }

    private ComponentMetadata resolve(String name, SourceSpan span) {
        List<ComponentMetadata> candidates = ComponentLookup.candidates(components,
                new SourceContext(source.path(), model.packageName(), model.imports()), name);
        if (candidates.size() == 1) return candidates.getFirst();
        error("JX104", candidates.isEmpty() ? "Unknown component '" + name + "' (include its .jx source in this compilation)"
                : "Ambiguous component '" + name + "'", span);
        return null;
    }

    private void value(Attribute attribute) {
        if (attribute.expression() != null) nodes(attribute.expression().expression());
        else out.synthetic(attribute.literal() == null ? "true" : quote(attribute.literal()), attribute.span().start());
    }

    private List<Node> meaningfulChildren(List<Node> children) {
        return children.stream().filter(n -> !(n instanceof Text t) || !whitespace(t.value()).isEmpty()).toList();
    }
    private void children(List<Node> children) {
        boolean first = true;
        for (Node child : meaningfulChildren(children)) {
            if (!first) out.synthetic(", ", child.span().start());
            first = false;
            nodes(List.of(child));
        }
    }
    private void error(String code, String message, SourceSpan span) {
        diagnostics.add(Diagnostic.error(source, code, message, span));
    }

    /** JSX-style layout whitespace: trim indentation on interior lines, join nonempty lines with a space. */
    static String whitespace(String value) {
        String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ').split("\n", -1);
        if (lines.length == 1) return lines[0];
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0) line = line.stripLeading();
            if (i < lines.length - 1) line = line.stripTrailing();
            if (line.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(line);
        }
        return result.toString();
    }

    static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                default -> {
                    if (c < 32 || c == 127) result.append(String.format(java.util.Locale.ROOT, "\\%03o", (int) c));
                    else result.append(c);
                }
            }
        }
        return result.append('"').toString();
    }

    private static final class MappedBuilder {
        final SourceFile source;
        final boolean editor;
        final StringBuilder text = new StringBuilder();
        final List<SourceMap.Segment> segments = new ArrayList<>();
        MappedBuilder(SourceFile source, boolean editor) { this.source = source; this.editor = editor; }
        void copy(int start, int end) {
            if (end <= start) return;
            checkBudget(end - start);
            segments.add(new SourceMap.Segment(text.length(), end - start, start, true));
            text.append(source.content(), start, end);
        }
        void synthetic(String value, int anchor) {
            if (value.isEmpty()) return;
            checkBudget(value.length());
            segments.add(new SourceMap.Segment(text.length(), value.length(), anchor, false));
            text.append(value);
        }
        private void checkBudget(int additional) {
            if (editor && additional > MAX_EDITOR_CHARS - text.length()) throw new EditorLimitException();
        }
    }

    static final class EditorLimitException extends RuntimeException {
        EditorLimitException() { super("Editor projection exceeds its expansion budget"); }
    }
}
