package jx.intellij;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Editor tokenization only; all semantic parsing belongs to the shared compiler. */
public final class JxLexer extends LexerBase {
    static final IElementType TEXT = token("TEXT"), KEYWORD = token("KEYWORD"), STRING = token("STRING"), COMMENT = token("COMMENT"), TAG = token("TAG"), NUMBER = token("NUMBER");
    private static final Set<String> KEYWORDS = Set.of("public", "private", "protected", "class", "record", "interface", "enum", "static", "final", "return", "new", "if", "else", "switch", "case", "yield", "void", "int", "long", "double", "boolean", "import", "package", "extends", "implements", "null", "true", "false", "var", "throw", "throws", "try", "catch", "finally", "for", "while", "sealed", "permits", "instanceof");
    private CharSequence buffer = "";
    private int start, end, limit;
    private IElementType type;
    private static IElementType token(String name) { return new IElementType(name, JxLanguage.INSTANCE); }
    @Override public void start(@NotNull CharSequence value, int startOffset, int endOffset, int initialState) {
        buffer = value; start = end = startOffset; limit = endOffset; advance();
    }
    @Override public int getState() { return 0; }
    @Override public IElementType getTokenType() { return type; }
    @Override public int getTokenStart() { return start; }
    @Override public int getTokenEnd() { return end; }
    @Override public @NotNull CharSequence getBufferSequence() { return buffer; }
    @Override public int getBufferEnd() { return limit; }
    private boolean at(int offset, String value) {
        if (offset + value.length() > limit) return false;
        for (int index = 0; index < value.length(); index++) if (buffer.charAt(offset + index) != value.charAt(index)) return false;
        return true;
    }
    @Override public void advance() {
        start = end;
        if (start == limit) { type = null; return; }
        char first = buffer.charAt(end++);
        if (Character.isWhitespace(first)) { while (end < limit && Character.isWhitespace(buffer.charAt(end))) end++; type = TokenType.WHITE_SPACE; }
        else if (at(start, "//")) { while (end < limit && buffer.charAt(end) != '\n') end++; type = COMMENT; }
        else if (at(start, "/*")) { end++; while (end < limit && !at(end, "*/")) end++; end = Math.min(limit, end + 2); type = COMMENT; }
        else if (first == '"' || first == '\'') {
            String delimiter = at(start, "\"\"\"") ? "\"\"\"" : String.valueOf(first);
            end = start + delimiter.length();
            while (end < limit) { if (at(end, delimiter)) { end += delimiter.length(); break; } if (buffer.charAt(end++) == '\\' && end < limit) end++; }
            type = STRING;
        } else if (first == '<' && end < limit && (Character.isJavaIdentifierStart(buffer.charAt(end)) || buffer.charAt(end) == '/')) {
            if (buffer.charAt(end) == '/') end++;
            while (end < limit && (Character.isJavaIdentifierPart(buffer.charAt(end)) || buffer.charAt(end) == '.')) end++;
            type = TAG;
        } else if (Character.isJavaIdentifierStart(first)) {
            while (end < limit && Character.isJavaIdentifierPart(buffer.charAt(end))) end++;
            type = KEYWORDS.contains(buffer.subSequence(start, end).toString()) ? KEYWORD : TEXT;
        } else if (Character.isDigit(first)) { while (end < limit && (Character.isDigit(buffer.charAt(end)) || buffer.charAt(end) == '.')) end++; type = NUMBER; }
        else type = TEXT;
    }
}
