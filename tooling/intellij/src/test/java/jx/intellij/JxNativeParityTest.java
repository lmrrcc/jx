package jx.intellij;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.nio.file.Path;

/** Invokes ordinary editor actions on the authored document, without selecting generated PSI. */
public final class JxNativeParityTest extends LightJavaCodeInsightFixtureTestCase {
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }

    @Override protected void setUp() throws Exception {
        super.setUp();
        myFixture.setCaresAboutInjection(false);
        Path core = Path.of(System.getProperty("jx.repositoryRoot")).resolve("jx-core/target/classes");
        PsiTestUtil.addLibrary(getTestRootDisposable(), getModule(), "jx-core", core.getParent().toString(), core.getFileName().toString());
        myFixture.addFileToProject("widgets/Counter.jx", """
                package widgets;
                @jx.Component public final class Counter {
                    public record Props(String label) {}
                    public static jx.View render(Props props) { return <span>{props.label()}</span>; }
                }
                """);
    }

    public void testOptimizeImportsFromJavaRetainsMarkupOnlyComponentImports() {
        optimizeImports("render");
    }

    public void testOptimizeImportsFromMarkupRetainsMarkupOnlyComponentImports() {
        optimizeImports("Counter label");
    }

    private void optimizeImports(String caretToken) {
        var file = myFixture.addFileToProject("sample/Screen.jx", """
                package sample;
                import java.util.ArrayList;
                import widgets.Counter;
                import jx.Component;
                import jx.View;
                @Component public final class Screen {
                    public static View render() { return <Counter label="Hello"/>; }
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(file.getText().indexOf(caretToken));
        myFixture.performEditorAction("OptimizeImports");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertFalse("Optimize Imports must remove an unused ordinary Java import: " + file.getText(),
                file.getText().contains("import java.util.ArrayList;"));
        assertTrue("A component used only in a tag still needs its import", file.getText().contains("import widgets.Counter;"));
        assertTrue(file.getText().contains("import jx.Component;"));
        assertTrue(file.getText().contains("<Counter label=\"Hello\"/>"));
        var errors = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.ERROR);
        assertTrue("The resulting markup must retain valid Java semantics: " + errors + "\n" + file.getText(), errors.isEmpty());
    }

    public void testAddImportIntentionEditsOriginalJavaAndLeavesMarkupIntact() {
        var file = myFixture.addFileToProject("sample/Screen.jx", """
                package sample;
                public final class Screen {
                    public Object render() {
                        ArrayList<String> names = new ArrayList<>();
                        return <span>{names.size()}</span>;
                    }
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(file.getText().indexOf("ArrayList") + 2);
        var actions = myFixture.getAvailableIntentions();
        var action = actions.stream().filter(candidate -> candidate.getText().startsWith("Import class"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing native import intention: "
                        + actions.stream().map(candidate -> candidate.getText()).toList()));
        myFixture.launchAction(action);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertTrue(file.getText(), file.getText().contains("import java.util.ArrayList;"));
        assertTrue(file.getText(), file.getText().contains("<span>{names.size()}</span>"));
    }

    public void testOptimizeImportsPreservesCommentsAndUndoesAsOneAction() {
        String source = """
                package sample;
                import java.util.ArrayList;
                // Component from the widgets package.
                import widgets.Counter;
                import jx.View;
                public final class Screen {
                    public static View render() { return <Counter label="Hello"/>; }
                }
                """;
        var file = myFixture.addFileToProject("sample/Screen.jx", source);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(file.getText().indexOf("Counter label"));
        myFixture.performEditorAction("OptimizeImports");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertFalse(file.getText(), file.getText().contains("import java.util.ArrayList;"));
        assertTrue(file.getText(), file.getText().contains("// Component from the widgets package."));
        myFixture.performEditorAction("$Undo");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertEquals(source, file.getText());
    }

    public void testIncompleteMarkupDoesNotDeleteImportsBasedOnRecoveredJava() {
        incompleteMarkupPreservesImports("Counter label");
    }

    public void testIncompleteMarkupDoesNotDeleteImportsFromJavaCaretEither() {
        incompleteMarkupPreservesImports("render");
    }

    private void incompleteMarkupPreservesImports(String caretToken) {
        var file = myFixture.addFileToProject("sample/Screen.jx", """
                package sample;
                import widgets.Counter;
                public final class Screen { Object render() { return <Counter label=; } }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(file.getText().indexOf(caretToken));
        String before = file.getText();
        myFixture.performEditorAction("OptimizeImports");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertEquals(before, file.getText());
    }
}
