package jx.intellij;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.ArrayList;

/** Compare IntelliJ's real incremental token cache against a fresh lexical pass after an edit. */
public final class JxIncrementalHighlightingTest extends BasePlatformTestCase {
    public void testClosingMarkupContextInvalidatesFollowingCachedTokens() {
        checkEdit("class App { Object render() { return <span>hello</span>; } }", ">hello", "/>hello");
    }

    public void testReopeningMarkupContextInvalidatesFollowingCachedTokens() {
        checkEdit("class App { Object render() { return <span/>hello</span>; } }", "/>hello", ">hello");
    }

    public void testChangingJavaBraceDepthRelexesFollowingMarkupInterpolation() {
        checkEdit("class App { Object render() { return <button onClick={event -> { count++; }}>Hi {label}</button>; } }",
                "{ count", "count");
    }

    private void checkEdit(String before, String needle, String replacement) {
        myFixture.setCaresAboutInjection(false);
        myFixture.configureByText("App.jx", before);
        var editor = (EditorEx) myFixture.getEditor();
        var highlighting = new LexerEditorHighlighter(new JxSyntaxHighlighterFactory().getSyntaxHighlighter(getProject(), null),
                EditorColorsManager.getInstance().getGlobalScheme());
        editor.setHighlighter(highlighting);
        var document = editor.getDocument();
        int offset = document.getText().indexOf(needle);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.replaceString(offset, offset + needle.length(), replacement));
        assertIncrementalMatchesFresh(highlighting, document.getText());
    }

    private static void assertIncrementalMatchesFresh(LexerEditorHighlighter highlighting, String text) {
        var actual = new ArrayList<String>();
        var iterator = highlighting.createIterator(0);
        while (!iterator.atEnd()) {
            actual.add(iterator.getStart() + ":" + iterator.getEnd() + ":" + iterator.getTokenType());
            iterator.advance();
        }
        var expected = new ArrayList<String>();
        var lexer = new JxLexer(); lexer.start(text);
        while (lexer.getTokenType() != null) {
            expected.add(lexer.getTokenStart() + ":" + lexer.getTokenEnd() + ":" + lexer.getTokenType());
            lexer.advance();
        }
        assertEquals(expected, actual);
    }
}
