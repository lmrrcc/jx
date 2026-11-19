package jx.intellij;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import org.jetbrains.annotations.NotNull;

/** Public host-edit contract used by native Java intention and ModCommand transactions. */
public final class JxJavaHostManipulator extends AbstractElementManipulator<JxJavaInjectionHost> {
    @Override public @NotNull JxJavaInjectionHost handleContentChange(@NotNull JxJavaInjectionHost element,
            @NotNull TextRange range, String newContent) {
        String source = element.getText();
        if (range.getStartOffset() < 0 || range.getEndOffset() > source.length())
            throw new com.intellij.util.IncorrectOperationException("JX host edit lies outside its source snapshot");
        element.updateText(source.substring(0, range.getStartOffset()) + newContent + source.substring(range.getEndOffset()));
        return element;
    }

    @Override public @NotNull TextRange getRangeInElement(@NotNull JxJavaInjectionHost element) {
        return new TextRange(0, element.getTextLength());
    }
}
