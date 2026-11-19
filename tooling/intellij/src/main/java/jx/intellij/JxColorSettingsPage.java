package jx.intellij;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/** Markup preferences stay local to JX and inherit HTML colors unless explicitly customized. */
public final class JxColorSettingsPage implements ColorSettingsPage {
    @Override public @NotNull String getDisplayName() { return "JX"; }
    @Override public Icon getIcon() { return JxFileType.INSTANCE.getIcon(); }
    @Override public @NotNull SyntaxHighlighter getHighlighter() { return new JxSyntaxHighlighterFactory().getSyntaxHighlighter(null, null); }
    @Override public @NotNull String getDemoText() {
        return """
                import jx.Component;
                import jx.View;

                @Component
                public final class Welcome {
                    public static View render() {
                        String name = "Java";
                        return (
                            <section class="welcome" aria-label="Greeting">
                                <!-- Markup and Java share one source file. -->
                                <h1>Hello, {name} &amp; JX!</h1>
                                <button onClick={event -> event.preventDefault()}>
                                    Continue
                                </button>
                            </section>
                        );
                    }
                }
                """;
    }
    @Override public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() { return null; }
    @Override public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return new AttributesDescriptor[] {
                new AttributesDescriptor("Markup//Tag and component names", JxColors.TAG),
                new AttributesDescriptor("Markup//Tag delimiters", JxColors.DELIMITER),
                new AttributesDescriptor("Markup//Attribute names", JxColors.ATTRIBUTE),
                new AttributesDescriptor("Markup//Attribute values", JxColors.VALUE),
                new AttributesDescriptor("Markup//Entity references", JxColors.ENTITY)
        };
    }
    @Override public ColorDescriptor @NotNull [] getColorDescriptors() { return ColorDescriptor.EMPTY_ARRAY; }
}
