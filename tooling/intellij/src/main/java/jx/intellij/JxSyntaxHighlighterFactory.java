package jx.intellij;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class JxSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
    @Override public @NotNull SyntaxHighlighter getSyntaxHighlighter(Project project, VirtualFile file) {
        return new SyntaxHighlighterBase() {
            @Override public @NotNull Lexer getHighlightingLexer() { return new JxLexer(); }
            @Override public TextAttributesKey @NotNull [] getTokenHighlights(IElementType type) {
                if (type == JxLexer.KEYWORD) return pack(DefaultLanguageHighlighterColors.KEYWORD);
                if (type == JxLexer.STRING) return pack(DefaultLanguageHighlighterColors.STRING);
                if (type == JxLexer.COMMENT) return pack(DefaultLanguageHighlighterColors.BLOCK_COMMENT);
                if (type == JxLexer.TAG) return pack(JxColors.TAG);
                if (type == JxLexer.ATTRIBUTE) return pack(JxColors.ATTRIBUTE);
                if (type == JxLexer.ATTRIBUTE_VALUE) return pack(JxColors.VALUE);
                if (type == JxLexer.MARKUP_DELIMITER) return pack(JxColors.DELIMITER);
                if (type == JxLexer.EXPRESSION_DELIMITER || type == JxLexer.EXPRESSION_START || type == JxLexer.EXPRESSION_END
                        || type == JxLexer.LBRACE || type == JxLexer.RBRACE) return pack(DefaultLanguageHighlighterColors.BRACES);
                if (type == JxLexer.LPAREN || type == JxLexer.RPAREN) return pack(DefaultLanguageHighlighterColors.PARENTHESES);
                if (type == JxLexer.LBRACKET || type == JxLexer.RBRACKET) return pack(DefaultLanguageHighlighterColors.BRACKETS);
                if (type == JxLexer.ENTITY) return pack(JxColors.ENTITY);
                if (type == JxLexer.OPERATOR) return pack(DefaultLanguageHighlighterColors.OPERATION_SIGN);
                if (type == JxLexer.NUMBER) return pack(DefaultLanguageHighlighterColors.NUMBER);
                return TextAttributesKey.EMPTY_ARRAY;
            }
        };
    }
}
