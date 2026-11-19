package jx.intellij;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import java.util.Optional;
import jx.compiler.GeneratedSource;
import jx.compiler.SourceSpan;

/** Read-action mapping for navigation and display; never authorizes a source edit. */
final class JxSourceNavigation {
    private JxSourceNavigation() {}

    record View(PsiFile authored, PsiJavaFile javaFile, GeneratedSource source, int hostOffset) {
        Optional<TextRange> copied(PsiElement element) {
            if (element == null || !element.isValid()) return Optional.empty();
            TextRange range = element.getTextRange();
            return source.sourceMap().editableSourceSpan(new SourceSpan(range.getStartOffset(), range.getEndOffset()))
                    .map(span -> new TextRange(hostOffset + span.start(), hostOffset + span.end()));
        }

        Optional<TextRange> enclosing(PsiElement first, PsiElement last) {
            return copied(first).flatMap(start -> copied(last)
                    .filter(end -> end.getEndOffset() > start.getStartOffset())
                    .map(end -> new TextRange(start.getStartOffset(), end.getEndOffset())));
        }

        Optional<TextRange> extent(PsiElement element) {
            TextRange range = element.getTextRange();
            if (range.isEmpty()) return Optional.empty();
            var start = source.sourceMap().editableSourceSpan(new SourceSpan(range.getStartOffset(), range.getStartOffset() + 1));
            var end = source.sourceMap().editableSourceSpan(new SourceSpan(range.getEndOffset() - 1, range.getEndOffset()));
            return start.flatMap(first -> end.filter(last -> last.end() > first.start())
                    .map(last -> new TextRange(hostOffset + first.start(), hostOffset + last.end())));
        }
    }

    static Optional<View> view(PsiFile file) {
        if (file == null || !file.isValid()) return Optional.empty();
        var host = JxJavaProjection.host(file);
        if (host == null) return Optional.empty();
        var source = JxJavaProjection.getInstance(file.getProject()).forHost(host);
        if (source.isEmpty()) return Optional.empty();
        var javaFile = JxJavaProjection.javaFile(file);
        if (javaFile == null || !javaFile.getText().equals(source.get().javaSource())) return Optional.empty();
        return Optional.of(new View(file, javaFile, source.get(), host.getTextRange().getStartOffset()));
    }
}
