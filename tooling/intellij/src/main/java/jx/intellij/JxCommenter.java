package jx.intellij;

import com.intellij.codeInsight.generation.CommenterDataHolder;
import com.intellij.codeInsight.generation.SelfManagingCommenter;
import com.intellij.lang.Commenter;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import java.util.HashMap;
import java.util.Map;

/** Comment actions use Java delimiters in code and HTML delimiters in markup. */
public final class JxCommenter implements Commenter, SelfManagingCommenter<JxCommenter.State> {
    static final class State extends CommenterDataHolder {
        final Map<Integer, Boolean> markupLines = new HashMap<>();
        final java.util.Set<Integer> tagInteriorLines = new java.util.HashSet<>();
        boolean markup;
        boolean tagInterior;
    }
    @Override public String getLineCommentPrefix() { return "//"; }
    @Override public String getBlockCommentPrefix() { return "/*"; }
    @Override public String getBlockCommentSuffix() { return "*/"; }
    @Override public String getCommentedBlockCommentPrefix() { return null; }
    @Override public String getCommentedBlockCommentSuffix() { return null; }

    static boolean markupAt(CharSequence text, int offset) {
        var lexer = new JxLexer(); lexer.start(text);
        while (lexer.getTokenType() != null) {
            if (offset < lexer.getTokenEnd()) return lexer.isMarkupToken();
            lexer.advance();
        }
        return false;
    }
    private static int contentStart(Document document, int line) {
        int start = document.getLineStartOffset(line), end = document.getLineEndOffset(line);
        CharSequence text = document.getCharsSequence();
        while (start < end && Character.isWhitespace(text.charAt(start))) start++;
        return start;
    }
    @Override public State createLineCommentingState(int first, int last, Document document, PsiFile file) {
        var state = new State();
        var lexer = new JxLexer(); lexer.start(document.getCharsSequence());
        for (int line = first; line <= last; line++) {
            int start = contentStart(document, line);
            while (lexer.getTokenType() != null && lexer.getTokenEnd() <= start) lexer.advance();
            state.markupLines.put(line, lexer.getTokenType() != null && lexer.isMarkupToken());
            if (lexer.getTokenType() != null && lexer.isTagInteriorToken()) state.tagInteriorLines.add(line);
        }
        return state;
    }
    @Override public State createBlockCommentingState(int start, int end, Document document, PsiFile file) {
        var state = new State();
        var lexer = new JxLexer(); lexer.start(document.getCharsSequence());
        while (lexer.getTokenType() != null && lexer.getTokenEnd() <= start) lexer.advance();
        state.markup = lexer.getTokenType() != null && lexer.isMarkupToken();
        state.tagInterior = lexer.getTokenType() != null && lexer.isTagInteriorToken();
        return state;
    }
    @Override public String getCommentPrefix(int line, Document document, State state) {
        return state.markupLines.getOrDefault(line, false) ? "<!--" : "//";
    }
    @Override public boolean isLineCommented(int line, int offset, Document document, State state) {
        String text = document.getText(new TextRange(contentStart(document, line), document.getLineEndOffset(line)));
        return state.markupLines.getOrDefault(line, false)
                ? text.startsWith("<!--") && text.endsWith("-->") : text.startsWith("//");
    }
    @Override public void commentLine(int line, int offset, Document document, State state) {
        if (state.tagInteriorLines.contains(line)) return;
        int start = contentStart(document, line), end = document.getLineEndOffset(line);
        if (state.markupLines.getOrDefault(line, false)) {
            String text = document.getText(new TextRange(start, end));
            // Nested comment delimiters cannot be represented without changing the source text.
            if (text.contains("--")) return;
            document.insertString(end, "-->"); document.insertString(start, "<!--");
        } else document.insertString(start, "//");
    }
    @Override public void uncommentLine(int line, int offset, Document document, State state) {
        int start = contentStart(document, line), end = document.getLineEndOffset(line);
        String text = document.getText(new TextRange(start, end));
        if (state.markupLines.getOrDefault(line, false) && text.startsWith("<!--") && text.endsWith("-->")) {
            document.deleteString(end - 3, end); document.deleteString(start, start + 4);
        } else if (!state.markupLines.getOrDefault(line, false) && text.startsWith("//")) document.deleteString(start, start + 2);
    }
    @Override public String getBlockCommentPrefix(int start, Document document, State state) { return state.markup ? "<!--" : "/*"; }
    @Override public String getBlockCommentSuffix(int end, Document document, State state) { return state.markup ? "-->" : "*/"; }
    @Override public TextRange getBlockCommentRange(int start, int end, Document document, State state) {
        var lexer = new JxLexer(); lexer.start(document.getCharsSequence());
        while (lexer.getTokenType() != null) {
            if (lexer.getTokenType() == JxLexer.COMMENT && lexer.getTokenStart() <= start && lexer.getTokenEnd() >= end) {
                String text = document.getCharsSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
                if (text.startsWith("/*") && text.endsWith("*/") || text.startsWith("<!--") && text.endsWith("-->"))
                    return new TextRange(lexer.getTokenStart(), lexer.getTokenEnd());
            }
            lexer.advance();
        }
        return null;
    }
    @Override public TextRange insertBlockComment(int start, int end, Document document, State state) {
        if (state.tagInterior) return new TextRange(start, end);
        String prefix = getBlockCommentPrefix(start, document, state), suffix = getBlockCommentSuffix(end, document, state);
        String text = document.getText(new TextRange(start, end));
        if (text.contains(prefix) || text.contains(suffix) || state.markup && text.contains("--")) return new TextRange(start, end);
        document.insertString(end, suffix); document.insertString(start, prefix);
        return new TextRange(start, end + prefix.length() + suffix.length());
    }
    @Override public void uncommentBlockComment(int start, int end, Document document, State state) {
        String text = document.getText(new TextRange(start, end));
        boolean markup = text.startsWith("<!--") && text.endsWith("-->");
        boolean java = text.startsWith("/*") && text.endsWith("*/");
        if (!markup && !java) return;
        int prefix = markup ? 4 : 2, suffix = markup ? 3 : 2;
        document.deleteString(end - suffix, end); document.deleteString(start, start + prefix);
    }
}
