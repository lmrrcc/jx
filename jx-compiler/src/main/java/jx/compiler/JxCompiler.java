package jx.compiler;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

/**
 * Stateless compiler service. Calls use immutable snapshots and are independent across modules/threads.
 * Java checks require a JDK 25 and never run annotation processors.
 */
public final class JxCompiler {
    /** Parse markup and preserve ordinary Java text. Errors are returned, including incomplete editor input. */
    public Syntax.ParsedSource parse(SourceFile source) { return new MarkupParser(source).parse(); }

    /** Recover package/import context with javac parsing, even when an opening tag is incomplete. */
    public SourceContext context(SourceFile source) {
        Syntax.ParsedSource parsed = parse(source);
        if (source.content().length() > 4 * 1024 * 1024) throw new IllegalArgumentException("Source exceeds compiler limit");
        JavaModel model = JavaModel.read(parsed, new ArrayList<>());
        return new SourceContext(source.path(), model.packageName(), model.imports());
    }

    /** Resolve a component using the same source/import rules as lowering; empty means unknown or ambiguous. */
    public java.util.Optional<ComponentMetadata> resolveComponent(Compilation compilation, SourceContext context, String name) {
        List<ComponentMetadata> candidates = ComponentLookup.candidates(compilation.components(), context, name);
        return candidates.size() == 1 ? java.util.Optional.of(candidates.getFirst()) : java.util.Optional.empty();
    }

    /** Resolve all source-declared components, validate property names and generate ordinary Java. No files are written. */
    public Compilation compile(List<SourceFile> sources) {
        return compile(sources, ComponentDependencies.EMPTY);
    }

    /** Resolves source declarations and versioned component descriptors from class directories
     * or source-less JARs. No application classes or processors execute. Source declarations
     * own matching qualified names; callers should exclude their own stale output directory.
     * Missing/incompatible library metadata is reported at the importing markup tag. */
    public Compilation compile(List<SourceFile> sources, List<Path> classpath) throws IOException {
        return compile(sources, ComponentDependencies.read(List.copyOf(classpath)));
    }

