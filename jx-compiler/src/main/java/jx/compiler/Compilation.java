package jx.compiler;

import java.util.List;

/** Deterministic batch result. Generation is usable only when successful() is true. */
public record Compilation(List<GeneratedSource> generatedSources, List<ComponentMetadata> components,
                          List<Diagnostic> diagnostics) {
    public Compilation {
        generatedSources = List.copyOf(generatedSources);
        components = List.copyOf(components);
        diagnostics = List.copyOf(diagnostics);
    }
    public boolean successful() { return diagnostics.stream().noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR); }
}
