package jx.intellij;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public final class JxParserDefinition implements ParserDefinition {
    private static final IFileElementType FILE = new IFileElementType(JxLanguage.INSTANCE);
    @Override public @NotNull Lexer createLexer(Project project) { return new JxLexer(); }
    @Override public @NotNull PsiParser createParser(Project project) {
        return (root, builder) -> { var marker = builder.mark(); while (!builder.eof()) builder.advanceLexer(); marker.done(root); return builder.getTreeBuilt(); };
    }
    @Override public @NotNull IFileElementType getFileNodeType() { return FILE; }
    @Override public @NotNull TokenSet getCommentTokens() { return TokenSet.create(JxLexer.COMMENT); }
    @Override public @NotNull TokenSet getStringLiteralElements() { return TokenSet.create(JxLexer.STRING); }
    @Override public @NotNull PsiElement createElement(ASTNode node) { return new com.intellij.extapi.psi.ASTWrapperPsiElement(node); }
    @Override public @NotNull PsiFile createFile(@NotNull FileViewProvider provider) {
        return new PsiFileBase(provider, JxLanguage.INSTANCE) { @Override public @NotNull FileType getFileType() { return JxFileType.INSTANCE; } };
    }
}
