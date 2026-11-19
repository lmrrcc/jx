package jx.intellij;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.psi.tree.IElementType;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;

public final class JxHighlightingTest extends TestCase {
    private static final String MIXED = """
            public final class App {
                List<String> labels;
                boolean less = count < maximum;
                View render() {
                    return (<main className="panel" data-id='root'>
                        if return &amp;
                        <!-- <not-a-tag> -->
                        <button disabled={false} onClick={event -> {
                            if (count < 3) update("<not markup>"); /* } <fake> */
                        }}>Click {count + 1}</button>
                        {enabled ? (<><Child title={"Hello"}/><span>{label}</span></>) : null}
                    </main>);
                }
            }
            """;
    private record Token(int start, int end, IElementType type, String text) {}

    public void testNestedMarkupAndJavaReceiveDifferentThemeColors() {
        List<Token> tokens = tokens(MIXED, 0);
        assertEquals(List.of("main", "button", "button", "Child", "span", "span", "main"),
                tokens.stream().filter(token -> token.type() == JxLexer.TAG).map(Token::text).toList());
        assertEquals(List.of("className", "data-id", "disabled", "onClick", "title"),
                tokens.stream().filter(token -> token.type() == JxLexer.ATTRIBUTE).map(Token::text).toList());
        assertEquals(List.of("\"panel\"", "'root'"), tokens.stream().filter(token -> token.type() == JxLexer.ATTRIBUTE_VALUE).map(Token::text).toList());
        assertTrue(tokens.stream().anyMatch(token -> token.type() == JxLexer.KEYWORD && token.text().equals("if")));
        assertTrue(tokens.stream().anyMatch(token -> token.type() == JxLexer.TEXT && token.text().contains("if return")));
        assertTrue(tokens.stream().anyMatch(token -> token.type() == JxLexer.ENTITY && token.text().equals("&amp;")));
        assertTrue(tokens.stream().anyMatch(token -> token.type() == JxLexer.STRING && token.text().equals("\"<not markup>\"")));
        var highlighter = new JxSyntaxHighlighterFactory().getSyntaxHighlighter(null, null);
        assertEquals(JxColors.TAG, highlighter.getTokenHighlights(JxLexer.TAG)[0]);
        assertEquals(JxColors.ATTRIBUTE, highlighter.getTokenHighlights(JxLexer.ATTRIBUTE)[0]);
        assertEquals(JxColors.VALUE, highlighter.getTokenHighlights(JxLexer.ATTRIBUTE_VALUE)[0]);
        assertEquals(DefaultLanguageHighlighterColors.KEYWORD, highlighter.getTokenHighlights(JxLexer.KEYWORD)[0]);
    }
    public void testIncrementalRestartAtEveryTokenPreservesNestedContexts() {
        List<Token> full = tokens(MIXED, 0);
        for (int index = 0; index < full.size(); index++) {
            assertEquals("Restart at " + full.get(index).start(), full.subList(index, full.size()), tokens(MIXED, full.get(index).start()));
        }
    }
    public void testIncompleteMarkupAndJavaTextBlocksRemainSafe() {
        String source = "String raw = \"\"\"\n<div fake='yes'>{broken}\n\"\"\"; return (<section id=\"part";
        List<Token> tokens = tokens(source, 0);
        assertEquals(List.of("section"), tokens.stream().filter(token -> token.type() == JxLexer.TAG).map(Token::text).toList());
        assertEquals(JxLexer.ATTRIBUTE_VALUE, tokens.getLast().type());
        assertEquals(source.length(), tokens.getLast().end());
    }
    private static List<Token> tokens(String source, int start) {
        var lexer = new JxLexer(); lexer.start(source, start, source.length(), 0);
        var tokens = new ArrayList<Token>();
        int cursor = start;
        while (lexer.getTokenType() != null) {
            assertEquals(cursor, lexer.getTokenStart());
            assertTrue(lexer.getTokenEnd() > cursor);
            tokens.add(new Token(cursor, lexer.getTokenEnd(), lexer.getTokenType(), source.substring(cursor, lexer.getTokenEnd())));
            cursor = lexer.getTokenEnd(); lexer.advance();
        }
        assertEquals(source.length(), cursor);
        return tokens;
    }
}
