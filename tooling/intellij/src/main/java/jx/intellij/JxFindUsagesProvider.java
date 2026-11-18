package jx.intellij;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/** Index authored Java identifiers so native reference search also visits JX documents. */
public final class JxFindUsagesProvider implements FindUsagesProvider {
    @Override public WordsScanner getWordsScanner() {
        return new DefaultWordsScanner(JavaParserDefinition.createLexer(LanguageLevel.JDK_25),
                TokenSet.create(JavaTokenType.IDENTIFIER), ElementType.JAVA_COMMENT_BIT_SET,
                TokenSet.create(JavaTokenType.STRING_LITERAL, JavaTokenType.CHARACTER_LITERAL, JavaTokenType.TEXT_BLOCK_LITERAL));
    }
    @Override public boolean canFindUsagesFor(@NotNull PsiElement element) { return element instanceof PsiNamedElement; }
    @Override public String getHelpId(@NotNull PsiElement element) { return null; }
    @Override public @NotNull String getType(@NotNull PsiElement element) { return "Java declaration"; }
    @Override public @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        return element instanceof PsiNamedElement named && named.getName() != null ? named.getName() : element.getText();
    }
    @Override public @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) { return getDescriptiveName(element); }
}
