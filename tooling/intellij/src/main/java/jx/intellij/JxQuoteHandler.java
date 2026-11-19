package jx.intellij;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;

/** Java's own handler still owns injected Java; this also handles quoted attributes. */
public final class JxQuoteHandler extends SimpleTokenSetQuoteHandler {
    public JxQuoteHandler() { super(JxLexer.STRING, JxLexer.ATTRIBUTE_VALUE); }
}
