package jx.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public final class JxFileType extends LanguageFileType {
    public static final JxFileType INSTANCE = new JxFileType();
    private JxFileType() { super(JxLanguage.INSTANCE); }
    @Override public @NotNull String getName() { return "JX"; }
    @Override public @NotNull String getDescription() { return "Java component with JX markup"; }
    @Override public @NotNull String getDefaultExtension() { return "jx"; }
    @Override public Icon getIcon() { return AllIcons.FileTypes.Java; }
}
