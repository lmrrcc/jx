package jx.compiler;

import com.sun.source.tree.*;
import com.sun.source.util.SourcePositions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;
import jx.compiler.Syntax.ParsedSource;

/** Declaration-only validation for the native class authoring subset. Java attribution still
 * checks assignability, constructor bodies and the concrete override types after lowering. */
final class ClassModel {
    private static final Set<String> GENERATED = Set.of("JX", "StateUpdate", "statePatch", "__JxMetadata");
    private static final Set<String> RESERVED_FIELDS = Set.of(
            "key", "ref", "__self", "__source", "__proto__", "prototype", "constructor");
    private static final Set<String> JAVA_LANG_FIELDS = Set.of("String", "Integer", "Long", "Float", "Double",
            "Boolean", "Byte", "Short", "Character", "Object", "Void");
    private static final Set<String> UNSUPPORTED_LIFECYCLES = Set.of("context", "contextTypes",
            "childContextTypes", "getChildContext", "componentWillMount", "componentWillReceiveProps",
            "componentWillUpdate", "defaultProps");

    private final ParsedSource parsed;
    private final ClassTree owner;
    private final ComponentMetadata.ClassKind kind;
    private final String packageName;
    private final List<String> imports;
    private final CompilationUnitTree unit;
    private final SourcePositions positions;
    private final StringBuilder shadow;
    private final List<Diagnostic> diagnostics;

    private ClassModel(ParsedSource parsed, ClassTree owner, ComponentMetadata.ClassKind kind,
                       String packageName, List<String> imports, CompilationUnitTree unit,
                       SourcePositions positions, StringBuilder shadow, List<Diagnostic> diagnostics) {
        this.parsed = parsed; this.owner = owner; this.kind = kind; this.packageName = packageName;
        this.imports = imports; this.unit = unit; this.positions = positions;
        this.shadow = shadow; this.diagnostics = diagnostics;
    }

    static ComponentMetadata.ClassKind kind(ClassTree type, String packageName, List<String> imports, CompilationUnitTree unit) {
        Tree base = unannotated(type.getExtendsClause());
        if (base instanceof ParameterizedTypeTree parameterized) base = unannotated(parameterized.getType());
        if (base == null) return null;
        String name = base.toString();
        // A nested declaration shadows an on-demand import just as a top-level declaration does.
        if (type.getMembers().stream().anyMatch(member -> member instanceof ClassTree nested
                && nested.getSimpleName().contentEquals(name))) return null;
        if (JavaModel.isJxAnnotation("ClassComponent", name, packageName, imports, unit))
            return ComponentMetadata.ClassKind.COMPONENT;
        if (JavaModel.isJxAnnotation("PureComponent", name, packageName, imports, unit))
            return ComponentMetadata.ClassKind.PURE_COMPONENT;
        return null;
    }

    static ComponentMetadata read(ParsedSource parsed, ClassTree owner, ComponentMetadata.ClassKind kind, boolean memoized,
                                  String packageName, List<String> imports, CompilationUnitTree unit,
                                  SourcePositions positions, StringBuilder shadow, List<Diagnostic> diagnostics) {
        return new ClassModel(parsed, owner, kind, packageName, imports, unit, positions, shadow, diagnostics).read(memoized);
    }

