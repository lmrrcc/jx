package jx.intellij;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jx.compiler.JxCompiler;
import jx.compiler.SourceFile;
import jx.compiler.Syntax;
import org.jetbrains.annotations.NotNull;

/** Source-only folds: generated expressions and synthetic component descriptors never appear. */
public final class JxFoldingBuilder extends FoldingBuilderEx {
    @Override public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        if (!(root instanceof PsiFile file) || !file.getText().contentEquals(document.getCharsSequence())) return FoldingDescriptor.EMPTY;
        var folds = new LinkedHashMap<TextRange, FoldingDescriptor>();
        var parsed = new JxCompiler().parse(new SourceFile(JxJavaProjection.path(file), file.getText()));
        if (parsed.diagnostics().isEmpty()) markup(parsed.nodes(), file, document, folds);
        if (!quick) JxSourceNavigation.view(file).ifPresent(view -> {
            for (var type : PsiTreeUtil.findChildrenOfType(view.javaFile(), PsiClass.class)) {
                ProgressManager.checkCanceled();
                view.enclosing(type.getLBrace(), type.getRBrace()).ifPresent(range -> add(file, document, folds, range, "{…}"));
            }
            for (var block : PsiTreeUtil.findChildrenOfType(view.javaFile(), PsiCodeBlock.class)) {
                ProgressManager.checkCanceled();
                view.enclosing(block.getLBrace(), block.getRBrace()).ifPresent(range -> add(file, document, folds, range, "{…}"));
            }
        });
        return folds.values().toArray(FoldingDescriptor[]::new);
    }

    private static void markup(List<Syntax.Node> nodes, PsiFile file, Document document, Map<TextRange, FoldingDescriptor> folds) {
        for (var node : nodes) {
            ProgressManager.checkCanceled();
            if (node instanceof Syntax.Element element) {
                add(file, document, folds, new TextRange(element.span().start(), element.span().end()),
                        "<" + element.name() + ">…</" + element.name() + ">");
                markup(element.children(), file, document, folds);
                for (var attribute : element.attributes()) if (attribute.expression() != null)
                    markup(attribute.expression().expression(), file, document, folds);
            } else if (node instanceof Syntax.Interpolation expression) markup(expression.expression(), file, document, folds);
        }
    }
    private static void add(PsiFile file, Document document, Map<TextRange, FoldingDescriptor> folds, TextRange range, String placeholder) {
        if (range.getLength() < 2 || range.getEndOffset() > document.getTextLength()
                || document.getLineNumber(range.getStartOffset()) == document.getLineNumber(range.getEndOffset() - 1)) return;
        folds.putIfAbsent(range, new FoldingDescriptor(file.getNode(), range, null, placeholder));
    }
    @Override public String getPlaceholderText(@NotNull ASTNode node) { return "…"; }
    @Override public boolean isCollapsedByDefault(@NotNull ASTNode node) { return false; }
}
