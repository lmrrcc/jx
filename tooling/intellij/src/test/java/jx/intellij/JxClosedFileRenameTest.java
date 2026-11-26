package jx.intellij;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Real rename/search/undo with closed, saved consumers; exact source preservation. */
public final class JxClosedFileRenameTest extends LightJavaCodeInsightFixtureTestCase {
    private boolean wasTrusted;
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }
    @Override protected void setUp() throws Exception {
        super.setUp();
        myFixture.setCaresAboutInjection(false);
        wasTrusted = TrustedProjects.isTrusted(getProject());
        TrustedProjects.setTrusted(getProject(), false);
        Path core = Path.of(System.getProperty("jx.repositoryRoot")).resolve("jx-core/target/classes");
        PsiTestUtil.addLibrary(getTestRootDisposable(), getModule(), "jx-core", core.getParent().toString(), core.getFileName().toString());
    }
    @Override protected void tearDown() throws Exception {
        try { TrustedProjects.setTrusted(getProject(), wasTrusted); } finally { super.tearDown(); }
    }

    public void testRenameAcrossNinetyNineClosedComponentsAndJavaPreservesSource() throws Exception {
        var app = new JxScaleFixture(myFixture);
        FileDocumentManager.getInstance().saveAllDocuments();
        assertClosed(app);
        assertEquals(100, JxJavaProjection.getInstance(getProject()).snapshot().generated().size());
        long start = System.nanoTime();
        rename(app.counter, "Meter");
        long elapsed = System.nanoTime() - start;
        assertSources(app, "Meter");
        assertClosed(app);
        assertNull(myFixture.findFileInTempDir(JxScaleFixture.ROOT + "scale/widgets/Counter.jx"));
        assertNotNull(myFixture.findFileInTempDir(JxScaleFixture.ROOT + "scale/widgets/Meter.jx"));
        System.out.printf(Locale.ROOT, "JX closed-file rename: 100 components + 1 ordinary Java consumer; %.3fms; exact source passed%n", elapsed / 1_000_000.0);
    }

    public void testClosedStructuralRenameUndoRedoAtTwentyFourComponents() throws Exception {
        // The pinned SDK's unit-test-only dirty-document guard also rejects native Java undo
        // at 101 strongly retained PSI files. Keep that recorded limit separate from rename100.
        var app = new JxScaleFixture(myFixture, 24);
        FileDocumentManager.getInstance().saveAllDocuments();
        assertClosed(app);
        rename(app.counter, "Meter");
        assertSources(app, "Meter");
        var undo = UndoManager.getInstance(getProject());
        TestDialogManager.setTestDialog(TestDialog.OK, getTestRootDisposable());
        assertTrue("All files belong to a single IDE command", undo.isUndoAvailable(null));
        undo.undo(null);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertSources(app, "Counter");
        assertNotNull(myFixture.findFileInTempDir(JxScaleFixture.ROOT + "scale/widgets/Counter.jx"));
        assertNull(myFixture.findFileInTempDir(JxScaleFixture.ROOT + "scale/widgets/Meter.jx"));
        assertTrue(undo.isRedoAvailable(null));
        undo.redo(null);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertSources(app, "Meter");
        assertClosed(app);
    }

    public void testCollisionLeavesEveryClosedSourceAndFileNameUnchanged() throws Exception {
        var app = new JxScaleFixture(myFixture);
        myFixture.addFileToProject("app/src/main/java/scale/widgets/Meter.java", "package scale.widgets; public class Meter {}");
        FileDocumentManager.getInstance().saveAllDocuments();
        assertClosed(app);
        assertThrows(BaseRefactoringProcessor.ConflictsInTestsException.class, () -> rename(app.counter, "Meter"));
        assertSources(app, "Counter");
        assertNull(myFixture.findFileInTempDir(JxScaleFixture.ROOT + "scale/widgets/Meter.jx"));
        assertClosed(app);
    }

    private void rename(PsiFile file, String name) {
        var java = JxJavaProjection.javaFile(file);
        assertNotNull(java);
        var type = java.getClasses()[0];
        PsiElementRenameHandler.rename(type, getProject(), type, null, name);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }
    private void assertClosed(JxScaleFixture app) {
        var files = new ArrayList<>(app.pages);
        files.add(app.counter); files.add(app.caller);
        for (PsiFile file : files) assertFalse("Rename must work without an open editor: " + file.getName(),
                FileEditorManager.getInstance(getProject()).isFileOpen(file.getVirtualFile()));
    }
    private void assertSources(JxScaleFixture app, String symbol) throws Exception {
        assertEquals(symbol + ".jx", app.counter.getName());
        assertEquals(JxScaleFixture.counter(symbol), app.counter.getText());
        for (int index = 0; index < app.pages.size(); index++) assertEquals("Consumer Page" + index,
                JxScaleFixture.page(index, symbol), app.pages.get(index).getText());
        assertEquals(JxScaleFixture.caller(symbol), app.caller.getText());
        FileDocumentManager.getInstance().saveAllDocuments();
        var files = new ArrayList<>(app.pages);
        files.addAll(List.of(app.counter, app.caller));
        for (PsiFile file : files) assertEquals("Saved content must agree with PSI: " + file.getName(), file.getText(),
                new String(file.getVirtualFile().contentsToByteArray(), StandardCharsets.UTF_8));
    }
}