    private ComponentMetadata read(boolean memoized) {
        int before = diagnostics.size();
        if (owner.getKind() != Tree.Kind.CLASS || !owner.getModifiers().getFlags().contains(Modifier.PUBLIC)
                || owner.getModifiers().getFlags().contains(Modifier.ABSTRACT) || !owner.getTypeParameters().isEmpty())
            error("JX110", "A native class component must be a public, concrete, non-generic top-level class", owner);
        if (memoized) error("JX110", "@Memo is for functional components; extend PureComponent for shallow class comparison", owner);
        Tree base = unannotated(owner.getExtendsClause());
        if (!(base instanceof ParameterizedTypeTree parameterized) || parameterized.getTypeArguments().size() != 3) {
            error("JX110", "A native class component must directly extend ClassComponent<Props, State, Snapshot> or PureComponent<Props, State, Snapshot>", owner);
            return null;
        }
        List<? extends Tree> arguments = parameterized.getTypeArguments();
        ClassTree props = nestedRecord(arguments.get(0), "Props", "props");
        ClassTree state = nestedRecord(arguments.get(1), null, "state");
        for (Tree member : owner.getMembers()) {
            String name = memberName(member);
            if (GENERATED.contains(name)) error("JX110", "'" + name + "' is reserved for generated class component members", member);
            if (UNSUPPORTED_LIFECYCLES.contains(name) || name.startsWith("UNSAFE_"))
                error("JX111", "Native class authoring does not yet support React lifecycle member '" + name + "'", member);
        }
        List<MethodTree> renders = methods("render");
        if (renders.size() != 1 || !instanceMethod(renders.getFirst(), 0, true)
                || renders.getFirst().getBody() == null)
            error("JX110", "A native class component requires exactly one public instance render() method without type parameters", owner);
        Tree contextValue = contextValue();
        boolean constructor = owner.getMembers().stream().filter(MethodTree.class::isInstance).map(MethodTree.class::cast)
                .anyMatch(method -> method.getReturnType() == null && method.getModifiers().getFlags().contains(Modifier.PUBLIC)
                        && method.getTypeParameters().isEmpty() && method.getParameters().size() == (contextValue == null ? 1 : 2)
                        && sameType(method.getParameters().getFirst().getType(), arguments.getFirst())
                        && (contextValue == null || genericType(method.getParameters().get(1).getType(),
                                "java.util.function.Supplier", contextValue)));
        if (!constructor) error("JX110", "A native class component requires a public constructor taking its Props record"
                + (contextValue == null ? "" : " and Supplier<" + spelling(contextValue) + "> for contextType"), owner);
        List<ComponentMetadata.Prop> properties = fields(props, true);
        List<ComponentMetadata.Prop> stateFields = fields(state, false);
        Set<ComponentMetadata.Lifecycle> lifecycles = lifecycles(arguments);
        boolean derivedProps = derivation("getDerivedStateFromProps", arguments.subList(0, 2), arguments.get(1), false);
        boolean derivedError = derivation("getDerivedStateFromError", List.of(), arguments.get(1), true);
        if (diagnostics.size() != before) return null;
        SourceSpan declaration = span(owner);
        int descriptorOffset = declaration.end() - 1;
        if (descriptorOffset < declaration.start() || shadow.charAt(descriptorOffset) != '}') descriptorOffset = -1;
        String name = owner.getSimpleName().toString();
        return new ComponentMetadata(qualified(name), name, packageName, imports, true, properties,
                parsed.source().path(), declaration, descriptorOffset, ComponentMetadata.Memo.NONE, 0,
                new ComponentMetadata.ClassSpec(kind, state.getSimpleName().toString(), stateFields, lifecycles,
                        contextValue == null ? null : spelling(contextValue), derivedProps, derivedError));
    }

    private ClassTree nestedRecord(Tree argument, String requiredName, String role) {
        Tree type = unannotated(argument);
        String name = type.toString();
        String ownerPrefix = owner.getSimpleName() + ".";
        if (name.startsWith(ownerPrefix)) name = name.substring(ownerPrefix.length());
        String recordName = name;
        ClassTree record = owner.getMembers().stream().filter(ClassTree.class::isInstance).map(ClassTree.class::cast)
                .filter(member -> member.getSimpleName().contentEquals(recordName)).findFirst().orElse(null);
        if ((requiredName != null && !requiredName.equals(recordName)) || record == null
                || record.getKind() != Tree.Kind.RECORD || !record.getModifiers().getFlags().contains(Modifier.PUBLIC)
                || !record.getTypeParameters().isEmpty()) {
            error("JX110", "The class " + role + " type must name a public, non-generic nested record"
                    + (requiredName == null ? "" : " " + requiredName), argument);
            return null;
        }
        return record;
    }

