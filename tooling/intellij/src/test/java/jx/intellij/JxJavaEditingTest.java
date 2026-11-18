package jx.intellij;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Native Java editor contracts in physical .jx files. These use actual Java PSI, completion,
 * daemon highlighting, reference search and rename; the language server is not the implementation. */
public final class JxJavaEditingTest extends LightJavaCodeInsightFixtureTestCase {
    private PsiFile authoredFile;
    private static final String HELPER = """
            package sample;
            public final class JavaHelper {
                public String joinName(String name) { return name; }
                public String joinLabel(String label) { return label; }
            }
            """;

    @Override protected void setUp() throws Exception {
        super.setUp();
        // Keep the actual .jx editor and authored offsets; normal IDE actions discover its Java
        // injection themselves. Switching the fixture to an EditorWindow would reinterpret
        // these offsets in generated Java and bypass host-level diagnostic mapping.
        myFixture.setCaresAboutInjection(false);
        Path core = Path.of(System.getProperty("jx.repositoryRoot")).resolve("jx-core/target/classes");
        assertTrue("Build the actual JX core before IDE acceptance tests", Files.isRegularFile(core.resolve("jx/ComponentType.class")));
        PsiTestUtil.addLibrary(getTestRootDisposable(), getModule(), "jx-core", core.getParent().toString(), core.getFileName().toString());
        myFixture.addFileToProject("sample/JavaHelper.java", HELPER);
    }

    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }

    public void testNativeJavaStructureAndNavigationOutsideAndInsideMarkup() {
        PsiJavaFile file = configureJx("""
                package sample;
                public final class Component {
                    private final JavaHelper helper = new JavaHelper();
                    public Object render() {
                        String title = helper.joinName("outside");
                        return (<section title={helper.joinLabel(title)}>{helper.joinName(title)}</section>);
                    }
                }
                """);
        assertSame(JxFileType.INSTANCE, myFixture.getFile().getFileType());
        assertEquals(1, file.getClasses().length);
        assertEquals("sample.Component", file.getClasses()[0].getQualifiedName());
        assertEquals(1, file.getClasses()[0].findMethodsByName("render", false).length);
        assertEquals(1, file.getClasses()[0].getFields().length);
        assertMethodReference(file, "joinName(\"outside\")", "joinName");
        assertMethodReference(file, "joinLabel(title)", "joinLabel");
        assertMethodReference(file, "joinName(title)", "joinName");
        var errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
        assertTrue("Valid markup and Java must not produce error diagnostics: " + errors, errors.isEmpty());
    }

    public void testNativeMemberCompletionInJavaMethodBody() {
        configureJx("""
                package sample;
                public final class Component {
                    private final JavaHelper helper = new JavaHelper();
                    public Object render() {
                        String value = helper.jo<caret>;
                        return (<section>{value}</section>);
                    }
                }
                """);
        completeHelperMethod();
    }

    public void testNativeMemberCompletionInMarkupAttributeExpression() {
        configureJx("""
                package sample;
                public final class Component {
                    private final JavaHelper helper = new JavaHelper();
                    public Object render() {
                        return (<section title={helper.jo<caret>}>Title</section>);
                    }
                }
                """);
        completeHelperMethod();
    }

    public void testNativeMemberCompletionInMarkupChildExpression() {
        configureJx("""
                package sample;
                public final class Component {
                    private final JavaHelper helper = new JavaHelper();
                    public Object render() {
                        return (<section>{helper.jo<caret>}</section>);
                    }
                }
                """);
        completeHelperMethod();
    }

    public void testNativeEventCompletionWithMissingInterpolationAndTagDelimiters() {
        configureJx("""
                package sample;
                public final class Component {
                    public Object render() {
                        return (<button onClick={event -> event.<caret>);
                    }
                }
                """);
        completeEventMethod();
    }

    public void testNativeEventCompletionAtUnterminatedFileEnd() {
        configureJx("""
                package sample;
                public final class Component {
                    public Object render() {
                        return <button onClick={event -> event.<caret>\
                """);
        completeEventMethod();
    }

    public void testNativeJavaTypeAndUnresolvedErrorsInBothRegions() {
        configureJx("""
                package sample;
                public final class Component {
                    private final JavaHelper helper = new JavaHelper();
                    public Object render() {
                        int count = "wrong type";
                        String text = unknownLocal;
                        return (<section>{helper.joinName(42)}{missingInMarkup}</section>);
                    }
                }
                """);
        String text = authoredFile.getText();
        List<String> invalid = List.of("\"wrong type\"", "unknownLocal", "helper.joinName(42)", "missingInMarkup");
        var errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
        for (String expression : invalid) {
            int start = text.indexOf(expression), end = start + expression.length();
            assertTrue("Missing native Java error at " + expression + ": " + errors,
                    errors.stream().anyMatch(error -> error.getStartOffset() < end && error.getEndOffset() > start));
        }
        for (var error : errors) {
            assertTrue("Unexpected error outside the intentionally invalid expressions: " + error,
                    invalid.stream().anyMatch(expression -> {
                        int start = text.indexOf(expression), end = start + expression.length();
                        return error.getStartOffset() < end && error.getEndOffset() > start;
                    }));
        }
    }

    public void testNativeJavaInspectionInMethodAndMarkup() {
        myFixture.enableInspections(new com.siyeh.ig.bugs.StringEqualityInspection());
        configureJx("""
                package sample;
                public final class Component {
                    public Object render(String left, String right) {
                        boolean same = left == right;
                        return <span>{left == right}{same}</span>;
                    }
                }
                """);
        var warnings = myFixture.doHighlighting(HighlightSeverity.WARNING);
        String text = authoredFile.getText();
        int first = text.indexOf("left == right"), second = text.lastIndexOf("left == right");
        for (int start : List.of(first, second))
            assertTrue("Missing native String equality inspection: " + warnings,
                    warnings.stream().anyMatch(info -> info.getStartOffset() < start + 13 && info.getEndOffset() > start));
    }

    public void testRenameLocalUpdatesJavaAndMarkupReferencesWithoutChangingText() {
        configureJx("""
                package sample;
                public final class Component {
                    private final JavaHelper helper = new JavaHelper();
                    public Object render() {
                        String <caret>greeting = "greeting";
                        String title = helper.joinName(greeting);
                        return (<section title={greeting}>greeting {helper.joinLabel(greeting)}{title}</section>);
                    }
                }
                """);
        assertTrue("Rename must target a Java local variable", myFixture.getElementAtCaret() instanceof PsiLocalVariable);
        myFixture.renameElementAtCaret("salutation");
        myFixture.checkResult("""
                package sample;
                public final class Component {
                    private final JavaHelper helper = new JavaHelper();
                    public Object render() {
                        String salutation = "greeting";
                        String title = helper.joinName(salutation);
                        return (<section title={salutation}>greeting {helper.joinLabel(salutation)}{title}</section>);
                    }
                }
                """, true);
    }

    public void testRenameDeclaredMethodUpdatesJavaAndMarkupCalls() {
        configureJx("""
                package sample;
                public final class Component {
                    private String <caret>format(String text) { return text; }
                    public Object render() {
                        String title = format("outside");
                        return (<section title={format(title)}>{format("inside")}</section>);
                    }
                }
                """);
        assertTrue("Rename must target a Java method declaration", myFixture.getElementAtCaret() instanceof PsiMethod);
        myFixture.renameElementAtCaret("display");
        myFixture.checkResult("""
                package sample;
                public final class Component {
                    private String display(String text) { return text; }
                    public Object render() {
                        String title = display("outside");
                        return (<section title={display(title)}>{display("inside")}</section>);
                    }
                }
                """, true);
    }

    public void testFindUsagesAndRenameAcrossJavaAndJxFiles() {
        PsiFile consumer = myFixture.addFileToProject("sample/JavaConsumer.java", """
                package sample;
                public final class JavaConsumer {
                    public String render(JavaHelper helper) { return helper.joinName("java"); }
                }
                """);
        PsiJavaFile component = configureJx("""
                package sample;
                public final class Component {
                    private final JavaHelper helper = new JavaHelper();
                    public Object render() {
                        String title = helper.<caret>joinName("outside");
                        return (<section>{helper.joinName(title)}</section>);
                    }
                }
                """);
        PsiMethod target = assertMethodReference(component, "joinName(\"outside\")", "joinName");
        var usages = myFixture.findUsages(target);
        assertEquals("Expected one Java and two JX usages", 3, usages.size());
        List<String> usageFiles = usages.stream().map(usage -> {
            assertNotNull("Every usage must identify actual PSI", usage.getElement());
            return InjectedLanguageManager.getInstance(getProject()).getTopLevelFile(usage.getElement()).getName();
        }).toList();
        assertEquals(2, usageFiles.stream().filter(name -> name.equals("Component.jx")).count());
        assertEquals(1, usageFiles.stream().filter(name -> name.equals("JavaConsumer.java")).count());
        myFixture.renameElement(target, "formatName");
        assertTrue(consumer.getText().contains("helper.formatName(\"java\")"));
        assertTrue(authoredFile.getText().contains("helper.formatName(\"outside\")"));
        assertTrue(authoredFile.getText().contains("helper.formatName(title)"));
        assertFalse(authoredFile.getText().contains("joinName"));
        assertEquals("formatName", target.getName());
    }

    private PsiJavaFile configureJx(String markedText) {
        int caret = markedText.indexOf("<caret>");
        PsiFile file = myFixture.addFileToProject("sample/Component.jx", markedText.replace("<caret>", ""));
        authoredFile = file;
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        if (caret >= 0) myFixture.getEditor().getCaretModel().moveToOffset(caret);
        int anchor = caret >= 0 ? caret : file.getText().indexOf("public final class");
        assertTrue("The fixture must contain a Java anchor", anchor >= 0);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(file, Math.min(anchor, file.getTextLength() - 1));
        assertNotNull("Authored Java must map into the full-class Java injection", injected);
        PsiFile javaView = injected.getContainingFile();
        assertTrue("JX must expose real Java PSI, got " + javaView.getClass().getName(), javaView instanceof PsiJavaFile);
        return (PsiJavaFile) javaView;
    }

    private PsiMethod assertMethodReference(PsiJavaFile file, String occurrence, String expectedName) {
        int offset = authoredFile.getText().indexOf(occurrence);
        assertTrue("Missing reference fixture: " + occurrence, offset >= 0);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(authoredFile, offset);
        assertNotNull("Authored reference must map to Java PSI at " + occurrence, injected);
        assertSame("All Java regions must share one class scope", file, injected.getContainingFile());
        PsiReference reference = file.findReferenceAt(injected.getTextOffset());
        assertNotNull("Expected a Java reference at " + occurrence, reference);
        assertTrue("References must be native Java PSI", reference.getElement() instanceof PsiReferenceExpression);
        PsiElement resolved = reference.resolve();
        assertTrue("Reference did not resolve to a Java method: " + occurrence, resolved instanceof PsiMethod);
        PsiMethod method = (PsiMethod) resolved;
        assertEquals(expectedName, method.getName());
        assertNotNull(method.getContainingClass());
        assertEquals("sample.JavaHelper", method.getContainingClass().getQualifiedName());
        return method;
    }

    private void completeHelperMethod() {
        LookupElement[] lookup = myFixture.completeBasic();
        assertNotNull("Java member completion must provide a lookup", lookup);
        List<String> names = Arrays.stream(lookup).map(LookupElement::getLookupString).toList();
        assertTrue("Missing Java joinName completion: " + names, names.contains("joinName"));
        assertTrue("Missing Java joinLabel completion: " + names, names.contains("joinLabel"));
        LookupElement selected = Arrays.stream(lookup).filter(item -> item.getLookupString().equals("joinName")).findFirst().orElseThrow();
        assertTrue("Completion must originate from native Java method PSI", selected.getPsiElement() instanceof PsiMethod);
        myFixture.getLookup().setCurrentItem(selected);
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        assertTrue("Selecting Java completion must insert the member call", authoredFile.getText().contains("helper.joinName()"));
        assertFalse("Completion placeholder must be consumed", authoredFile.getText().contains("helper.jo;"));
    }

    private void completeEventMethod() {
        LookupElement[] lookup = myFixture.completeBasic();
        assertNotNull("An unfinished event lambda must retain native Java completion", lookup);
        List<String> names = Arrays.stream(lookup).map(LookupElement::getLookupString).toList();
        assertTrue("Missing inferred Event.preventDefault completion: " + names, names.contains("preventDefault"));
        assertTrue("Missing inferred Event.targetValue completion: " + names, names.contains("targetValue"));
        LookupElement selected = Arrays.stream(lookup).filter(item -> item.getLookupString().equals("preventDefault"))
                .findFirst().orElseThrow();
        assertTrue("The callback type must come from Java PSI", selected.getPsiElement() instanceof PsiMethod);
        PsiMethod method = (PsiMethod) selected.getPsiElement();
        assertNotNull(method.getContainingClass());
        assertEquals("jx.Event", method.getContainingClass().getQualifiedName());
        myFixture.getLookup().setCurrentItem(selected);
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        assertTrue("Completion must edit the authored event expression", authoredFile.getText().contains("event.preventDefault()"));
    }
}
