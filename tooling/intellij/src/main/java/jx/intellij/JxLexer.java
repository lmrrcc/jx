package jx.intellij;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Editor tokenization only; all semantic parsing belongs to the shared compiler. */
public final class JxLexer extends LexerBase {
    static final IElementType TEXT = token("TEXT"), KEYWORD = token("KEYWORD"), STRING = token("STRING"), COMMENT = token("COMMENT"), TAG = token("TAG"), NUMBER = token("NUMBER"),
            ATTRIBUTE = token("ATTRIBUTE"), ATTRIBUTE_VALUE = token("ATTRIBUTE_VALUE"), MARKUP_DELIMITER = token("MARKUP_DELIMITER"),
            EXPRESSION_DELIMITER = token("EXPRESSION_DELIMITER"), ENTITY = token("ENTITY"), OPERATOR = token("OPERATOR");
    static final IElementType LBRACE = token("LBRACE"), RBRACE = token("RBRACE"),
            LPAREN = token("LPAREN"), RPAREN = token("RPAREN"),
            LBRACKET = token("LBRACKET"), RBRACKET = token("RBRACKET"),
            EXPRESSION_START = token("EXPRESSION_START"), EXPRESSION_END = token("EXPRESSION_END");
    private static final Set<String> KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "record", "sealed", "permits", "var", "yield", "when");
    private sealed interface Context permits JavaContext, MarkupContext {}
    private static final class JavaContext implements Context {
        private final boolean expression;
        private int braces = 1;
        private boolean allowsMarkup = true;
        private JavaContext(boolean expression) { this.expression = expression; }
    }
    private static final class MarkupContext implements Context {
        private int depth;
        private boolean inTag, closing, expectsName;
    }
    private final Deque<Context> contexts = new ArrayDeque<>();
    private CharSequence buffer = "";
    private int start, end, limit;
    private IElementType type;
    private int state;
    private boolean markupToken;
    private boolean tagInteriorToken;
    boolean isMarkupToken() { return markupToken; }
    boolean isTagInteriorToken() { return tagInteriorToken; }
    private static IElementType token(String name) { return new IElementType(name, JxLanguage.INSTANCE); }
    @Override public void start(@NotNull CharSequence value, int startOffset, int endOffset, int initialState) {
        buffer = value; start = end = 0; limit = endOffset;
        contexts.clear(); contexts.push(new JavaContext(false));
        // Replay the prefix on incremental restart, so arbitrary nested Java/markup state
        // remains correct without retaining unbounded mutable state IDs across edits.
        do { advance(); } while (type != null && end <= startOffset);
        start = Math.max(start, startOffset);
    }
    // IntelliJ may reuse all following cached tokens when it reaches the initial state.
    // Only neutral outer Java is a safe synchronization point. Returning zero inside
    // markup/interpolations makes edits to nesting leave stale colors farther below.
    // Nonzero states are deliberately non-restartable; start() reconstructs their context
    // by replaying the prefix, without a growing table of encoded nested states.
    @Override public int getState() { return state; }
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
        state = contexts.size() == 1 && contexts.peek() instanceof JavaContext java
                && !java.expression && java.allowsMarkup ? 0 : 1;
        if (start >= limit) { type = null; return; }
        markupToken = contexts.peek() instanceof MarkupContext;
        tagInteriorToken = contexts.peek() instanceof MarkupContext markup && markup.inTag;
        if (contexts.peek() instanceof MarkupContext markup) markup(markup);
        else java((JavaContext) contexts.peek());
        if (type == MARKUP_DELIMITER || type == EXPRESSION_START || type == EXPRESSION_END) markupToken = true;
    }
    private void java(JavaContext java) {
        char first = buffer.charAt(end++);
        if (Character.isWhitespace(first)) { while (end < limit && Character.isWhitespace(buffer.charAt(end))) end++; type = TokenType.WHITE_SPACE; }
        else if (at(start, "//")) { while (end < limit && buffer.charAt(end) != '\n') end++; type = COMMENT; }
        else if (at(start, "/*")) { end++; while (end < limit && !at(end, "*/")) end++; end = Math.min(limit, end + 2); type = COMMENT; }
        else if (first == '"' || first == '\'') {
            String delimiter = at(start, "\"\"\"") ? "\"\"\"" : String.valueOf(first);
            end = start + delimiter.length();
            while (end < limit) { if (at(end, delimiter)) { end += delimiter.length(); break; } if (buffer.charAt(end++) == '\\' && end < limit) end++; }
            type = STRING; java.allowsMarkup = false;
        } else if (first == '<' && java.allowsMarkup && end < limit && (Character.isJavaIdentifierStart(buffer.charAt(end)) || buffer.charAt(end) == '>')) {
            java.allowsMarkup = false;
            var markup = new MarkupContext(); contexts.push(markup); openTag(markup);
        } else if (Character.isJavaIdentifierStart(first)) {
            while (end < limit && Character.isJavaIdentifierPart(buffer.charAt(end))) end++;
            String word = buffer.subSequence(start, end).toString();
            type = KEYWORDS.contains(word) ? KEYWORD : TEXT;
            java.allowsMarkup = word.equals("return") || word.equals("yield");
        } else if (Character.isDigit(first)) {
            while (end < limit && (Character.isLetterOrDigit(buffer.charAt(end)) || buffer.charAt(end) == '.' || buffer.charAt(end) == '_')) end++;
            type = NUMBER; java.allowsMarkup = false;
        } else if (first == '{') { java.braces++; java.allowsMarkup = true; type = LBRACE; }
        else if (first == '}' && java.expression && --java.braces == 0) { contexts.pop(); type = EXPRESSION_END; }
        else {
            if (first == '-' && end < limit && buffer.charAt(end) == '>') end++;
            java.allowsMarkup = "(=?:,[;".indexOf(first) >= 0 || at(start, "->");
            type = switch (first) {
                case '(' -> LPAREN; case ')' -> RPAREN;
                case '[' -> LBRACKET; case ']' -> RBRACKET;
                case '}' -> RBRACE; default -> OPERATOR;
            };
        }
    }
    private void markup(MarkupContext markup) {
        char first = buffer.charAt(end++);
        if (first == '{') { contexts.push(new JavaContext(true)); type = EXPRESSION_START; }
        else if (at(start, "<!--")) { end = start + 4; until("-->"); type = COMMENT; }
        else if (first == '<' && !markup.inTag) openTag(markup);
        else if (markup.inTag) {
            if (Character.isWhitespace(first)) {
                while (end < limit && Character.isWhitespace(buffer.charAt(end))) end++;
                type = TokenType.WHITE_SPACE;
            } else if (first == '"' || first == '\'') {
                while (end < limit) {
                    if (buffer.charAt(end++) == first) break;
                    if (buffer.charAt(end - 1) == '\\' && end < limit) end++;
                }
                type = ATTRIBUTE_VALUE;
            } else if (first == '>' || at(start, "/>")) {
                boolean selfClosing = first == '/';
                if (selfClosing) end++;
                markup.inTag = false;
                if (markup.closing) markup.depth--; else if (!selfClosing) markup.depth++;
                if (markup.depth <= 0) contexts.pop();
                type = MARKUP_DELIMITER;
            } else if (Character.isJavaIdentifierStart(first)) {
                while (end < limit && (Character.isJavaIdentifierPart(buffer.charAt(end)) || ".:-".indexOf(buffer.charAt(end)) >= 0)) end++;
                type = markup.expectsName ? TAG : ATTRIBUTE; markup.expectsName = false;
            } else type = MARKUP_DELIMITER;
        } else if (first == '&') {
            while (end < limit && (Character.isLetterOrDigit(buffer.charAt(end)) || buffer.charAt(end) == '#')) end++;
            if (end < limit && buffer.charAt(end) == ';') end++;
            type = ENTITY;
        } else {
            while (end < limit && "<{&".indexOf(buffer.charAt(end)) < 0) end++;
            type = TEXT;
        }
    }
    private void openTag(MarkupContext markup) {
        markup.inTag = true; markup.expectsName = true;
        markup.closing = end < limit && buffer.charAt(end) == '/';
        if (markup.closing) end++;
        type = MARKUP_DELIMITER;
    }
    private void until(String delimiter) {
        while (end < limit && !at(end, delimiter)) end++;
        end = Math.min(limit, end + delimiter.length());
    }
}
