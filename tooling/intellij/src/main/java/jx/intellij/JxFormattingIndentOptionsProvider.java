package jx.intellij;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;

/** JX shares the project's Java indentation preferences, including tabs and tab width. */
public final class JxFormattingIndentOptionsProvider extends FileIndentOptionsProvider {
    @Override public CommonCodeStyleSettings.IndentOptions getIndentOptions(CodeStyleSettings settings, PsiFile file) {
        if (file.getFileType() != JxFileType.INSTANCE) return null;
        var options = new CommonCodeStyleSettings.IndentOptions();
        options.copyFrom(settings.getLanguageIndentOptions(JavaLanguage.INSTANCE));
        return options;
    }
    @Override public boolean useOnFullReformat() { return true; }
}
