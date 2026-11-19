package jx.intellij;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exercises host registrations and actual authored-editor navigation/fold regions. */
public final class JxStructureAndFoldingTest extends LightJavaCodeInsightFixtureTestCase {
    private static final String SOURCE = """
            package sample;
            @jx.Component public final class Counter {
                public record Props(String label) {}
                private static int total = 0;
                public static jx.View render(Props props) {
                    return <section>
                        <button onClick={event -> {
                            total++;
                        }}>{props.label()}</button>
                    </section>;
                }
                public static int value() { return total; }
            }
            """;
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }
    @Override protected void setUp() throws Exception {
        super.setUp();
        myFixture.setCaresAboutInjection(false);
        Path core = Path.of(System.getProperty("jx.repositoryRoot")).resolve("jx-core/target/classes");
        PsiTestUtil.addLibrary(getTestRootDisposable(), getModule(), "jx-core", core.getParent().toString(), core.getFileName().toString());
    }
    private PsiFile configure(String text) {
        var file = myFixture.addFileToProject("sample/Counter.jx", text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return file;
    }
    public void testNativeHostStructureFiltersGeneratedMembersAndNavigatesToAuthoredMethod() {
        var file = configure(SOURCE);
        var builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(file);
        assertTrue("JX host must register native Structure support", builder instanceof TreeBasedStructureViewBuilder);
        var model = ((TreeBasedStructureViewBuilder) builder).createStructureViewModel(myFixture.getEditor());
        try {
            var entries = flatten(model.getRoot());
            List<String> names = entries.stream().map(entry -> entry.getPresentation().getPresentableText()).toList();
            assertContainsElements(names, "Counter", "Props", "label: String", "total: int", "render(Props)", "value()");
            assertFalse("Synthetic descriptors are not authored members: " + names, names.stream().anyMatch(name -> name.startsWith("JX")));
            var render = entries.stream().filter(entry -> "render(Props)".equals(entry.getPresentation().getPresentableText())).findFirst().orElseThrow();
            ((StructureViewTreeElement) render).navigate(true);
            assertEquals(file.getVirtualFile(), com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(myFixture.getEditor().getDocument()));
            assertEquals(SOURCE.indexOf("render(Props"), myFixture.getEditor().getCaretModel().getOffset());
            assertEquals(render.getValue(), model.getCurrentEditorElement());
            myFixture.getEditor().getCaretModel().moveToOffset(SOURCE.indexOf("<button"));
            assertEquals("Structure selection must retain the owning Java method in markup", render.getValue(), model.getCurrentEditorElement());
        } finally { Disposer.dispose(model); }
    }
    public void testNativeHostFoldingIncludesJavaBodiesAndMarkup() {
        var file = configure(SOURCE);
        CodeFoldingManager.getInstance(getProject()).updateFoldRegions(myFixture.getEditor());
        var regions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
        List<String> contents = Arrays.stream(regions).map(region -> file.getText().substring(region.getStartOffset(), region.getEndOffset())).toList();
        assertTrue(contents.toString(), contents.stream().anyMatch(text -> text.startsWith("<section>") && text.endsWith("</section>")));
        assertTrue(contents.toString(), contents.stream().anyMatch(text -> text.startsWith("{\n        return <section>")));
        assertTrue(contents.toString(), contents.stream().anyMatch(text -> text.startsWith("{\n                total++;")));
        assertTrue(Arrays.stream(regions).allMatch(region -> region.getEndOffset() <= file.getTextLength()));
        myFixture.getEditor().getCaretModel().moveToOffset(0);
        myFixture.performEditorAction("CollapseAllRegions");
        assertTrue("The native collapse action must affect JX folds", Arrays.stream(regions).anyMatch(region -> !region.isExpanded()));
        myFixture.performEditorAction("ExpandAllRegions");
        assertTrue(Arrays.stream(regions).allMatch(region -> region.isExpanded()));
    }
    public void testLeadingWhitespaceUsesFileCoordinatesForNavigationSelectionAndFolding() {
        String text = "\n  \n  " + SOURCE + "\n  \n";
        var file = configure(text);
        assertTrue("Fixture must exercise a host after leading whitespace", JxJavaProjection.host(file).getTextRange().getStartOffset() > 0);
        var builder = (TreeBasedStructureViewBuilder) LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(file);
        var model = builder.createStructureViewModel(myFixture.getEditor());
        try {
            var render = flatten(model.getRoot()).stream()
                    .filter(entry -> "render(Props)".equals(entry.getPresentation().getPresentableText())).findFirst().orElseThrow();
            render.navigate(true);
            assertEquals(text.indexOf("render(Props"), myFixture.getEditor().getCaretModel().getOffset());
            myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("<button"));
            assertEquals(render.getValue(), model.getCurrentEditorElement());
            CodeFoldingManager.getInstance(getProject()).updateFoldRegions(myFixture.getEditor());
            var contents = Arrays.stream(myFixture.getEditor().getFoldingModel().getAllFoldRegions())
                    .map(region -> text.substring(region.getStartOffset(), region.getEndOffset())).toList();
            assertTrue(contents.toString(), contents.stream().anyMatch(fold -> fold.startsWith("{\n        return <section>")));
            assertTrue(contents.toString(), contents.stream().anyMatch(fold -> fold.startsWith("<section>") && fold.endsWith("</section>")));
        } finally { Disposer.dispose(model); }
    }
    public void testStructureTracksUnsavedHostEdits() {
        var file = configure(SOURCE);
        myFixture.getEditor().getCaretModel().moveToOffset(SOURCE.indexOf("value()"));
        myFixture.getEditor().getSelectionModel().setSelection(SOURCE.indexOf("value()"), SOURCE.indexOf("value()") + 5);
        myFixture.type("currentValue");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        var builder = (TreeBasedStructureViewBuilder) LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(file);
        var model = builder.createStructureViewModel(myFixture.getEditor());
        try {
            var names = flatten(model.getRoot()).stream().map(entry -> entry.getPresentation().getPresentableText()).toList();
            assertContainsElements(names, "currentValue()");
            assertFalse(names.contains("value()"));
        } finally { Disposer.dispose(model); }
    }
    private static List<StructureViewTreeElement> flatten(StructureViewTreeElement element) {
        var result = new ArrayList<StructureViewTreeElement>();
        result.add(element);
        for (TreeElement child : element.getChildren()) result.addAll(flatten((StructureViewTreeElement) child));
        return result;
    }
}
