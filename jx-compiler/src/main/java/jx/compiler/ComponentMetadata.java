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
    public enum Lifecycle { MOUNT, UPDATE, UNMOUNT, SNAPSHOT, SHOULD_UPDATE, CATCH,
        WILL_MOUNT, UNSAFE_WILL_MOUNT, WILL_RECEIVE_PROPS, UNSAFE_WILL_RECEIVE_PROPS,
        WILL_UPDATE, UNSAFE_WILL_UPDATE }
    /** The nested state record's source type and fields in constructor order. State fields
     * are required. A null contextValueType means no declared contextType; otherwise it is
     * the authored Java value type supplied to the constructor's typed Supplier. The two
     * derivation flags identify checked static methods, distinct from instance lifecycles.
     * contextShouldUpdate selects the typed three-argument overload over a two-argument override.
     * defaultProps identifies the checked native declaration; optional props then lower to
     * absent input fields and the generated descriptor requires a defined native default.
     * contextLifecycles identifies typed next-context overloads for receive/update callbacks.
     * Collections are immutable snapshots, independent of the parser's lists. */
    public record ClassSpec(ClassKind kind, String stateType, List<Prop> stateFields, Set<Lifecycle> lifecycles,
                            String contextValueType, boolean derivedProps, boolean derivedError, boolean contextShouldUpdate,
                            boolean defaultProps, Set<Lifecycle> contextLifecycles) {
        public ClassSpec {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(stateType);
            stateFields = List.copyOf(stateFields);
            lifecycles = Set.copyOf(lifecycles);
            contextLifecycles = Set.copyOf(contextLifecycles);
            if (!Set.of(Lifecycle.WILL_RECEIVE_PROPS, Lifecycle.UNSAFE_WILL_RECEIVE_PROPS,
                    Lifecycle.WILL_UPDATE, Lifecycle.UNSAFE_WILL_UPDATE).containsAll(contextLifecycles)
                    || !lifecycles.containsAll(contextLifecycles)
                    || !contextLifecycles.isEmpty() && contextValueType == null)
                throw new IllegalArgumentException("Typed legacy lifecycles require matching capabilities and contextType");
        }
        /** Compatibility constructor for classes without context or static derivation. */
        public ClassSpec(ClassKind kind, String stateType, List<Prop> stateFields, Set<Lifecycle> lifecycles) {
            this(kind, stateType, stateFields, lifecycles, null, false, false, false, false, Set.of());
        }
        /** Compatibility constructor for class metadata without a three-argument update policy. */
        public ClassSpec(ClassKind kind, String stateType, List<Prop> stateFields, Set<Lifecycle> lifecycles,
                         String contextValueType, boolean derivedProps, boolean derivedError) {
            this(kind, stateType, stateFields, lifecycles, contextValueType, derivedProps, derivedError, false, false, Set.of());
        }
        /** Compatibility constructor for class metadata without native defaults. */
        public ClassSpec(ClassKind kind, String stateType, List<Prop> stateFields, Set<Lifecycle> lifecycles,
                         String contextValueType, boolean derivedProps, boolean derivedError, boolean contextShouldUpdate) {
            this(kind,stateType,stateFields,lifecycles,contextValueType,derivedProps,derivedError,contextShouldUpdate,false,Set.of());
        }
        /** Compatibility constructor for metadata without typed legacy lifecycle overloads. */
        public ClassSpec(ClassKind kind, String stateType, List<Prop> stateFields, Set<Lifecycle> lifecycles,
                         String contextValueType, boolean derivedProps, boolean derivedError, boolean contextShouldUpdate,
                         boolean defaultProps) {
            this(kind,stateType,stateFields,lifecycles,contextValueType,derivedProps,derivedError,contextShouldUpdate,defaultProps,Set.of());
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