    private Compilation compile(List<SourceFile> sources, ComponentDependencies dependencies) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<JavaModel> models = new ArrayList<>();
        for (SourceFile source : sources.stream().sorted(Comparator.comparing(s -> s.path().toString())).toList()) {
            if (!source.path().toString().endsWith(".jx")) {
                diagnostics.add(Diagnostic.error(source, "JX105", "Expected a .jx source", new SourceSpan(0, 0)));
                continue;
            }
            Syntax.ParsedSource parsed = parse(source);
            diagnostics.addAll(parsed.diagnostics());
            if (parsed.diagnostics().isEmpty()) models.add(JavaModel.read(parsed, diagnostics));
        }
        List<ComponentMetadata> declared = models.stream().flatMap(m -> m.components().stream()).toList();
        dependencies = dependencies.excludingSources(declared);
        List<ComponentMetadata> components = new ArrayList<>(declared);
        components.addAll(dependencies.components());
        Map<String, ComponentMetadata> names = new LinkedHashMap<>();
        for (ComponentMetadata component : declared) {
            if (names.putIfAbsent(component.qualifiedName(), component) != null) {
                SourceFile source = models.stream().map(m -> m.parsed().source()).filter(s -> s.path().equals(component.source())).findFirst().orElseThrow();
                diagnostics.add(Diagnostic.error(source, "JX106", "Duplicate component '" + component.qualifiedName() + "'", component.declaration()));
            }
        }
        List<GeneratedSource> generated = new ArrayList<>();
        Map<Path, GeneratedSource> outputs = new LinkedHashMap<>();
        for (JavaModel model : models) {
            GeneratedSource source = new Lowerer(model, components, diagnostics, Map.of(), dependencies).lower();
            if (outputs.putIfAbsent(source.relativePath(), source) != null)
                diagnostics.add(Diagnostic.error(source.source(), "JX106", "Duplicate generated path " + source.relativePath(), new SourceSpan(0, 0)));
            generated.add(source);
            HookRules.check(source, components, diagnostics);
        }
        Map<String, String> signatures = HookSignatures.compute(generated, components);
        ComponentDependencies resolved = dependencies;
        List<GeneratedSource> finalSources = models.stream()
                .map(model -> new Lowerer(model, components, new ArrayList<>(), signatures, resolved).lower()).toList();
        return new Compilation(finalSources, components, diagnostics);
    }

    /**
     * Typecheck generated and ordinary Java together and map javac diagnostics back to authored sources.
     * Classes are written only to the caller-owned classOutput directory; this does not replace Maven's compilation.
     * The supplied classpath is complete (no implicit agent or Maven plugin classpath is added).
     */
    public List<Diagnostic> check(Compilation compilation, List<Path> ordinaryJavaSources,
                                  List<Path> classpath, Path classOutput) throws IOException {
        if (!compilation.successful()) return compilation.diagnostics();
        List<Diagnostic> result = new ArrayList<>(compilation.diagnostics());
        var collector = new javax.tools.DiagnosticCollector<JavaFileObject>();
        Files.createDirectories(classOutput);
        try (StandardJavaFileManager manager = JavaModel.compiler().getStandardFileManager(collector, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<JavaFileObject> units = new ArrayList<>();
            Map<java.net.URI, GeneratedSource> generated = new LinkedHashMap<>();
            for (GeneratedSource source : compilation.generatedSources()) {
                var unit = new JavaModel.MemorySource(source.relativePath().toString(), source.javaSource());
                units.add(unit); generated.put(unit.toUri(), source);
            }
            for (JavaFileObject unit : manager.getJavaFileObjectsFromPaths(ordinaryJavaSources)) units.add(unit);
            if (units.isEmpty()) return result;
            List<String> options = List.of("--release", "25", "-proc:none", "-encoding", "UTF-8",
                    "-d", classOutput.toAbsolutePath().toString(), "-classpath",
                    String.join(java.io.File.pathSeparator, classpath.stream().map(p -> p.toAbsolutePath().toString()).toList()));
            StringWriter messages = new StringWriter();
            boolean success = JavaModel.compiler().getTask(messages, manager, collector, options, null, units).call();
            for (javax.tools.Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                if (diagnostic.getKind() != javax.tools.Diagnostic.Kind.ERROR
                        && diagnostic.getKind() != javax.tools.Diagnostic.Kind.WARNING
                        && diagnostic.getKind() != javax.tools.Diagnostic.Kind.MANDATORY_WARNING) continue;
                GeneratedSource mapped = diagnostic.getSource() == null ? null : generated.get(diagnostic.getSource().toUri());
                SourceFile original;
                int start, end;
                if (mapped != null) {
                    original = mapped.source();
                    start = mapped.sourceMap().sourceOffset(diagnostic.getPosition());
                    end = Math.max(start + 1, mapped.sourceMap().sourceOffset(diagnostic.getEndPosition()));
                } else if (diagnostic.getSource() != null && diagnostic.getSource().toUri().getScheme().equals("file")) {
                    Path path = Path.of(diagnostic.getSource().toUri());
                    original = new SourceFile(path, Files.readString(path));
                    start = (int) Math.clamp(diagnostic.getPosition(), 0, original.content().length());
                    end = (int) Math.clamp(diagnostic.getEndPosition(), start, original.content().length());
                } else {
                    if (compilation.generatedSources().isEmpty()) throw new IOException("javac: " + diagnostic.getMessage(Locale.ROOT));
                    original = compilation.generatedSources().getFirst().source(); start = 0; end = 0;
                }
                end = Math.min(original.content().length(), end);
                result.add(new Diagnostic(original.path(),
                        diagnostic.getKind() == javax.tools.Diagnostic.Kind.ERROR ? Diagnostic.Severity.ERROR : Diagnostic.Severity.WARNING,
                        diagnostic.getCode(), diagnostic.getMessage(Locale.ROOT), new SourceSpan(start, end),
                        original.line(start), original.column(start)));
            }
            if (!success && result.stream().noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR))
                throw new IOException("javac failed without a diagnostic: " + messages);
        }
        return List.copyOf(result);
    }
}
