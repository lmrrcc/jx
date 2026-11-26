package jx.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

/** Invocation-owned artifact catalog; malformed libraries are diagnosed at their markup use. */
final class ComponentDependencies {
    private record Artifact(Path path, List<ComponentMetadata> components, String failure) {}
    private final List<Artifact> artifacts;
    private final Map<String,List<Artifact>> owners = new HashMap<>();
    private Set<String> sourceNames = Set.of();
    static final ComponentDependencies EMPTY = new ComponentDependencies(List.of());
    private ComponentDependencies(List<Artifact> artifacts) { this.artifacts = List.copyOf(artifacts); }
    static ComponentDependencies read(List<Path> classpath) throws IOException {
        if (classpath.size() > 1024) throw new IOException("Component classpath exceeds 1024 entries");
        List<Artifact> artifacts = new ArrayList<>(); Set<Path> seen = new HashSet<>();
        long units = 0; int components = 0;
        for (Path value : classpath) {
            Path path = value.toAbsolutePath().normalize();
            if (!Files.exists(path)) continue; // Maven includes not-yet-created output directories.
            path = path.toRealPath(); if (!seen.add(path)) continue;
            try {
                var index = ComponentLibrary.read(path);
                artifacts.add(new Artifact(path, index.map(i -> i.entries().stream().map(ComponentLibrary.Entry::component).toList()).orElse(List.of()), null));
            } catch (IOException invalid) { artifacts.add(new Artifact(path, List.of(), invalid.getMessage() == null ? "Truncated component descriptor" : invalid.getMessage())); }
            for (var component : artifacts.getLast().components()) {
                components++;
                units += component.qualifiedName().length();
                for (var prop : component.props()) units += prop.name().length() + prop.javaType().length();
                if (component.classSpec() != null) {
                    units += component.classSpec().stateType().length();
                    if (component.classSpec().contextValueType() != null) units += component.classSpec().contextValueType().length();
                    for (var prop : component.classSpec().stateFields()) units += prop.name().length() + prop.javaType().length();
                }
            }
            if (components > 4096 || units > 16L * 1024 * 1024) throw new IOException("Component classpath catalog exceeds its bounded capacity");
        }
        return new ComponentDependencies(artifacts);
    }
    ComponentDependencies excludingSources(List<ComponentMetadata> sources) {
        var copy = new ComponentDependencies(artifacts);
        copy.sourceNames = sources.stream().map(ComponentMetadata::qualifiedName).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return copy;
    }
    List<ComponentMetadata> components() {
        return artifacts.stream().flatMap(artifact -> artifact.components().stream()).filter(value -> !sourceNames.contains(value.qualifiedName())).toList();
    }
    String problem(SourceContext context, String name) {
        List<String> names;
        if (name.contains(".")) names = List.of(name);
        else {
            var explicit = context.imports().stream().filter(value -> value.endsWith("." + name)).toList();
            if (!explicit.isEmpty()) names = explicit;
            else {
                var possible = new ArrayList<String>();
                possible.add(context.packageName().isEmpty() ? name : context.packageName() + "." + name);
                for (String imported : context.imports()) if (imported.endsWith(".*")) possible.add(imported.substring(0, imported.length() - 1) + name);
                names = possible;
            }
        }
        for (String qualified : names) {
            if (sourceNames.contains(qualified)) return null;
            List<Artifact> declaring = owners.computeIfAbsent(qualified,
                    value -> artifacts.stream().filter(artifact -> hasClass(artifact.path(), value)).toList());
            if (declaring.isEmpty()) continue;
            if (declaring.size() > 1) return "Duplicate component binary '" + qualified + "' on the classpath";
            Artifact artifact = declaring.getFirst();
            if (artifact.failure() != null) return "Cannot use component '" + qualified + "' from " + artifact.path().getFileName() + ": " + artifact.failure();
            if (artifact.components().stream().noneMatch(component -> component.qualifiedName().equals(qualified)))
                return "Missing component descriptor for '" + qualified + "' in " + artifact.path().getFileName() + "; rebuild the producer with jx:library";
            return null;
        }
        return null;
    }
    private static boolean hasClass(Path artifact, String name) {
        if (!javax.lang.model.SourceVersion.isName(name)) return false;
        String relative = name.replace('.', '/') + ".class";
        if (Files.isDirectory(artifact)) return Files.isRegularFile(artifact.resolve(relative));
        try (var jar = new ZipFile(artifact.toFile())) { return jar.getEntry(relative) != null; }
        catch (IOException invalid) { return false; }
    }
}
