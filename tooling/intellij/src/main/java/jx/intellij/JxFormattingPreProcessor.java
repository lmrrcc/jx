package jx.intellij;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor;
import java.util.ArrayList;
import java.util.List;
import jx.compiler.SourceSpan;
import org.jetbrains.annotations.NotNull;

/** Uses the bundled Java formatter, accepting only whitespace edits wholly in copied Java.
 * The generated compilation unit is detached: generated wrappers never become authored edits. */
public final class JxFormattingPreProcessor implements PreFormatProcessor {
    static final Key<String> JAVA_FORMATTED_SOURCE = Key.create("jx.native.formatted.source");
    private record Token(int start, int end, String text) {}
    private record Edit(int start, int end, String text) {}

    @Override public @NotNull TextRange process(@NotNull ASTNode node, @NotNull TextRange range) {
        var file = node.getPsi().getContainingFile();
        if (file == null || file.getLanguage() != JxLanguage.INSTANCE) return range;
        file.putUserData(JAVA_FORMATTED_SOURCE, null);
        var host = JxJavaProjection.host(file);
        if (host == null || host.getTextLength() > 1_048_576) return range;
        var projected = JxJavaProjection.getInstance(file.getProject()).forHost(host);
        if (projected.isEmpty()) return range;
        var source = projected.orElseThrow();
        if (source.javaSource().length() > 2_097_152) return range;
        var copy = PsiFileFactory.getInstance(file.getProject()).createFileFromText(
                "JxFormatting.java", JavaFileType.INSTANCE, source.javaSource(), 0, false);
        CodeStyleManager.getInstance(file.getProject()).reformat(copy);
        String formatted = copy.getText();
        var before = tokens(source.javaSource());
        var after = tokens(formatted);
        // A formatter may also transform Javadoc or text-block contents. Never map those edits
        // using token indices: require an identical token stream, otherwise leave Java unchanged.
        if (before.size() != after.size()) return range;
        for (int index = 0; index < before.size(); index++)
            if (!before.get(index).text().equals(after.get(index).text())) return range;
        var edits = new ArrayList<Edit>();
        String authored = host.getText();
        for (int index = 1; index < before.size(); index++) {
            ProgressManager.checkCanceled();
            Token previous = before.get(index - 1), next = before.get(index);
            // Include both neighboring characters so insertion at a projection boundary is
            // not mistaken for permission to change markup or generated constructor syntax.
            var mapped = source.sourceMap().editableSourceSpan(new SourceSpan(previous.end() - 1, next.start() + 1));
            if (mapped.isEmpty()) continue;
            int start = mapped.orElseThrow().start() + 1, end = mapped.orElseThrow().end() - 1;
            int hostOffset = host.getTextRange().getStartOffset();
            if (!range.containsRange(hostOffset + start, hostOffset + end)) continue;
            String whitespace = formatted.substring(after.get(index - 1).end(), after.get(index).start());
            if (!whitespace.equals(authored.substring(start, end))) edits.add(new Edit(start, end, whitespace));
        }
        if (edits.isEmpty()) {
            file.putUserData(JAVA_FORMATTED_SOURCE, file.getText());
            return range;
        }
        var replacement = new StringBuilder(authored);
        for (int index = edits.size() - 1; index >= 0; index--) {
            Edit edit = edits.get(index);
            replacement.replace(edit.start(), edit.end(), edit.text());
        }
        // Replacing the host is a PSI edit, but the explicit formatter below owns its layout.
        // Suppress automatic post-PSI formatting of the entire host, especially for selections.
        CodeStyleManager.getInstance(file.getProject()).performActionWithFormatterDisabled((Runnable) () -> {
            host.updateText(replacement.toString());
        });
        file.putUserData(JAVA_FORMATTED_SOURCE, file.getText());
        return new TextRange(range.getStartOffset(), range.getEndOffset() + replacement.length() - authored.length());
    }

    @Override public boolean changesWhitespacesOnly() { return true; }

    private static List<Token> tokens(String source) {
        var lexer = new JavaLexer(LanguageLevel.JDK_25);
        lexer.start(source);
        var result = new ArrayList<Token>();
        while (lexer.getTokenType() != null) {
            ProgressManager.checkCanceled();
            if (lexer.getTokenType() != TokenType.WHITE_SPACE)
                result.add(new Token(lexer.getTokenStart(), lexer.getTokenEnd(),
                        source.substring(lexer.getTokenStart(), lexer.getTokenEnd())));
            lexer.advance();
        }
        return result;
    }
}
