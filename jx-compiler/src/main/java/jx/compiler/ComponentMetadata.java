package jx.compiler;

import java.nio.file.Path;
import java.util.List;

/** Source-level component contract. Property ordering follows the nested Props record constructor.
 * descriptorOffset is -1 when an incomplete editor class has no closing brace for a generated member. */
public record ComponentMetadata(String qualifiedName, String simpleName, String packageName,
                                List<String> imports, boolean hasProps, List<Prop> props,
                                Path source, SourceSpan declaration, int descriptorOffset, Memo memo,
                                int propsTypeParameters) {
    /** How the component is memoized: not at all, by comparing each Props field, or by its own
     * {@code arePropsEqual} method. */
    public enum Memo { NONE, FIELDS, CUSTOM }
    public ComponentMetadata { imports = List.copyOf(imports); props = List.copyOf(props); }
    /** {@code optional} fields are marked {@code @OptionalProp} and may be omitted in markup. */
    public record Prop(String name, String javaType, SourceSpan declaration, boolean optional) {}
    /** The Props type as written in generated code: {@code Props}, or {@code Props<?, ?>} for a generic record. */
    public String propsType() {
        return propsTypeParameters == 0 ? "Props" : "Props<" + String.join(", ", java.util.Collections.nCopies(propsTypeParameters, "?")) + ">";
    }
}
