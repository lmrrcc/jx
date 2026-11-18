package jx.compiler;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.tools.*;
import static jx.compiler.Syntax.*;

record JavaModel(ParsedSource parsed, String packageName, List<String> imports, List<ComponentMetadata> components, List<String> topLevelTypes) {
    static JavaModel read(ParsedSource parsed, List<Diagnostic> diagnostics) {
        StringBuilder shadow = new StringBuilder(parsed.source().content());
        for (Node node : parsed.nodes()) if (node instanceof Element element) {
            for (int i = element.span().start(); i < element.span().end(); i++)
                if (shadow.charAt(i) != '\n' && shadow.charAt(i) != '\r') shadow.setCharAt(i, ' ');
            shadow.replace(element.span().start(), element.span().start() + 4, "null");
        }
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler().getStandardFileManager(collector, null, java.nio.charset.StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler().getTask(null, manager, collector,
                    List.of("-proc:none", "--release", "25"), null,
                    List.of(new MemorySource(parsed.source().path().getFileName().toString().replaceFirst("\\.jx$", ".java"), shadow.toString())));
            CompilationUnitTree unit = task.parse().iterator().next();
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            List<String> imports = unit.getImports().stream().filter(i -> !i.isStatic())
                    .map(i -> i.getQualifiedIdentifier().toString()).toList();
            List<ComponentMetadata> components = new ArrayList<>();
            for (Tree declaration : unit.getTypeDecls()) {
                if (!(declaration instanceof ClassTree type)) continue;
                boolean annotated = type.getModifiers().getAnnotations().stream().anyMatch(annotation ->
                        annotation.getAnnotationType().toString().equals("JxComponent")
                                || annotation.getAnnotationType().toString().equals("jx.JxComponent"));
                if (!annotated) continue;
                SourceSpan declarationSpan = span(positions, unit, type, shadow.length());
                List<MethodTree> renders = type.getMembers().stream().filter(MethodTree.class::isInstance)
                        .map(MethodTree.class::cast).filter(m -> m.getName().contentEquals("render")).toList();
                if (renders.size() != 1 || !renders.getFirst().getModifiers().getFlags().containsAll(Set.of(Modifier.PUBLIC, Modifier.STATIC))
                        || renders.getFirst().getParameters().size() > 1) {
                    diagnostics.add(Diagnostic.error(parsed.source(), "JX100",
                            "@JxComponent requires one public static render() or render(Props) method", declarationSpan));
                    continue;
                }
                MethodTree render = renders.getFirst();
                boolean hasProps = !render.getParameters().isEmpty();
                if (hasProps && !render.getParameters().getFirst().getType().toString().equals("Props")
                        && !render.getParameters().getFirst().getType().toString().equals(type.getSimpleName() + ".Props")) {
                    diagnostics.add(Diagnostic.error(parsed.source(), "JX100", "render's parameter must be the nested Props record", declarationSpan));
                    continue;
                }
                ClassTree props = type.getMembers().stream().filter(ClassTree.class::isInstance).map(ClassTree.class::cast)
                        .filter(c -> c.getKind() == Tree.Kind.RECORD && c.getSimpleName().contentEquals("Props")).findFirst().orElse(null);
                if (hasProps && (props == null || !props.getModifiers().getFlags().contains(Modifier.PUBLIC))) {
                    diagnostics.add(Diagnostic.error(parsed.source(), "JX100", "A component with props requires a public nested record Props", declarationSpan));
                    continue;
                }
                if (hasProps && !props.getTypeParameters().isEmpty()) {
                    diagnostics.add(Diagnostic.error(parsed.source(), "JX100", "Props must be a concrete record; generic record parameters are not supported", declarationSpan));
                    continue;
                }
                if (!type.getTypeParameters().isEmpty()) {
                    diagnostics.add(Diagnostic.error(parsed.source(), "JX100", "Generic component classes are not supported; use concrete Props", declarationSpan));
                    continue;
                }
                if (type.getMembers().stream().anyMatch(member -> member instanceof VariableTree variable && variable.getName().contentEquals("JX"))) {
                    diagnostics.add(Diagnostic.error(parsed.source(), "JX100", "JX is reserved for the generated component descriptor", declarationSpan));
                    continue;
                }
                List<ComponentMetadata.Prop> properties = new ArrayList<>();
                if (hasProps) {
                    // javac exposes record components as private final VariableTrees before the body.
                    for (Tree member : props.getMembers()) if (member instanceof VariableTree variable
                            && !variable.getModifiers().getFlags().contains(Modifier.STATIC)) {
                        properties.add(new ComponentMetadata.Prop(variable.getName().toString(), variable.getType().toString(),
                                span(positions, unit, variable, shadow.length())));
                        if (variable.getName().contentEquals("key") || variable.getName().contentEquals("ref")) {
                            diagnostics.add(Diagnostic.error(parsed.source(), "JX100", "Props field '" + variable.getName()
                                    + "' is reserved for framework identity/ref metadata", span(positions, unit, variable, shadow.length())));
                        }
                    }
                }
                String simpleName = type.getSimpleName().toString();
                components.add(new ComponentMetadata(packageName.isEmpty() ? simpleName : packageName + "." + simpleName,
                        simpleName, packageName, imports, hasProps, properties, parsed.source().path(),
                        declarationSpan, Math.max(declarationSpan.start(), declarationSpan.end() - 1)));
            }
            for (javax.tools.Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics())
                if (diagnostic.getKind() == javax.tools.Diagnostic.Kind.ERROR) {
                    int start = (int) Math.clamp(diagnostic.getPosition(), 0, parsed.source().content().length());
                    diagnostics.add(Diagnostic.error(parsed.source(), "JAVA_PARSE", diagnostic.getMessage(java.util.Locale.ROOT),
                            new SourceSpan(start, Math.min(start + 1, parsed.source().content().length()))));
                }
            List<String> topLevelTypes = unit.getTypeDecls().stream().filter(ClassTree.class::isInstance)
                    .map(ClassTree.class::cast).map(c -> packageName.isEmpty() ? c.getSimpleName().toString()
                            : packageName + "." + c.getSimpleName()).toList();
            return new JavaModel(parsed, packageName, imports, List.copyOf(components), topLevelTypes);
        } catch (IOException ex) { throw new IllegalStateException("Cannot parse Java source", ex); }
    }

    private static SourceSpan span(SourcePositions positions, CompilationUnitTree unit, Tree tree, int length) {
        int start = (int) Math.clamp(positions.getStartPosition(unit, tree), 0, length);
        int end = (int) Math.clamp(positions.getEndPosition(unit, tree), start, length);
        return new SourceSpan(start, end);
    }
    static JavaCompiler compiler() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("The JX compiler requires a JDK, not a JRE");
        return compiler;
    }
    static final class MemorySource extends SimpleJavaFileObject {
        private final String content;
        MemorySource(String name, String content) { super(uri(name), Kind.SOURCE); this.content = content; }
        @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return content; }
        private static URI uri(String name) {
            try { return new URI("memory", null, "/" + name.replace('\\', '/'), null); }
            catch (URISyntaxException ex) { throw new IllegalArgumentException("Invalid source name", ex); }
        }
    }
}
