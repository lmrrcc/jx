package jx.intellij;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;

/** Splits paired tags using the project's Java indent; ordinary Java keeps its native handler. */
public final class JxEnterHandler implements EnterHandlerDelegate {
    @Override public Result preprocessEnter(PsiFile file, Editor editor, Ref<Integer> caretOffset,
            Ref<Integer> caretAdvance, DataContext context, EditorActionHandler originalHandler) {
        if (file.getFileType() != JxFileType.INSTANCE || !CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER)
            return Result.Continue;
        int offset = caretOffset.get();
        var document = editor.getDocument();
        CharSequence text = document.getCharsSequence();
        String name = JxTagTypingHandler.openingTagAt(text, offset);
        if (name == null || !text.subSequence(offset, text.length()).toString().startsWith("</" + name + ">"))
            return Result.Continue;
        int lineStart = document.getLineStartOffset(document.getLineNumber(offset)), indentEnd = lineStart;
        while (indentEnd < offset && (text.charAt(indentEnd) == ' ' || text.charAt(indentEnd) == '\t')) indentEnd++;
        String indent = text.subSequence(lineStart, indentEnd).toString();
        var options = CodeStyle.getSettings(file).getCommonSettings(JavaLanguage.INSTANCE).getIndentOptions();
        String unit = options == null ? "    " : options.USE_TAB_CHARACTER ? "\t" : " ".repeat(Math.max(0, options.INDENT_SIZE));
        String first = "\n" + indent + unit;
        document.insertString(offset, first + "\n" + indent);
        editor.getCaretModel().moveToOffset(offset + first.length());
        return Result.Stop;
    }
}
