package jx.compiler;

import java.util.List;
import java.util.stream.Collectors;

/** Ordinary, checked Java members for the deliberately bounded class-authoring subset. */
final class ClassDescriptorSource {
    private ClassDescriptorSource() {}

    static String members(ComponentMetadata component) {
        ComponentMetadata.ClassSpec spec = component.classSpec();
        String state = spec.stateType();
        String owner = component.simpleName();
        StringBuilder out = new StringBuilder("\n    private static final class __JxMetadata {\n");
        schema(out, "Props", component.props(), "p", "PROPS");
        schema(out, state, spec.stateFields(), "s", "STATE");
        out.append("    }\n");
        if (spec.defaultProps()) propsInput(out,component.props());
        out.append("    /** Immutable partial state update; omitted fields retain their values. */\n")
                .append("    public static final class StateUpdate {\n")
                .append("        private final jx.StatePatch<").append(state).append("> value;\n")
                .append("        private StateUpdate(jx.StatePatch<").append(state).append("> value) { this.value = value; }\n");
        for (int index = 0; index < spec.stateFields().size(); index++) {
            ComponentMetadata.Prop field = spec.stateFields().get(index);
            out.append("        public StateUpdate with").append(Character.toUpperCase(field.name().charAt(0)))
                    .append(field.name().substring(1)).append('(').append(field.javaType())
                    .append(" value) { return new StateUpdate(this.value.set(__JxMetadata.s")
                    .append(index).append(", value)); }\n");
        }
        out.append("        /** Returns the checked patch without mutating this builder. */\n")
                .append("        public jx.StatePatch<").append(state).append("> build() { return value; }\n")
                .append("    }\n")
                .append("    /** Starts an empty immutable update; repeated fields fail explicitly. */\n")
                .append("    public static StateUpdate statePatch() { return new StateUpdate(__JxMetadata.STATE.patch()); }\n")
                .append("    /** Stable native React class identity generated from this declaration. */\n")
                .append("    public static final jx.ClassComponentType<Props, ").append(state).append(", ")
                .append(owner).append("> JX = jx.ClassComponentType.")
                .append(spec.kind() == ComponentMetadata.ClassKind.PURE_COMPONENT ? "pure" : "normal")
                .append(spec.contextValueType() == null ? "" : "WithContext")
                .append('(').append(Lowerer.quote(component.qualifiedName()))
                .append(", __JxMetadata.PROPS, __JxMetadata.STATE, ")
                .append(spec.contextValueType() == null ? "" : "contextType, ").append(owner)
                .append("::new, java.util.Set.of(")
                .append(spec.lifecycles().stream().sorted().map(value -> "jx.ClassComponentType.Lifecycle." + value.name())
                        .collect(Collectors.joining(", ")))
                .append("))");
        if (spec.derivedProps()) out.append(".withDerivedStateFromProps(").append(owner).append("::getDerivedStateFromProps)");
        if (spec.derivedError()) out.append(".withDerivedStateFromError(").append(owner).append("::getDerivedStateFromError)");
        if (spec.contextShouldUpdate()) out.append(".withContextShouldUpdate(contextType, ").append(owner).append("::shouldComponentUpdate)");
        if (spec.defaultProps()) out.append(".withDefaultProps(__jxDefaults())");
        out.append(";\n");
        return out.toString();
    }

