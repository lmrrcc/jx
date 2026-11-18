package jx.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Composes the registered Java error visitors through the public highlighting extension contract.
 * Their ordinary-Java policy excludes generic injections; JX supplies a complete compilation unit. */
public final class JxJavaHighlightVisitor implements HighlightVisitor {
    private List<HighlightVisitor> delegates = List.of();

    @Override public boolean suitableForFile(@NotNull PsiFile file) {
        return !HighlightingLevelManager.getInstance(file.getProject()).runEssentialHighlightingOnly(file)
                && file instanceof PsiJavaFile && InjectedLanguageManager.getInstance(file.getProject())
                .getInjectionHost(file) instanceof JxJavaInjectionHost;
    }
    @Override public boolean supersedesDefaultHighlighter() { return true; }
    @Override public void visit(@NotNull PsiElement element) {
        for (HighlightVisitor visitor : delegates) visitor.visit(element);
    }
    @Override public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile,
                                     @NotNull HighlightInfoHolder holder, @NotNull Runnable action) {
        PsiFile ordinaryJava = PsiFileFactory.getInstance(file.getProject()).createFileFromText(
                "JxHighlightingProbe.java", JavaFileType.INSTANCE, "final class JxHighlightingProbe {}", 0, false, true);
        delegates = EP_HIGHLIGHT_VISITOR.getExtensionList(file.getProject()).stream()
                .filter(visitor -> !(visitor instanceof JxJavaHighlightVisitor))
                .filter(visitor -> visitor.supersedesDefaultHighlighter() && visitor.suitableForFile(ordinaryJava)
                        && !visitor.suitableForFile(file))
                .map(HighlightVisitor::clone).toList();
        try {
            // No native Java visitor means that required Java analysis is unavailable: ask the
            // daemon to retry rather than claiming this file has been successfully analyzed.
            return !delegates.isEmpty() && analyze(0, file, updateWholeFile, holder, action);
        } finally { delegates = List.of(); }
    }
    private boolean analyze(int index, PsiFile file, boolean updateWholeFile, HighlightInfoHolder holder, Runnable action) {
        if (index == delegates.size()) { action.run(); return true; }
        boolean[] nested = {false};
        boolean result = delegates.get(index).analyze(file, updateWholeFile, holder,
                () -> nested[0] = analyze(index + 1, file, updateWholeFile, holder, action));
        return result && nested[0];
    }
    @Override public @NotNull JxJavaHighlightVisitor clone() { return new JxJavaHighlightVisitor(); }
}
