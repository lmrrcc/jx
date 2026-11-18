package jx.compiler;

import java.util.List;

/** Shared source-level type lookup in Java's declaration, explicit import, package, wildcard order. */
final class ComponentLookup {
    static List<ComponentMetadata> candidates(List<ComponentMetadata> components, SourceContext context, String name) {
        if (name.contains(".")) return components.stream().filter(c -> c.qualifiedName().equals(name)).toList();
        List<ComponentMetadata> candidates = components.stream().filter(c -> c.source().equals(context.source()) && c.simpleName().equals(name)).toList();
        if (!candidates.isEmpty()) return candidates;
        candidates = components.stream().filter(c -> c.simpleName().equals(name) && context.imports().contains(c.qualifiedName())).toList();
        if (!candidates.isEmpty()) return candidates;
        String qualified = context.packageName().isEmpty() ? name : context.packageName() + "." + name;
        candidates = components.stream().filter(c -> c.qualifiedName().equals(qualified)).toList();
        if (!candidates.isEmpty()) return candidates;
        return components.stream().filter(c -> context.imports().stream().anyMatch(i ->
                i.endsWith(".*") && (i.substring(0, i.length() - 1) + name).equals(c.qualifiedName()))).toList();
    }
}