    private static void propsInput(StringBuilder out, List<ComponentMetadata.Prop> fields) {
        out.append("    /** Immutable raw input: React resolves defaults before constructing Props. */\n")
                .append("    public static final class PropsInput {\n")
                .append("        private final jx.ClassProps<Props> value;\n")
                .append("        private PropsInput(jx.ClassProps<Props> value) { this.value = value; }\n");
        for (int i = 0; i < fields.size(); i++) {
            ComponentMetadata.Prop field = fields.get(i);
            String suffix = Character.toUpperCase(field.name().charAt(0)) + field.name().substring(1);
            out.append("        public PropsInput with").append(suffix).append('(').append(field.javaType())
                    .append(" value) { return new PropsInput(this.value.set(__JxMetadata.p").append(i).append(", value)); }\n")
                    .append("        public PropsInput undefined").append(suffix)
                    .append("() { return new PropsInput(value.undefined(__JxMetadata.p").append(i).append(")); }\n")
                    .append("        public PropsInput omit").append(suffix)
                    .append("() { return new PropsInput(value.omit(__JxMetadata.p").append(i).append(")); }\n");
        }
        out.append("        public jx.ClassProps<Props> build() { return value; }\n")
                .append("    }\n")
                .append("    /** Safe in defaultProps initialization: schema storage does not read JX. */\n")
                .append("    public static PropsInput propsInput() { return new PropsInput(jx.ClassProps.empty(__JxMetadata.PROPS)); }\n")
                .append("    private static jx.ClassProps<Props> __jxDefaults() {\n")
                .append("        var defaults = java.util.Objects.requireNonNull(defaultProps, \"Class defaultProps must be non-null\");\n")
                .append("        if (defaults.schema() != __JxMetadata.PROPS) throw new java.lang.IllegalArgumentException(\"Class defaultProps belongs to another schema\");\n");
        for (int i = 0; i < fields.size(); i++) if (fields.get(i).optional()) {
            out.append("        if (!(defaults.find(__JxMetadata.p").append(i)
                    .append(") instanceof jx.Presence.Present<?>)) throw new java.lang.IllegalArgumentException(")
                    .append(Lowerer.quote("Optional class property requires a defined native default: " + fields.get(i).name()))
                    .append(");\n");
        }
        out.append("        return defaults;\n    }\n");
    }

    private static void schema(StringBuilder out, String type, List<ComponentMetadata.Prop> fields,
                               String prefix, String name) {
        for (int index = 0; index < fields.size(); index++) {
            ComponentMetadata.Prop field = fields.get(index);
            out.append("        private static final jx.ClassSchema.Field<").append(type).append(", ")
                    .append(boxed(field.javaType())).append("> ").append(prefix).append(index)
                    .append(" = jx.ClassSchema.field(").append(Lowerer.quote(field.name())).append(", ")
                    .append(codec(field.javaType())).append(", ").append(type).append("::")
                    .append(field.name()).append(");\n");
        }
        out.append("        private static final jx.ClassSchema<").append(type).append("> ").append(name)
                .append(" = new jx.ClassSchema<>(java.util.List.of(");
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) out.append(", ");
            out.append(prefix).append(index);
        }
        out.append("), reader -> new ").append(type).append('(');
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) out.append(", ");
            out.append("reader.get(").append(prefix).append(index).append(')');
        }
        out.append("));\n");
    }

    private static String boxed(String type) {
        return switch (type) {
            case "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            case "double" -> "java.lang.Double";
            case "float" -> "java.lang.Float";
            case "boolean" -> "java.lang.Boolean";
            default -> type;
        };
    }

    private static String codec(String type) {
        String scalar = switch (type) {
            case "int", "Integer", "java.lang.Integer" -> "INTEGER";
            case "long", "Long", "java.lang.Long" -> "LONG";
            case "double", "Double", "java.lang.Double" -> "DOUBLE";
            case "float", "Float", "java.lang.Float" -> "FLOAT";
            case "boolean", "Boolean", "java.lang.Boolean" -> "BOOLEAN";
            case "String", "java.lang.String" -> "STRING";
            default -> null;
        };
        if (scalar != null) {
            boolean primitive = List.of("int", "long", "double", "float", "boolean").contains(type);
            return "jx.ClassSchema." + scalar + (primitive ? "" : ".nullable()");
        }
        if (type.equals("Object") || type.equals("java.lang.Object"))
            return "jx.ClassSchema.<java.lang.Object>reference(value -> value).nullable()";
        return "jx.ClassSchema.<" + type + ">reference(value -> { if (value instanceof " + type
                + " typed) return typed; throw new java.lang.IllegalArgumentException("
                + Lowerer.quote("Expected class field of type " + type) + "); }).nullable()";
    }
}
