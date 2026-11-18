package jx.intellij;

import com.intellij.lang.Language;

public final class JxLanguage extends Language {
    public static final JxLanguage INSTANCE = new JxLanguage();
    private JxLanguage() { super("JX"); }
}
