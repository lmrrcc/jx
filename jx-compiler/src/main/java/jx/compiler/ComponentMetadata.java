package jx.compiler;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Source-level component contract. Property ordering follows the nested Props record constructor.
 * descriptorOffset is -1 when an incomplete editor class has no closing brace for a generated member.
 * A null {@code classSpec} identifies a functional component; a non-null value describes a
 * native React class component's explicit props/state schema and lifecycle capabilities. */
public record ComponentMetadata(String qualifiedName, String simpleName, String packageName,
                                List<String> imports, boolean hasProps, List<Prop> props,
                                Path source, SourceSpan declaration, int descriptorOffset, Memo memo,
                                int propsTypeParameters, ClassSpec classSpec) {
    /** How the component is memoized: not at all, by comparing each Props field, or by its own
     * {@code arePropsEqual} method. */
    public enum Memo { NONE, FIELDS, CUSTOM }
    /** Native React superclass used by the generated class descriptor. */
    public enum ClassKind { COMPONENT, PURE_COMPONENT }
    /** Authored instance overrides that the renderer must dispatch. */
    public enum Lifecycle { MOUNT, UPDATE, UNMOUNT, SNAPSHOT, SHOULD_UPDATE }
    /** The nested state record's source type and fields in constructor order. State fields
     * are required. Collections are immutable snapshots, independent of the parser's lists. */
    public record ClassSpec(ClassKind kind, String stateType, List<Prop> stateFields, Set<Lifecycle> lifecycles) {
        public ClassSpec {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(stateType);
            stateFields = List.copyOf(stateFields);
            lifecycles = Set.copyOf(lifecycles);
        }
    }
    public ComponentMetadata { imports = List.copyOf(imports); props = List.copyOf(props); }
    /** Compatibility constructor for callers describing functional components. */
    public ComponentMetadata(String qualifiedName, String simpleName, String packageName,
                             List<String> imports, boolean hasProps, List<Prop> props,
                             Path source, SourceSpan declaration, int descriptorOffset, Memo memo,
                             int propsTypeParameters) {
        this(qualifiedName, simpleName, packageName, imports, hasProps, props, source, declaration,
                descriptorOffset, memo, propsTypeParameters, null);
    }
    /** {@code optional} fields are marked {@code @OptionalProp} and may be omitted in markup. */
    public record Prop(String name, String javaType, SourceSpan declaration, boolean optional) {}
    /** The Props type as written in generated code: {@code Props}, or {@code Props<?, ?>} for a generic record. */
    public String propsType() {
        return propsTypeParameters == 0 ? "Props" : "Props<" + String.join(", ", java.util.Collections.nCopies(propsTypeParameters, "?")) + ">";
    }
}
