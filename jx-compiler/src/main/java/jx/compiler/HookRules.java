package jx.compiler;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.util.List;
import javax.tools.JavaFileObject;

/** Conservative syntax checks, not a call-graph or arbitrary side-effect proof. */
final class HookRules {
    static void check(GeneratedSource source, List<ComponentMetadata> components, List<Diagnostic> diagnostics) {
        var collector = new javax.tools.DiagnosticCollector<JavaFileObject>();
        try (var manager = JavaModel.compiler().getStandardFileManager(collector, null, java.nio.charset.StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) JavaModel.compiler().getTask(null, manager, collector,
                    List.of("-proc:none", "--release", "25"), null,
                    List.of(new JavaModel.MemorySource(source.relativePath().toString(), source.javaSource())));
            CompilationUnitTree unit = task.parse().iterator().next();
            var positions = Trees.instance(task).getSourcePositions();
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            new TreeScanner<Void, Integer>() {
                private boolean relevantMethod;
                private boolean nativeClass;
                private int classDepth;
                @Override public Void visitBlock(BlockTree tree, Integer level) {
                    boolean priorReturn = false;
                    for (StatementTree statement : tree.getStatements()) {
                        scan(statement, priorReturn ? level + 1 : level);
                        if (!(statement instanceof ReturnTree) && containsReturn(statement)) priorReturn = true;
                    }
                    return null;
                }
                @Override public Void visitMethod(MethodTree tree, Integer level) {
                    boolean previous = relevantMethod;
                    relevantMethod = tree.getName().contentEquals("render") || hookName(tree.getName().toString());
                    scan(tree.getBody(), 0);
                    relevantMethod = previous;
                    return null;
                }
                @Override public Void visitMethodInvocation(MethodInvocationTree tree, Integer level) {
                    String called = tree.getMethodSelect().toString();
                    String name = called.substring(called.lastIndexOf('.') + 1);
                    if (nativeClass && hookName(name)) {
                        int offset = source.sourceMap().sourceOffset(positions.getStartPosition(unit, tree));
                        diagnostics.add(Diagnostic.error(source.source(), "JX201",
                                "Hook '" + name + "' is not allowed in a native class component; use class state and lifecycle callbacks",
                                new SourceSpan(offset, Math.min(offset + name.length(), source.source().content().length()))));
                    } else if (relevantMethod && level > 0 && hookName(name)) {
                        int offset = source.sourceMap().sourceOffset(positions.getStartPosition(unit, tree));
                        diagnostics.add(Diagnostic.error(source.source(), "JX200",
                                "Hook '" + name + "' must be called at the top level of render or a custom hook, outside branches, loops, try blocks and callbacks",
                                new SourceSpan(offset, Math.min(offset + name.length(), source.source().content().length()))));
                    }
                    return super.visitMethodInvocation(tree, level);
                }
                @Override public Void visitIf(IfTree tree, Integer level) {
                    scan(tree.getCondition(), level); scan(tree.getThenStatement(), level + 1); scan(tree.getElseStatement(), level + 1); return null;
                }
                @Override public Void visitConditionalExpression(ConditionalExpressionTree tree, Integer level) {
                    scan(tree.getCondition(), level); scan(tree.getTrueExpression(), level + 1); scan(tree.getFalseExpression(), level + 1); return null;
                }
                @Override public Void visitBinary(BinaryTree tree, Integer level) {
                    scan(tree.getLeftOperand(), level);
                    scan(tree.getRightOperand(), tree.getKind() == Tree.Kind.CONDITIONAL_AND || tree.getKind() == Tree.Kind.CONDITIONAL_OR ? level + 1 : level);
                    return null;
                }
                @Override public Void visitForLoop(ForLoopTree tree, Integer level) { return super.visitForLoop(tree, level + 1); }
                @Override public Void visitEnhancedForLoop(EnhancedForLoopTree tree, Integer level) { return super.visitEnhancedForLoop(tree, level + 1); }
                @Override public Void visitWhileLoop(WhileLoopTree tree, Integer level) { return super.visitWhileLoop(tree, level + 1); }
                @Override public Void visitDoWhileLoop(DoWhileLoopTree tree, Integer level) { return super.visitDoWhileLoop(tree, level + 1); }
                @Override public Void visitLambdaExpression(LambdaExpressionTree tree, Integer level) { return super.visitLambdaExpression(tree, level + 1); }
                @Override public Void visitTry(TryTree tree, Integer level) { return super.visitTry(tree, level + 1); }
                @Override public Void visitSwitch(SwitchTree tree, Integer level) { return super.visitSwitch(tree, level + 1); }
                @Override public Void visitSwitchExpression(SwitchExpressionTree tree, Integer level) { return super.visitSwitchExpression(tree, level + 1); }
                @Override public Void visitClass(ClassTree tree, Integer level) {
                    // Each method resets lexical placement; a local class never shares its parent's hook sequence.
                    boolean previous = nativeClass;
                    String name = packageName.isEmpty() ? tree.getSimpleName().toString() : packageName + "." + tree.getSimpleName();
                    nativeClass = classDepth == 0 && components.stream().anyMatch(component ->
                            component.qualifiedName().equals(name) && component.classSpec() != null);
                    classDepth++;
                    try { return super.visitClass(tree, level == null ? 0 : level); }
                    finally { classDepth--; nativeClass = previous; }
                }
            }.scan(unit, 0);
        } catch (IOException ex) { throw new IllegalStateException("Cannot inspect hook placement", ex); }
    }
    private static boolean hookName(String name) {
        return name.startsWith("use") && name.length() > 3 && Character.isUpperCase(name.charAt(3));
    }
    private static boolean containsReturn(Tree tree) {
        return Boolean.TRUE.equals(new TreeScanner<Boolean, Void>() {
            @Override public Boolean visitReturn(ReturnTree value, Void unused) { return true; }
            @Override public Boolean visitLambdaExpression(LambdaExpressionTree value, Void unused) { return false; }
            @Override public Boolean visitClass(ClassTree value, Void unused) { return false; }
            @Override public Boolean reduce(Boolean left, Boolean right) { return Boolean.TRUE.equals(left) || Boolean.TRUE.equals(right); }
        }.scan(tree, null));
    }
}