    private List<ComponentMetadata.Prop> fields(ClassTree record, boolean props) {
        if (record == null) return List.of();
        List<ComponentMetadata.Prop> fields = new ArrayList<>();
        Set<String> updateMethods = new HashSet<>();
        for (Tree member : record.getMembers()) if (member instanceof VariableTree variable
                && !variable.getModifiers().getFlags().contains(Modifier.STATIC)) {
            String name = variable.getName().toString();
            if (!name.matches("[A-Za-z_$][A-Za-z0-9_$]*") || RESERVED_FIELDS.contains(name) || name.startsWith("__jx"))
                error("JX110", "Record field '" + name + "' must be an ASCII Java identifier not reserved for native class identity or renderer metadata", variable);
            if (!props && !name.isEmpty()) {
                String setter = "with" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                if (!updateMethods.add(setter)) error("JX110", "State field '" + name
                        + "' collides with another field's generated update method '" + setter + "'", variable);
            }
            String javaType = spelling(variable.getType());
            if (!fieldType(variable.getType())) error("JX110", "Class field '" + name + "' uses unsupported type " + javaType
                    + "; use int, long, float, double, boolean, their boxed types, String, or a reifiable reference/array type", variable);
            boolean optional = props && variable.getModifiers().getAnnotations().stream().anyMatch(annotation ->
                    JavaModel.isJxAnnotation("OptionalProp", annotation.getAnnotationType().toString(), packageName, imports, unit));
            fields.add(new ComponentMetadata.Prop(name, javaType, span(variable), optional));
        }
        return fields;
    }

    private boolean fieldType(Tree tree) {
        tree = unannotated(tree);
        if (tree instanceof ArrayTypeTree array) return reifiable(array.getType());
        if (tree instanceof PrimitiveTypeTree)
            return Set.of("int", "long", "float", "double", "boolean").contains(tree.toString());
        String type = spelling(tree);
        return reifiable(tree) && !Set.of("Byte", "Short", "Character", "java.lang.Byte", "java.lang.Short", "java.lang.Character").contains(type);
    }

    private static boolean reifiable(Tree tree) {
        tree = unannotated(tree);
        return switch (tree) {
            case ArrayTypeTree array -> reifiable(array.getType());
            case PrimitiveTypeTree primitive -> !primitive.toString().equals("void");
            case IdentifierTree ignored -> true;
            case MemberSelectTree select -> reifiable(select.getExpression());
            default -> false;
        };
    }

    private Set<ComponentMetadata.Lifecycle> lifecycles(List<? extends Tree> arguments) {
        Set<ComponentMetadata.Lifecycle> result = EnumSet.noneOf(ComponentMetadata.Lifecycle.class);
        lifecycle("componentDidMount", ComponentMetadata.Lifecycle.MOUNT, List.of(), "void", result);
        lifecycle("componentWillUnmount", ComponentMetadata.Lifecycle.UNMOUNT, List.of(), "void", result);
        lifecycle("componentDidUpdate", ComponentMetadata.Lifecycle.UPDATE, arguments, "void", result);
        lifecycle("getSnapshotBeforeUpdate", ComponentMetadata.Lifecycle.SNAPSHOT,
                arguments.subList(0, 2), canonical(arguments.get(2)), result);
        lifecycle("shouldComponentUpdate", ComponentMetadata.Lifecycle.SHOULD_UPDATE,
                arguments.subList(0, 2), "boolean", result);
        List<MethodTree> catches = methods("componentDidCatch");
        if (!catches.isEmpty()) {
            MethodTree method = catches.getFirst();
            boolean valid = catches.size() == 1 && instanceMethod(method, 2, false) && method.getBody() != null
                    && canonical(method.getReturnType()).equals("void")
                    && namedType(method.getParameters().get(0).getType(), "java.lang.Throwable")
                    && namedType(method.getParameters().get(1).getType(), "jx.ComponentErrorInfo");
            if (!valid) error("JX111", "componentDidCatch must be one public or protected instance void method taking Throwable and ComponentErrorInfo", method);
            else result.add(ComponentMetadata.Lifecycle.CATCH);
        }
        if (result.contains(ComponentMetadata.Lifecycle.SNAPSHOT) && !result.contains(ComponentMetadata.Lifecycle.UPDATE))
            error("JX111", "getSnapshotBeforeUpdate requires componentDidUpdate to consume its snapshot", owner);
        if (kind == ComponentMetadata.ClassKind.PURE_COMPONENT && result.contains(ComponentMetadata.Lifecycle.SHOULD_UPDATE))
            error("JX111", "PureComponent supplies shallow comparison; do not override shouldComponentUpdate", owner);
        return result;
    }

