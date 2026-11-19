package jx.intellij;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.Wrap;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Authored layout, independent of generated wrapper indentation. Non-layout markup text is
 * an indivisible leaf, including spaces which have observable rendering semantics. */
public final class JxFormattingModelBuilder implements FormattingModelBuilder {
    @Override public @NotNull FormattingModel createModel(@NotNull FormattingContext context) {
        var file = context.getContainingFile();
        var options = context.getCodeStyleSettings().getLanguageIndentOptions(JavaLanguage.INSTANCE);
        String source = file.getText();
        // Layout-only markup whitespace is a text token in PSI. A PSI whitespace replacer
        // would insert an additional whitespace node beside it instead of replacing it.
        return new DocumentBasedFormattingModel(
                new Root(source, Math.max(0, options.INDENT_SIZE), Math.max(1, options.TAB_SIZE),
                        source.equals(file.getUserData(JxFormattingPreProcessor.JAVA_FORMATTED_SOURCE))),
                context.getCodeStyleSettings(), file);
    }

    private record Leaf(TextRange range, IElementType type, String text, boolean markup,
                        boolean protectedText, int columns) implements Block {
        @Override public @NotNull TextRange getTextRange() { return range; }
        @Override public @NotNull List<Block> getSubBlocks() { return List.of(); }
        @Override public @Nullable Wrap getWrap() { return null; }
        @Override public @Nullable Indent getIndent() { return Indent.getSpaceIndent(columns, true); }
        @Override public @Nullable Alignment getAlignment() { return null; }
        @Override public @Nullable Spacing getSpacing(Block left, @NotNull Block right) { return null; }
        @Override public @NotNull ChildAttributes getChildAttributes(int index) { return new ChildAttributes(Indent.getNoneIndent(), null); }
        @Override public boolean isIncomplete() { return false; }
        @Override public boolean isLeaf() { return true; }
    }

    private static final class Root implements Block {
        private final String source;
        private final List<Block> children;
        private final int size;
        Root(String source, int size, int tabSize, boolean nativeLayout) {
            this.source = source; this.size = size;
            var lexer = new JxLexer(); lexer.start(source);
            var blocks = new ArrayList<Block>();
            int braces = 0, tags = 0, rootExtra = 0;
            var tagHeaders = new ArrayDeque<Boolean>();
            while (lexer.getTokenType() != null) {
                ProgressManager.checkCanceled();
                IElementType type = lexer.getTokenType();
                String text = source.substring(lexer.getTokenStart(), lexer.getTokenEnd());
                boolean markup = lexer.isMarkupToken();
                boolean inTag = !tagHeaders.isEmpty();
                if (type == JxLexer.RBRACE) braces = Math.max(0, braces - 1);
                if (type == JxLexer.MARKUP_DELIMITER && text.equals("</")) {
                    tags = Math.max(0, tags - 1); tagHeaders.push(true); inTag = true;
                } else if (type == JxLexer.MARKUP_DELIMITER && text.equals("<")) {
                    if (tags == 0 && !inTag) {
                        int line = source.lastIndexOf('\n', lexer.getTokenStart() - 1) + 1;
                        rootExtra = source.substring(line, lexer.getTokenStart()).isBlank() ? 1 : 0;
                    }
                    tagHeaders.push(false); inTag = true;
                }
                boolean markupText = markup && type == JxLexer.TEXT;
                // The compiler discards whitespace-only multiline JSX layout. A single-line
                // space remains text, so it is deliberately not available to the formatter.
                boolean layout = markupText && text.isBlank() && (text.contains("\n") || text.contains("\r"));
                if (type != TokenType.WHITE_SPACE && !layout) {
                    int level = braces + tags + (markup || inTag || tags > 0 ? rootExtra : 0);
                    int columns = level * size;
                    // Outside markup preserve the native Java formatter's continuation,
                    // alignment and brace-style decisions instead of approximating Java layout.
                    if (nativeLayout && tags == 0 && !inTag && !markup) {
                        int line = source.lastIndexOf('\n', lexer.getTokenStart() - 1) + 1;
                        int actual = 0, cursor = line;
                        while (cursor < lexer.getTokenStart() && (source.charAt(cursor) == ' ' || source.charAt(cursor) == '\t')) {
                            actual = source.charAt(cursor++) == '\t' ? actual + tabSize - actual % tabSize : actual + 1;
                        }
                        if (cursor == lexer.getTokenStart()) columns = actual;
                    }
                    blocks.add(new Leaf(new TextRange(lexer.getTokenStart(), lexer.getTokenEnd()),
                            type, text, markup, markupText, columns));
                }
                if (type == JxLexer.LBRACE) braces++;
                if (type == JxLexer.MARKUP_DELIMITER && (text.equals(">") || text.equals("/>")) && !tagHeaders.isEmpty()) {
                    boolean closing = tagHeaders.pop();
                    if (text.equals(">") && !closing) tags++;
                }
                lexer.advance();
            }
            children = List.copyOf(blocks);
        }
        @Override public @NotNull TextRange getTextRange() { return new TextRange(0, source.length()); }
        @Override public @NotNull List<Block> getSubBlocks() { return children; }
        @Override public @Nullable Wrap getWrap() { return null; }
        @Override public @Nullable Indent getIndent() { return Indent.getNoneIndent(); }
        @Override public @Nullable Alignment getAlignment() { return null; }
        @Override public @Nullable Spacing getSpacing(Block left, @NotNull Block right) {
            if (!(left instanceof Leaf previous) || !(right instanceof Leaf next)) return null;
            if (previous.protectedText() || next.protectedText()) return Spacing.getReadOnlySpacing();
            String gap = source.substring(previous.range().getEndOffset(), next.range().getStartOffset());
            int lineFeeds = (int) gap.chars().filter(c -> c == '\n').count();
            if (lineFeeds > 0) return Spacing.createSpacing(0, 0, lineFeeds, true, lineFeeds);
            int spaces = gap.length();
            if (next.type() == JxLexer.ATTRIBUTE && previous.markup()) spaces = 1;
            if (previous.type() == JxLexer.ATTRIBUTE && next.text().equals("=")) spaces = 0;
            if (previous.markup() && previous.text().equals("=")) spaces = 0;
            return Spacing.createSpacing(spaces, spaces, 0, false, 0);
        }
        @Override public @NotNull ChildAttributes getChildAttributes(int index) {
            int columns = index < children.size() ? ((Leaf) children.get(index)).columns()
                    : children.isEmpty() ? 0 : ((Leaf) children.getLast()).columns() + size;
            return new ChildAttributes(Indent.getSpaceIndent(columns, true), null);
        }
        @Override public boolean isIncomplete() { return false; }
        @Override public boolean isLeaf() { return children.isEmpty(); }
    }
}
