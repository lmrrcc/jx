package jx.intellij;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.nio.file.Path;

public final class JxMarkupEditingTest extends LightJavaCodeInsightFixtureTestCase {
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }
    @Override protected void setUp() throws Exception {
        super.setUp();
        Path core = Path.of(System.getProperty("jx.repositoryRoot")).resolve("jx-core/target/classes");
        PsiTestUtil.addLibrary(getTestRootDisposable(), getModule(), "jx-core", core.getParent().toString(), core.getFileName().toString());
        myFixture.addFileToProject("sample/Counter.jx", """
                package sample;
                @jx.Component public final class Counter {
                    public Counter() {}
                    public record Props(String label) {}
                    public static jx.View render(Props props) { return <span>{props.label()}</span>; }
                }
                """);
    }
    private com.intellij.psi.PsiFile app() {
        return myFixture.addFileToProject("sample/App.jx", """
                package sample;
                @jx.Component public final class App {
                    public static jx.View render() { return <Counter label={"Hello"}></Counter>; }
                }
                """);
    }
    public void testComponentAndPropertyReferencesUseNativeDeclarations() {
        var file = app(); var host = JxJavaProjection.host(file);
        assertNotNull(host);
        var references = PsiReferenceService.getService().getReferences(host, PsiReferenceService.Hints.NO_HINTS);
        assertEquals(3, references.size());
        int tags = 0, properties = 0;
        for (PsiReference reference : references) {
            var target = reference.resolve(); assertNotNull(reference.toString(), target);
            if (target instanceof PsiClass type) { tags++; assertEquals("sample.Counter", type.getQualifiedName()); }
            if (target instanceof PsiRecordComponent property) { properties++; assertEquals("label", property.getName()); }
        }
        assertEquals(2, tags); assertEquals(1, properties);
        var classes = PsiShortNamesCache.getInstance(getProject()).getClassesByName("Counter", GlobalSearchScope.projectScope(getProject()));
        assertEquals(1, classes.length);
        assertTrue(InjectedLanguageManager.getInstance(getProject()).getInjectionHost(classes[0]) instanceof JxJavaInjectionHost);
    }
    public void testNativeClassRenameUpdatesBothTagsAndAuthoredFile() {
        var app = app();
        var caller = myFixture.addFileToProject("other/Use.java", """
                package other;
                import sample.Counter;
                public class Use { public Counter make() { return new Counter(); } }
                """);
        var source = myFixture.findFileInTempDir("sample/Counter.jx");
        var file = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(source);
        var type = JxJavaProjection.javaFile(file).getClasses()[0];
        com.intellij.refactoring.rename.PsiElementRenameHandler.rename(type, getProject(), type, null, "Meter");
        assertTrue(app.getText(), app.getText().contains("<Meter label={\"Hello\"}></Meter>"));
        assertNotNull(myFixture.findFileInTempDir("sample/Meter.jx"));
        assertNull(myFixture.findFileInTempDir("sample/Counter.jx"));
        assertTrue(file.getText(), file.getText().contains("public Meter()"));
        assertTrue(caller.getText(), caller.getText().contains("import sample.Meter;"));
        assertTrue(caller.getText(), caller.getText().contains("public Meter make() { return new Meter(); }"));
        var undo = com.intellij.openapi.command.undo.UndoManager.getInstance(getProject());
        assertTrue("The complete rename must be one undoable IDE operation", undo.isUndoAvailable(null));
        com.intellij.openapi.ui.TestDialogManager.setTestDialog(com.intellij.openapi.ui.TestDialog.OK, getTestRootDisposable());
        undo.undo(null);
        com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertNotNull(myFixture.findFileInTempDir("sample/Counter.jx"));
        assertNull(myFixture.findFileInTempDir("sample/Meter.jx"));
        assertTrue(app.getText(), app.getText().contains("<Counter label={\"Hello\"}></Counter>"));
        assertTrue(caller.getText(), caller.getText().contains("import sample.Counter;"));
    }
    public void testClassNameCollisionRejectsEveryEdit() {
        var app = app();
        myFixture.addFileToProject("sample/Meter.java", "package sample; public class Meter {}");
        var source = myFixture.findFileInTempDir("sample/Counter.jx");
        var file = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(source);
        var type = JxJavaProjection.javaFile(file).getClasses()[0];
        String beforeApp = app.getText(), beforeClass = file.getText();
        assertThrows(com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException.class,
                () -> com.intellij.refactoring.rename.PsiElementRenameHandler.rename(type, getProject(), type, null, "Meter"));
        assertEquals(beforeApp, app.getText()); assertEquals(beforeClass, file.getText());
        assertEquals("Counter.jx", file.getName());
    }
    public void testClassRenameCannotTurnAComponentTagIntoAnHtmlElement() {
        var app = app();
        var file = com.intellij.psi.PsiManager.getInstance(getProject())
                .findFile(myFixture.findFileInTempDir("sample/Counter.jx"));
        var type = JxJavaProjection.javaFile(file).getClasses()[0];
        String beforeApp = app.getText(), beforeClass = file.getText();
        assertThrows(com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException.class,
                () -> com.intellij.refactoring.rename.PsiElementRenameHandler.rename(type, getProject(), type, null, "button"));
        assertEquals(beforeApp, app.getText()); assertEquals(beforeClass, file.getText());
        assertEquals("Counter.jx", file.getName());
    }
    public void testNativeRecordPropertyRenameUpdatesMarkupAttribute() {
        var app = app();
        var source = myFixture.findFileInTempDir("sample/Counter.jx");
        var file = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(source);
        var props = JxJavaProjection.javaFile(file).getClasses()[0].findInnerClassByName("Props", false);
        assertNotNull(props);
        myFixture.renameElement(props.getRecordComponents()[0], "title");
        assertTrue(app.getText(), app.getText().contains("<Counter title={\"Hello\"}></Counter>"));
        assertTrue(file.getText(), file.getText().contains("Props(String title)"));
        assertTrue(file.getText(), file.getText().contains("props.title()"));
    }
    public void testNativeJavaCompletionImportsJxClass() {
        myFixture.configureByText("Use.java", "package other; public class Use { Coun<caret> field; }");
        var items = myFixture.completeBasic();
        if (items != null) {
            var choice = java.util.Arrays.stream(items).filter(item -> item.getLookupString().equals("Counter")).findFirst().orElseThrow();
            myFixture.getLookup().setCurrentItem(choice);
            myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR);
        }
        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("import sample.Counter;"));
        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("Counter field"));
    }
    public void testNativeCompletionAddsImportToAuthoredJx() {
        myFixture.setCaresAboutInjection(false);
        var file = myFixture.addFileToProject("sample/Use.jx", """
                package sample;
                public class Use {
                    public Object render() {
                        Object values = new ArrayL();
                        return <span>{values}</span>;
                    }
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(file.getText().indexOf("ArrayL") + "ArrayL".length());
        var items = myFixture.completeBasic();
        assertNotNull(items);
        var choice = java.util.Arrays.stream(items).filter(item -> item.getLookupString().equals("ArrayList")).findFirst().orElseThrow();
        myFixture.getLookup().setCurrentItem(choice);
        myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR);
        assertTrue(file.getText(), file.getText().contains("import java.util.ArrayList;"));
        assertTrue(file.getText(), file.getText().contains("<span>{values}</span>"));
    }
    public void testRenameWithReorderedPropertyExpressionsKeepsSourceOrder() {
        myFixture.addFileToProject("sample/Pair.jx", """
                package sample;
                @jx.Component public final class Pair {
                    public record Props(String first, String second) {}
                    public static jx.View render(Props props) { return <span>{props.first()}{props.second()}</span>; }
                }
                """);
        var file = myFixture.addFileToProject("sample/Use.jx", """
                package sample;
                public class Use {
                    public Object render() {
                        String greeting = "hello";
                        return <Pair second={greeting + "second"} first={greeting + "first"} />;
                    }
                }
                """);
        var java = JxJavaProjection.javaFile(file);
        var local = com.intellij.psi.util.PsiTreeUtil.findChildOfType(java, com.intellij.psi.PsiLocalVariable.class);
        assertNotNull(local);
        myFixture.renameElement(local, "message");
        assertTrue(file.getText(), file.getText().contains("String message = \"hello\";"));
        assertTrue(file.getText(), file.getText().contains("<Pair second={message + \"second\"} first={message + \"first\"} />"));
    }
}
