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
            // An unfinished editor tag can be shorter than "null". Keep all offsets stable even
            // for that partial tree; this shadow is parsed only for declarations, never executed.
            String placeholder = element.span().end() - element.span().start() >= 4 ? "null" : "0";
            shadow.replace(element.span().start(), element.span().start() + placeholder.length(), placeholder);
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
                        isComponentAnnotation(annotation.getAnnotationType().toString(), packageName, imports, unit));
                if (!annotated) continue;
                boolean memoized = type.getModifiers().getAnnotations().stream().anyMatch(annotation ->
                        isJxAnnotation("Memo", annotation.getAnnotationType().toString(), packageName, imports, unit));
                SourceSpan declarationSpan = span(positions, unit, type, shadow.length());
                ComponentMetadata.ClassKind classKind = ClassModel.kind(type, packageName, imports, unit);
                if (classKind != null) {
                    ComponentMetadata component = ClassModel.read(parsed, type, classKind, memoized,
                            packageName, imports, unit, positions, shadow, diagnostics);
                    if (component != null) components.add(component);
                    continue;
                }
                List<MethodTree> renders = type.getMembers().stream().filter(MethodTree.class::isInstance)
                        .map(MethodTree.class::cast).filter(m -> m.getName().contentEquals("render")).toList();
                if (renders.size() != 1 || !renders.getFirst().getModifiers().getFlags().containsAll(Set.of(Modifier.PUBLIC, Modifier.STATIC))
                        || renders.getFirst().getParameters().size() > 1) {
                    diagnostics.add(Diagnostic.error(parsed.source(), "JX100",
                            "@Component requires one public static render() or render(Props) method", declarationSpan));
                    continue;
                }
                MethodTree render = renders.getFirst();
                boolean hasProps = !render.getParameters().isEmpty();
                // A generic Props record is named with its type arguments, such as Props<T>.
                String parameterType = hasProps ? render.getParameters().getFirst().getType().toString().replaceFirst("<.*", "") : "";
                if (hasProps && !parameterType.equals("Props") && !parameterType.equals(type.getSimpleName() + ".Props")) {
                    diagnostics.add(Diagnostic.error(parsed.source(), "JX100", "render's parameter must be the nested Props record", declarationSpan));
                    continue;
                }
                ClassTree props = type.getMembers().stream().filter(ClassTree.class::isInstance).map(ClassTree.class::cast)
                        .filter(c -> c.getKind() == Tree.Kind.RECORD && c.getSimpleName().contentEquals("Props")).findFirst().orElse(null);
                if (hasProps && (props == null || !props.getModifiers().getFlags().contains(Modifier.PUBLIC))) {
                    diagnostics.add(Diagnostic.error(parsed.source(), "JX100", "A component with props requires a public nested record Props", declarationSpan));
                    continue;
                }
                if (hasProps && props.getTypeParameters().size() != render.getTypeParameters().size()) {
                    diagnostics.add(Diagnostic.error(parsed.source(), "JX100", "A generic Props record needs a render method with the same type parameters, such as <T> View render(Props<T> props)", declarationSpan));
                    continue;
                }
                if (!type.getTypeParameters().isEmpty()) {
                    diagnostics.add(Diagnostic.error(parsed.source(), "JX100", "Generic component classes are not supported; make the Props record and the render method generic", declarationSpan));
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
                        boolean optional = variable.getModifiers().getAnnotations().stream().anyMatch(annotation ->
                                isJxAnnotation("OptionalProp", annotation.getAnnotationType().toString(), packageName, imports, unit));
                        properties.add(new ComponentMetadata.Prop(variable.getName().toString(), variable.getType().toString(),
                                span(positions, unit, variable, shadow.length()), optional));
                        // As in React 19, ref is an ordinary prop of a component; only key is not.
                        if (variable.getName().contentEquals("key")) {
                            diagnostics.add(Diagnostic.error(parsed.source(), "JX100", "Props field 'key'"
                                    + " is reserved for framework identity metadata", span(positions, unit, variable, shadow.length())));
                        }
                    }
                }
                List<MethodTree> comparisons = type.getMembers().stream().filter(MethodTree.class::isInstance)
                        .map(MethodTree.class::cast).filter(m -> m.getName().contentEquals("arePropsEqual")).toList();
                if (!comparisons.isEmpty() && (!memoized || !hasProps || comparisons.size() != 1
                        || comparisons.getFirst().getParameters().size() != 2
                        || !comparisons.getFirst().getModifiers().getFlags().containsAll(Set.of(Modifier.PUBLIC, Modifier.STATIC)))) {
                    diagnostics.add(Diagnostic.error(parsed.source(), "JX100",
                            "arePropsEqual must be one public static method (Props previous, Props next) on a @Memo component with props", declarationSpan));
                    continue;
                }
                ComponentMetadata.Memo memo = !memoized ? ComponentMetadata.Memo.NONE
                        : comparisons.isEmpty() ? ComponentMetadata.Memo.FIELDS : ComponentMetadata.Memo.CUSTOM;
                String simpleName = type.getSimpleName().toString();
                int descriptorOffset = declarationSpan.end() - 1;
                // javac ends an unterminated class at EOF. That is not an insertion point: the
                // authored final character can belong to an unfinished interpolation or Java call.
                if (descriptorOffset < declarationSpan.start() || shadow.charAt(descriptorOffset) != '}')
                    descriptorOffset = -1;
                components.add(new ComponentMetadata(packageName.isEmpty() ? simpleName : packageName + "." + simpleName,
                        simpleName, packageName, imports, hasProps, properties, parsed.source().path(),
                        declarationSpan, descriptorOffset, memo, hasProps ? props.getTypeParameters().size() : 0));
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

    /** Resolve supported annotation spellings from authored imports, without treating Spring or other
     * same-named component annotations as JX. Full javac attribution still validates ambiguous Java. */
    /** The same import resolution for another annotation of the jx package, such as Memo. */
    static boolean isJxAnnotation(String simpleName, String annotation, String packageName, List<String> imports, CompilationUnitTree unit) {
        if (annotation.equals("jx." + simpleName)) return true;
        if (!annotation.equals(simpleName)) return false;
        List<String> explicit = imports.stream().filter(name -> name.endsWith("." + simpleName)).toList();
        if (!explicit.isEmpty()) return explicit.size() == 1 && explicit.getFirst().equals("jx." + simpleName);
        boolean localType = unit.getTypeDecls().stream().anyMatch(declaration -> declaration instanceof ClassTree type
                && type.getSimpleName().contentEquals(simpleName));
        return !localType && (packageName.equals("jx") || imports.contains("jx.*"));
    }

    private static boolean isComponentAnnotation(String annotation, String packageName, List<String> imports, CompilationUnitTree unit) {
        if (annotation.equals("jx.Component") || annotation.equals("jx.JxComponent")) return true;
        if (!annotation.equals("Component") && !annotation.equals("JxComponent")) return false;
        List<String> explicit = imports.stream().filter(name -> name.endsWith("." + annotation)).toList();
        if (!explicit.isEmpty()) return explicit.size() == 1 && explicit.getFirst().equals("jx." + annotation);
        boolean localType = unit.getTypeDecls().stream().anyMatch(declaration -> declaration instanceof ClassTree type
                && type.getSimpleName().contentEquals(annotation));
        if (localType) return false;
        if (packageName.equals("jx")) return true;
        if (annotation.equals("Component") && imports.contains("org.springframework.stereotype.*")) return false;
        return imports.contains("jx.*");
    }

    static SourceSpan span(SourcePositions positions, CompilationUnitTree unit, Tree tree, int length) {
        int start = (int) Math.clamp(positions.getStartPosition(unit, tree), 0, length);
        int end = (int) Math.clamp(positions.getEndPosition(unit, tree), start, length);
        return new SourceSpan(start, end);
    }
    static JavaCompiler compiler() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            // IntelliJ's system classloader does not expose all boot-layer service providers.
            compiler = java.util.ServiceLoader.load(ModuleLayer.boot(), JavaCompiler.class).stream()
                    .filter(provider -> "jdk.compiler".equals(provider.type().getModule().getName()))
                    .map(java.util.ServiceLoader.Provider::get).findFirst().orElse(null);
        }
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
