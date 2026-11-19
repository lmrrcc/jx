package jx.intellij;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;

/** Host delimiters remain available even while the Java projection is incomplete. */
public final class JxBraceMatcher implements PairedBraceMatcher {
    @Override public BracePair[] getPairs() {
        return new BracePair[] {
                new BracePair(JxLexer.LBRACE, JxLexer.RBRACE, true),
                new BracePair(JxLexer.LPAREN, JxLexer.RPAREN, false),
                new BracePair(JxLexer.LBRACKET, JxLexer.RBRACKET, false),
                new BracePair(JxLexer.EXPRESSION_START, JxLexer.EXPRESSION_END, false)
        };
    }
    @Override public boolean isPairedBracesAllowedBeforeType(IElementType left, IElementType next) { return true; }
    @Override public int getCodeConstructStart(PsiFile file, int openingBraceOffset) { return openingBraceOffset; }
}
