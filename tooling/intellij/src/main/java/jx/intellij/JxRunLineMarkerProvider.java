package jx.intellij;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiComment;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

/** Starts the containing Maven application's browser workflow, never a JVM main method. */
public final class JxRunLineMarkerProvider implements LineMarkerProvider {
    static final String TOOLTIP = "Run JX browser application (containing Maven module)";

    @Override public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        var file = element.getContainingFile();
        if (element.getFirstChild() != null || file == null || file.getFileType() != JxFileType.INSTANCE
                || !(element.getParent() instanceof JxJavaInjectionHost)) return null;
        var virtual = file.getVirtualFile();
        if (virtual == null || !element.getText().equals(virtual.getNameWithoutExtension())
                || !ProjectFileIndex.getInstance(element.getProject()).isInContent(virtual)) return null;
        var previous = element.getPrevSibling();
        while (previous instanceof PsiWhiteSpace || previous instanceof PsiComment
                || previous != null && previous.getNode().getElementType() == JxLexer.COMMENT) previous = previous.getPrevSibling();
        if (previous == null || previous.getNode().getElementType() != JxLexer.KEYWORD
                || !previous.getText().equals("class")) return null;
        return new LineMarkerInfo<>(element, element.getTextRange(), AllIcons.Actions.Execute,
                ignored -> TOOLTIP, (event, target) -> {
                    var source = target.getContainingFile().getVirtualFile();
                    if (source != null && source.isInLocalFileSystem()
                            && ProjectFileIndex.getInstance(target.getProject()).isInContent(source)) {
                        JxDevelopmentService.getInstance(target.getProject()).start(Path.of(source.getPath()));
                    }
                }, GutterIconRenderer.Alignment.CENTER, () -> TOOLTIP);
    }
}
