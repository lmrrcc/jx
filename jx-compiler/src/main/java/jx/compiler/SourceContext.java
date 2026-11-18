package jx.compiler;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Package/import context for name lookup, including incomplete editor snapshots. */
public record SourceContext(Path source, String packageName, List<String> imports) {
    public SourceContext {
        source = Objects.requireNonNull(source).normalize();
        Objects.requireNonNull(packageName);
        imports = List.copyOf(imports);
    }
}
