package jx.compiler;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

/** Conservative structural compatibility for browser state checkpoints; never executes user code. */
final class HookSignatures {
    private static final Set<String> BUILT_INS = Set.of("useState", "useStateLazy", "useReducer", "useEffect",
            "useLayoutEffect", "useRef", "useImperativeHandle",
            "useTransition", "useDeferredValue", "useActionState", "useActionStateAsync", "useOptimistic", "useFormStatus", "useReducerLazy", "useInsertionEffect", "useEffectEvent", "useDebugValue", "useDomRef", "useMemo", "useCallback", "useContext", "useSyncExternalStore", "useId");
    private static final Set<String> STATE = Set.of("useState", "useStateLazy", "useReducer", "useReducerLazy");

    static Map<String, String> compute(List<GeneratedSource> generated, List<ComponentMetadata> components) {
        Map<String, String> result = new LinkedHashMap<>();
        for (GeneratedSource source : generated) {
            var collector = new javax.tools.DiagnosticCollector<JavaFileObject>();
            try (var manager = JavaModel.compiler().getStandardFileManager(collector, null, StandardCharsets.UTF_8)) {
                JavacTask task = (JavacTask) JavaModel.compiler().getTask(null, manager, collector,
                        List.of("-proc:none", "--release", "25"), null,
                        List.of(new JavaModel.MemorySource(source.relativePath().toString(), source.javaSource())));
                CompilationUnitTree unit = task.parse().iterator().next();
                String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
                for (Tree declaration : unit.getTypeDecls()) if (declaration instanceof ClassTree type) {
                    String name = packageName.isEmpty() ? type.getSimpleName().toString() : packageName + "." + type.getSimpleName();
                    ComponentMetadata component = components.stream().filter(c -> c.qualifiedName().equals(name)).findFirst().orElse(null);
                    if (component == null) continue;
                    Analyzer analyzer = new Analyzer(unit, type, component);
                    result.put(name, analyzer.signature());
                }
            } catch (IOException ex) { throw new IllegalStateException("Cannot compute hook compatibility", ex); }
        }
        return Map.copyOf(result);
    }

    private record Binding(String slot, String type) {
        static final Binding NONE = new Binding("", "");
        String describe() { return slot + ":" + type; }
    }

    private static final class Analyzer extends TreeScanner<Void, Binding> {
        private final CompilationUnitTree unit;
        private final ClassTree type;
        private final ComponentMetadata component;
        private final Map<String, List<MethodTree>> methods = new LinkedHashMap<>();
        private final Set<String> activeMethods = new HashSet<>();
        private final StringBuilder trace = new StringBuilder("jx-hooks-v1\n");
        private boolean safe = true;
        private int calls;
        private Binding returned = Binding.NONE;

        Analyzer(CompilationUnitTree unit, ClassTree type, ComponentMetadata component) {
            this.unit = unit; this.type = type; this.component = component;
            for (Tree member : type.getMembers()) if (member instanceof MethodTree method)
                methods.computeIfAbsent(method.getName().toString(), ignored -> new ArrayList<>()).add(method);
        }

