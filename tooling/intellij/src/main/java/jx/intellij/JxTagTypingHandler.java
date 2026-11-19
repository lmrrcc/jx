package jx.intellij;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import java.util.ArrayDeque;

/** Tag insertion is lexical and local; Java operators, strings and lambdas are untouched. */
public final class JxTagTypingHandler extends TypedHandlerDelegate {
    private static final class Tag {
        final boolean closing;
        String name = "";
        Tag(boolean closing) { this.closing = closing; }
    }
    static String openingTagAt(CharSequence text, int end) {
        var lexer = new JxLexer(); lexer.start(text, 0, end, 0);
        var tags = new ArrayDeque<Tag>();
        while (lexer.getTokenType() != null) {
            String token = text.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
            if (lexer.getTokenType() == JxLexer.MARKUP_DELIMITER) {
                if (token.equals("<") || token.equals("</")) tags.push(new Tag(token.equals("</")));
                else if ((token.equals(">") || token.equals("/>")) && !tags.isEmpty()) {
                    Tag tag = tags.pop();
                    if (lexer.getTokenEnd() == end && !tag.closing && token.equals(">")) return tag.name;
                }
            } else if (lexer.getTokenType() == JxLexer.TAG && !tags.isEmpty()) tags.peek().name = token;
            lexer.advance();
        }
        return null;
    }
    @Override public Result charTyped(char character, Project project, Editor editor, PsiFile file) {
        if (character != '>' || file.getFileType() != JxFileType.INSTANCE
                || !WebEditorOptions.getInstance().isAutomaticallyInsertClosingTag()) return Result.CONTINUE;
        int offset = editor.getCaretModel().getOffset();
        var document = editor.getDocument();
        String name = openingTagAt(document.getCharsSequence(), offset);
        if (name == null) return Result.CONTINUE;
        String closing = "</" + name + ">";
        String following = document.getCharsSequence().subSequence(offset, document.getTextLength()).toString().stripLeading();
        if (!following.startsWith(closing)) document.insertString(offset, closing);
        return Result.STOP;
    }
}
