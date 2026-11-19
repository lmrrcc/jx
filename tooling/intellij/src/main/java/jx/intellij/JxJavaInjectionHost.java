package jx.intellij;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.ContributedReferenceHost;
import com.intellij.psi.impl.source.tree.injected.InjectionBackgroundSuppressor;
import org.jetbrains.annotations.NotNull;

/** Owns authored source beneath the file node; IntelliJ does not inject into a PsiFile itself.
 * Java is the source language here, so the platform's host marker suppresses fragment tinting
 * while retaining injected syntax colors, semantic inspections and native edit behavior. */
public final class JxJavaInjectionHost extends ASTWrapperPsiElement implements PsiLanguageInjectionHost,
        ContributedReferenceHost, InjectionBackgroundSuppressor {
    public JxJavaInjectionHost(@NotNull ASTNode node) { super(node); }
    @Override public boolean isValidHost() { return isValid(); }
    @Override public @NotNull LiteralTextEscaper<JxJavaInjectionHost> createLiteralTextEscaper() {
        return LiteralTextEscaper.createSimple(this, false);
    }
    @Override public @NotNull PsiLanguageInjectionHost updateText(@NotNull String text) {
        var replacement = PsiFileFactory.getInstance(getProject())
                .createFileFromText("jx-edit.jx", JxFileType.INSTANCE, text, 0, false, true);
        var body = replacement.getNode().findChildByType(JxParserDefinition.JAVA_HOST);
        if (body == null) throw new IllegalStateException("Cannot parse replacement JX host");
        // Native injection edits replace this aggregate host. That replacement must not
        // schedule a reformat of the entire authored file after a local rename/quick fix.
        com.intellij.psi.codeStyle.CodeStyleManager.getInstance(getProject())
                .performActionWithFormatterDisabled((Runnable) () -> getNode().replaceAllChildrenToChildrenOf(body));
        return this;
    }
}
