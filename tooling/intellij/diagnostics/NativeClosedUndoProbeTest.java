package jx.intellij;

import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/** Negative diagnostic control: ordinary Java only, no JX PSI. */
public final class NativeClosedUndoProbeTest extends LightJavaCodeInsightFixtureTestCase {
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }
    public void testNativeJavaRenameAndUndoAcrossOneHundredOneFiles() {
        var target = (PsiJavaFile) myFixture.addFileToProject("sample/Counter.java", "package sample; public class Counter {}");
        var consumers = new java.util.ArrayList<com.intellij.psi.PsiFile>();
        for (int index = 0; index < 100; index++) {
            consumers.add(myFixture.addFileToProject("sample/Page" + index + ".java", "package sample; public class Page" + index + " { Counter value; }"));
        }
        FileDocumentManager.getInstance().saveAllDocuments();
        myFixture.renameElement(target.getClasses()[0], "Meter");
        assertEquals("Meter.java", target.getName());
        for (var consumer : consumers) assertTrue(consumer.getText(), consumer.getText().contains("Meter value"));
        FileDocumentManager.getInstance().saveAllDocuments();
        TestDialogManager.setTestDialog(TestDialog.OK, getTestRootDisposable());
        UndoManager.getInstance(getProject()).undo(null);
        assertEquals("Counter.java", target.getName());
        for (var consumer : consumers) assertTrue(consumer.getText(), consumer.getText().contains("Counter value"));
    }
}
