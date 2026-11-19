package jx.intellij;

import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.awt.Color;

/** Resolves actual theme attributes, rather than merely asserting lexer token names. */
public final class JxThemeHighlightingTest extends BasePlatformTestCase {
    private static final String SOURCE = "class App { Object render() { return <button title=\"Hello\">Go &amp; run</button>; } }";

    public void testLightThemeUsesHtmlColors() { assertHtmlPalette(EditorColorsManager.getInstance().getDefaultScheme()); }
    public void testDarkThemeUsesHtmlColors() {
        var scheme = EditorColorsManager.getInstance().getScheme("Darcula");
        assertNotNull("The supported IDEA distribution must supply its dark editor scheme", scheme);
        assertHtmlPalette(scheme);
    }
    private void assertHtmlPalette(EditorColorsScheme scheme) {
        var highlighter = highlighter(scheme);
        assertColor(highlighter, scheme, "button", XmlHighlighterColors.HTML_TAG_NAME);
        assertColor(highlighter, scheme, "title", XmlHighlighterColors.HTML_ATTRIBUTE_NAME);
        assertColor(highlighter, scheme, "\"Hello\"", XmlHighlighterColors.HTML_ATTRIBUTE_VALUE);
        assertColor(highlighter, scheme, "&amp;", XmlHighlighterColors.HTML_ENTITY_REFERENCE);
        assertFalse("HTML tags must not inherit plain source text's foreground in " + scheme.getName(),
                scheme.getDefaultForeground().equals(highlighter.createIterator(SOURCE.indexOf("button")).getTextAttributes().getForegroundColor()));
    }
    public void testJxColorCustomizationDoesNotChangeHtmlOrGlobalScheme() {
        var original = EditorColorsManager.getInstance().getGlobalScheme();
        var scheme = (EditorColorsScheme) original.clone();
        var htmlBefore = scheme.getAttributes(XmlHighlighterColors.HTML_TAG_NAME).clone();
        var override = new TextAttributes(); override.setForegroundColor(Color.MAGENTA);
        scheme.setAttributes(JxColors.TAG, override);
        assertEquals(Color.MAGENTA, highlighter(scheme).createIterator(SOURCE.indexOf("button")).getTextAttributes().getForegroundColor());
        assertEquals(htmlBefore, scheme.getAttributes(XmlHighlighterColors.HTML_TAG_NAME));
        assertSame(original, EditorColorsManager.getInstance().getGlobalScheme());
    }
    private static LexerEditorHighlighter highlighter(EditorColorsScheme scheme) {
        var highlighter = new LexerEditorHighlighter(new JxSyntaxHighlighterFactory().getSyntaxHighlighter(null, null), scheme);
        highlighter.setText(SOURCE);
        return highlighter;
    }
    private static void assertColor(LexerEditorHighlighter highlighter, EditorColorsScheme scheme, String token, TextAttributesKey expected) {
        assertEquals(token + " in " + scheme.getName(), scheme.getAttributes(expected).getForegroundColor(),
                highlighter.createIterator(SOURCE.indexOf(token)).getTextAttributes().getForegroundColor());
    }
}
