package jx.intellij;

import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

/** Inherit the active theme's HTML palette, with optional JX-only overrides. */
public final class JxColors {
    public static final TextAttributesKey TAG = TextAttributesKey.createTextAttributesKey("JX.TAG_NAME", XmlHighlighterColors.HTML_TAG_NAME);
    public static final TextAttributesKey DELIMITER = TextAttributesKey.createTextAttributesKey("JX.TAG_DELIMITER", XmlHighlighterColors.HTML_TAG);
    public static final TextAttributesKey ATTRIBUTE = TextAttributesKey.createTextAttributesKey("JX.ATTRIBUTE_NAME", XmlHighlighterColors.HTML_ATTRIBUTE_NAME);
    public static final TextAttributesKey VALUE = TextAttributesKey.createTextAttributesKey("JX.ATTRIBUTE_VALUE", XmlHighlighterColors.HTML_ATTRIBUTE_VALUE);
    public static final TextAttributesKey ENTITY = TextAttributesKey.createTextAttributesKey("JX.ENTITY", XmlHighlighterColors.HTML_ENTITY_REFERENCE);
    private JxColors() {}
}