    private Tree contextValue() {
        List<? extends Tree> members = owner.getMembers().stream().filter(member -> memberName(member).equals("contextType")).toList();
        if (members.isEmpty()) return null;
        Tree member = members.getFirst();
        if (members.size() == 1 && member instanceof VariableTree field
                && field.getModifiers().getFlags().containsAll(Set.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL))
                && field.getInitializer() != null && unannotated(field.getType()) instanceof ParameterizedTypeTree type
                && namedType(type.getType(), "jx.Context") && type.getTypeArguments().size() == 1
                && !(unannotated(type.getTypeArguments().getFirst()) instanceof WildcardTree))
            return type.getTypeArguments().getFirst();
        error("JX111", "contextType must be one initialized public static final jx.Context<T> field with a concrete value type", member);
        return null;
    }

    private boolean derivation(String name, List<? extends Tree> parameters, Tree state, boolean fromError) {
        List<MethodTree> methods = methods(name);
        if (methods.isEmpty()) return false;
        MethodTree method = methods.getFirst();
        Set<Modifier> flags = method.getModifiers().getFlags();
        boolean valid = methods.size() == 1 && flags.containsAll(Set.of(Modifier.PUBLIC, Modifier.STATIC))
                && method.getTypeParameters().isEmpty() && method.getBody() != null
                && method.getParameters().size() == (fromError ? 1 : parameters.size())
                && genericType(method.getReturnType(), "jx.StatePatch", state);
        if (valid && fromError) valid = namedType(method.getParameters().getFirst().getType(), "java.lang.Throwable");
        else if (valid) for (int index = 0; index < parameters.size(); index++)
            valid &= sameType(method.getParameters().get(index).getType(), parameters.get(index));
        if (!valid) error("JX111", name + " must be one public static StatePatch<" + spelling(state) + "> method taking "
                + (fromError ? "Throwable" : "the declared Props and state types"), method);
        return valid;
    }

    private boolean genericType(Tree tree, String name, Tree argument) {
        return unannotated(tree) instanceof ParameterizedTypeTree type && namedType(type.getType(), name)
                && type.getTypeArguments().size() == 1 && sameType(type.getTypeArguments().getFirst(), argument);
    }

    private boolean namedType(Tree tree, String qualifiedName) {
        if (tree == null) return false;
        String name = unannotated(tree).toString();
        if (name.equals(qualifiedName)) return true;
        int separator = qualifiedName.lastIndexOf('.');
        String simple = qualifiedName.substring(separator + 1);
        if (!name.equals(simple)) return false;
        if (owner.getMembers().stream().anyMatch(member -> member instanceof ClassTree nested
                && nested.getSimpleName().contentEquals(simple))
                || unit.getTypeDecls().stream().anyMatch(member -> member instanceof ClassTree type
                && type.getSimpleName().contentEquals(simple))) return false;
        List<String> explicit = imports.stream().filter(value -> value.endsWith("." + simple)).toList();
        if (!explicit.isEmpty()) return explicit.size() == 1 && explicit.getFirst().equals(qualifiedName);
        String targetPackage = qualifiedName.substring(0, separator);
        return targetPackage.equals("java.lang") || packageName.equals(targetPackage) || imports.contains(targetPackage + ".*");
    }

    private void lifecycle(String name, ComponentMetadata.Lifecycle capability, List<? extends Tree> parameters,
                           String returnType, Set<ComponentMetadata.Lifecycle> result) {
        List<MethodTree> methods = methods(name);
        if (methods.isEmpty()) return;
        MethodTree method = methods.getFirst();
        boolean valid = methods.size() == 1 && instanceMethod(method, parameters.size(), false)
                && method.getBody() != null && (capability == ComponentMetadata.Lifecycle.SNAPSHOT
                    // Java permits a covariant snapshot return. The lowered subclass is still
                    // attributed by javac, which checks that it actually overrides the base.
                    ? method.getReturnType() != null && !(unannotated(method.getReturnType()) instanceof PrimitiveTypeTree)
                    : canonical(method.getReturnType()).equals(returnType));
        if (valid) for (int index = 0; index < parameters.size(); index++)
            valid &= sameType(method.getParameters().get(index).getType(), parameters.get(index));
        if (!valid) error("JX111", "'" + name + "' must be one public or protected instance override with the declared Props, state and snapshot types", method);
        else result.add(capability);
    }

    private static boolean instanceMethod(MethodTree method, int arity, boolean publicOnly) {
        Set<Modifier> flags = method.getModifiers().getFlags();
        return !flags.contains(Modifier.STATIC) && !flags.contains(Modifier.ABSTRACT)
                && (flags.contains(Modifier.PUBLIC) || (!publicOnly && flags.contains(Modifier.PROTECTED)))
                && method.getTypeParameters().isEmpty() && method.getParameters().size() == arity;
    }

    private List<MethodTree> methods(String name) {
        return owner.getMembers().stream().filter(MethodTree.class::isInstance).map(MethodTree.class::cast)
                .filter(method -> method.getName().contentEquals(name)).toList();
    }

    private static String memberName(Tree member) {
        return switch (member) {
            case VariableTree variable -> variable.getName().toString();
            case MethodTree method -> method.getName().toString();
            case ClassTree type -> type.getSimpleName().toString();
            default -> "";
        };
    }

    private boolean sameType(Tree first, Tree second) { return canonical(first).equals(canonical(second)); }

    private String canonical(Tree type) {
        if (type == null) return "";
        type = unannotated(type);
        return switch (type) {
            case ArrayTypeTree array -> canonical(array.getType()) + "[]";
            case ParameterizedTypeTree parameterized -> canonical(parameterized.getType()) + "<"
                    + String.join(", ", parameterized.getTypeArguments().stream().map(this::canonical).toList()) + ">";
            case IdentifierTree identifier -> canonicalName(identifier.getName().toString());
            case MemberSelectTree select -> {
                String value = spelling(select);
                String localPrefix = owner.getSimpleName() + ".";
                yield value.startsWith(localPrefix) ? qualified(value) : value;
            }
            default -> type.toString();
        };
    }

    private String canonicalName(String name) {
        if (owner.getMembers().stream().anyMatch(member -> member instanceof ClassTree nested && nested.getSimpleName().contentEquals(name)))
            return qualified(owner.getSimpleName() + "." + name);
        if (unit.getTypeDecls().stream().anyMatch(member -> member instanceof ClassTree type && type.getSimpleName().contentEquals(name)))
            return qualified(name);
        List<String> explicit = imports.stream().filter(value -> value.endsWith("." + name)).toList();
        if (explicit.size() == 1) return explicit.getFirst();
        return JAVA_LANG_FIELDS.contains(name) ? "java.lang." + name : name;
    }

    /** Preserve the underlying Java type, excluding type-use annotations. Qualify explicit
     * shadows of java.lang scalar names so the generator cannot select a scalar codec for them. */
    private String spelling(Tree tree) {
        tree = unannotated(tree);
        return switch (tree) {
            case ArrayTypeTree array -> spelling(array.getType()) + "[]";
            case ParameterizedTypeTree parameterized -> spelling(parameterized.getType()) + "<"
                    + String.join(", ", parameterized.getTypeArguments().stream().map(this::spelling).toList()) + ">";
            case MemberSelectTree select -> spelling(select.getExpression()) + "." + select.getIdentifier();
            case IdentifierTree identifier -> fieldName(identifier.getName().toString());
            default -> tree.toString();
        };
    }

    private String fieldName(String name) {
        if (!JAVA_LANG_FIELDS.contains(name)) return name;
        if (owner.getMembers().stream().anyMatch(member -> member instanceof ClassTree nested && nested.getSimpleName().contentEquals(name)))
            return qualified(owner.getSimpleName() + "." + name);
        if (unit.getTypeDecls().stream().anyMatch(member -> member instanceof ClassTree type && type.getSimpleName().contentEquals(name)))
            return qualified(name);
        List<String> explicit = imports.stream().filter(value -> value.endsWith("." + name)).toList();
        return explicit.size() == 1 ? explicit.getFirst() : name;
    }

    private String qualified(String name) { return packageName.isEmpty() ? name : packageName + "." + name; }
    private SourceSpan span(Tree tree) { return JavaModel.span(positions, unit, tree, shadow.length()); }
    private void error(String code, String message, Tree tree) { diagnostics.add(Diagnostic.error(parsed.source(), code, message, span(tree))); }
    private static Tree unannotated(Tree tree) {
        while (tree instanceof AnnotatedTypeTree annotated) tree = annotated.getUnderlyingType();
        return tree;
    }
}
