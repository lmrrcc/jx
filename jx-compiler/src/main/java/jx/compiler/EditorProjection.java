package jx.compiler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only Java projections for IDE-owned Java language engines. No builds, classloading of project
 * code, annotation processors or source writes occur here. GeneratedSource carries the exact authored
 * snapshot and UTF-16 mappings; synthetic ranges must not be written back into user source.
 * This representation is for editor analysis, not a second execution/compiler pipeline. */
public final class EditorProjection {
    private EditorProjection() {}

    /** Lower source snapshots even when Java or markup is incomplete while typing. Missing markup
     * delimiters recover a partial syntax tree; generated wrappers supply the same Java callback and
     * props contexts as a complete build. Copied Java remains character-for-character mapped to this
     * exact snapshot. Synthetic wrappers and recovery delimiters are never editable source ranges.
     * Authored Java slices remain in source order, including component attributes whose order differs
     * from the Props constructor; the projection is analysis-only and must never be executed.
     * Include sibling components so their typed props can participate in native Java inference.
     * Sources exceeding compiler limits, 65,536 expanded prop slots, or 16,777,216 generated UTF-16
     * code units, or with unrecoverable nesting, are absent, never stale. Limits are per source.
     * This does not relax {@link JxCompiler#compile(List)} or run hook validation/build processing. */
    public static List<GeneratedSource> project(List<SourceFile> sources) {
        List<JavaModel> models = new ArrayList<>();
        for (SourceFile source : sources.stream().sorted(Comparator.comparing(s -> s.path().toString())).toList()) {
            if (!source.path().toString().endsWith(".jx")) continue;
            Syntax.ParsedSource parsed = new MarkupParser(source).parse();
            if (!parsed.diagnostics().isEmpty()) parsed = new MarkupParser(source, true).parse();
            if (parsed.nodes().isEmpty() && !source.content().isEmpty()) continue;
            models.add(JavaModel.read(parsed, new ArrayList<>()));
        }
        List<ComponentMetadata> components = models.stream().flatMap(model -> model.components().stream()).toList();
        List<GeneratedSource> generated = new ArrayList<>();
        for (JavaModel model : models) {
            try { generated.add(Lowerer.editor(model, components).lower()); }
            catch (Lowerer.EditorLimitException exceeded) {
                // Absence explicitly tells adapters to discard the native working copy. A partial
                // or stale generated unit must never masquerade as the current authored snapshot.
            }
        }
        return List.copyOf(generated);
    }
}
