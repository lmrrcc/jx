package jx.compiler;

import java.nio.file.Path;
import java.util.List;

/** Source-level component contract. Property ordering follows the nested Props record constructor.
 * descriptorOffset is -1 when an incomplete editor class has no closing brace for a generated member. */
public record ComponentMetadata(String qualifiedName, String simpleName, String packageName,
                                List<String> imports, boolean hasProps, List<Prop> props,
                                Path source, SourceSpan declaration, int descriptorOffset) {
    public ComponentMetadata { imports = List.copyOf(imports); props = List.copyOf(props); }
    public record Prop(String name, String javaType, SourceSpan declaration) {}
}