        String signature() {
            trace.append(component.qualifiedName()).append('\n');
            component.props().forEach(prop -> trace.append("prop:").append(prop.name()).append(':').append(prop.javaType()).append('\n'));
            List<MethodTree> render = methods.getOrDefault("render", List.of());
            if (render.size() != 1) return "";
            expand(render.getFirst(), new Binding("render", render.getFirst().getReturnType().toString()));
            if (!safe) return "";
            try {
                return "jx-hooks-v1:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(trace.toString().getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        }

        private void expand(MethodTree method, Binding binding) {
            String key = method.getName() + "/" + method.getParameters().size();
            if (activeMethods.size() >= 64 || !activeMethods.add(key)) { safe = false; return; }
            Binding before = returned;
            returned = new Binding(binding.slot() + "/return", method.getReturnType().toString());
            trace.append("method:").append(key).append(':').append(method.getReturnType()).append(':').append(binding.describe()).append('\n');
            for (VariableTree parameter : method.getParameters())
                trace.append("parameter:").append(parameter.getName()).append(':').append(parameter.getType()).append('\n');
            scan(method.getBody(), Binding.NONE);
            returned = before;
            activeMethods.remove(key);
        }

        @Override public Void visitVariable(VariableTree variable, Binding ignored) {
            scan(variable.getInitializer(), new Binding(variable.getName().toString(),
                    variable.getType() == null ? "var" : variable.getType().toString()));
            return null;
        }

        @Override public Void visitReturn(ReturnTree tree, Binding ignored) {
            scan(tree.getExpression(), returned); return null;
        }

        @Override public Void visitAssignment(AssignmentTree tree, Binding ignored) {
            scan(tree.getExpression(), new Binding("assign:" + tree.getVariable(), "")); return null;
        }

        @Override public Void visitMethodInvocation(MethodInvocationTree invocation, Binding binding) {
            if (!safe) return null;
            // Java evaluates the receiver/arguments before invoking the enclosing hook.
            scan(invocation.getMethodSelect(), binding);
            for (ExpressionTree argument : invocation.getArguments()) scan(argument, binding);
            String selected = invocation.getMethodSelect().toString();
            String name = selected.substring(selected.lastIndexOf('.') + 1);
            String receiver = selected.contains(".") ? selected.substring(0, selected.lastIndexOf('.')) : "";
            List<MethodTree> local = local(receiver) ? methods.getOrDefault(name, List.of()).stream()
                    .filter(method -> method.getParameters().size() == invocation.getArguments().size()
                            && method.getModifiers().getFlags().contains(Modifier.STATIC)).toList() : List.of();
            if (!hookName(name)) {
                // Hooks hidden in a non-hook helper cannot be assigned a reliable public hook boundary.
                if (local.stream().anyMatch(method -> containsHook(method.getBody()))) safe = false;
                return null;
            }
            if (++calls > 1024) { safe = false; return null; }
            if (!local.isEmpty()) {
                if (local.size() != 1) { safe = false; return null; }
                trace.append("custom:").append(name).append(':').append(binding.describe()).append(':')
                        .append(invocation.getArguments()).append('\n');
                expand(local.getFirst(), binding);
            } else if (builtIn(receiver, name)) {
                trace.append("hook:").append(name).append(':').append(binding.describe()).append('\n');
                if (STATE.contains(name)) {
                    if (binding.slot().isEmpty()) { safe = false; return null; }
                    // Initializer/codec changes reset conservatively, including var inference and custom schemas.
                    trace.append("state-inputs:").append(invocation.getTypeArguments()).append(':')
                            .append(invocation.getArguments()).append('\n');
                }
            } else safe = false; // External, dynamic, inherited, or otherwise unresolved custom hook.
            return null;
        }

        private boolean local(String receiver) {
            return receiver.isEmpty() || receiver.equals(type.getSimpleName().toString()) || receiver.equals(component.qualifiedName());
        }

        private boolean builtIn(String receiver, String name) {
            if (!BUILT_INS.contains(name)) return false;
            if (receiver.equals("jx.Hooks")) return true;
            if (receiver.equals("Hooks")) {
                return component.packageName().equals("jx") || unit.getImports().stream().anyMatch(imported ->
                        !imported.isStatic() && (imported.getQualifiedIdentifier().toString().equals("jx.Hooks")
                                || imported.getQualifiedIdentifier().toString().equals("jx.*")));
            }
            return receiver.isEmpty() && unit.getImports().stream().anyMatch(imported -> imported.isStatic()
                    && (imported.getQualifiedIdentifier().toString().equals("jx.Hooks." + name)
                            || imported.getQualifiedIdentifier().toString().equals("jx.Hooks.*")));
        }

        @Override public Void visitLambdaExpression(LambdaExpressionTree tree, Binding binding) { return null; }
        @Override public Void visitClass(ClassTree tree, Binding binding) { return null; }
    }

    private static boolean hookName(String name) {
        return name.startsWith("use") && name.length() > 3 && Character.isUpperCase(name.charAt(3));
    }

    private static boolean containsHook(Tree tree) {
        return Boolean.TRUE.equals(new TreeScanner<Boolean, Void>() {
            @Override public Boolean visitMethodInvocation(MethodInvocationTree invocation, Void ignored) {
                String selected = invocation.getMethodSelect().toString();
                return hookName(selected.substring(selected.lastIndexOf('.') + 1)) || Boolean.TRUE.equals(super.visitMethodInvocation(invocation, ignored));
            }
            @Override public Boolean reduce(Boolean first, Boolean second) { return Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second); }
        }.scan(tree, null));
    }
}

