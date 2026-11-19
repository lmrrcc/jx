package jx.intellij;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.util.Arrays;

/** Real compiler projections with deterministic reuse assertions; timings are evidence, not gates. */
public final class JxProjectionPerformanceTest extends LightJavaCodeInsightFixtureTestCase {
    private boolean wasTrusted;
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }
    @Override protected void setUp() throws Exception {
        super.setUp(); myFixture.setCaresAboutInjection(false);
        wasTrusted = TrustedProjects.isTrusted(getProject());
        TrustedProjects.setTrusted(getProject(), false);
    }
    @Override protected void tearDown() throws Exception {
        try { TrustedProjects.setTrusted(getProject(), wasTrusted); } finally { super.tearDown(); }
    }
    private PsiFile component(String module, String name, String body) {
        return myFixture.addFileToProject(module + "/src/main/jx/sample/" + name + ".jx",
                "\npackage sample;\n@jx.Component public final class " + name + " {\n"
                        + "public static jx.View render() { return " + body + "; }\n}\n");
    }
    private void module(String name) {
        myFixture.addFileToProject(name + "/pom.xml", "<project><modelVersion>4.0.0</modelVersion></project>");
    }
    private void replace(PsiFile file, String before, String after) {
        var document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        assertNotNull(document);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            int offset = document.getText().indexOf(before);
            assertTrue(offset >= 0);
            document.replaceString(offset, offset + before.length(), after);
            PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        });
    }
    public void testHostWhitespaceDoesNotTriggerRepeatedCompilerProjection() {
        module("first"); module("second");
        PsiFile app = component("first", "App", "<Counter/>");
        component("first", "Counter", "<span>One</span>");
        component("second", "Screen", "<Other/>");
        component("second", "Other", "<span>Two</span>");
        var service = JxJavaProjection.getInstance(getProject());
        var state = service.snapshot();
        var host = JxJavaProjection.host(app);
        assertNotNull(host);
        assertFalse("Fixture must reproduce file/host whitespace mismatch", app.getText().equals(host.getText()));
        for (int index = 0; index < 3; index++) service.forHost(host).orElseThrow();
        long[] nanos = new long[20];
        for (int index = 0; index < nanos.length; index++) {
            long start = System.nanoTime(); service.forHost(host).orElseThrow(); nanos[index] = System.nanoTime() - start;
        }
        Arrays.sort(nanos);
        System.out.printf("JX repeated host projection: 4 source files / 2 Maven roots, 3 warmups, 20 samples; p50=%.3fms p95=%.3fms%n",
                nanos[9] / 1_000_000.0, nanos[18] / 1_000_000.0);
        var projected = service.forHost(host).orElseThrow();
        assertSame("Physical host must reuse the canonical projection despite outer whitespace",
                state.generated().get(JxJavaProjection.path(app)), projected);
        assertSame("Repeated unchanged host requests must not invoke the compiler again", projected, service.forHost(host).orElseThrow());
    }
    public void testUnrelatedEditsReuseOtherModuleAndJavaChangesReuseAllJx() {
        module("first"); module("second");
        PsiFile first = component("first", "App", "<span>One</span>");
        PsiFile second = component("second", "Other", "<span>Two</span>");
        PsiFile ordinary = myFixture.addFileToProject("second/src/main/java/sample/Plain.java", "class Plain { int n = 1; }");
        var service = JxJavaProjection.getInstance(getProject());
        var before = service.snapshot();
        replace(first, "One", "Changed");
        var after = service.snapshot();
        assertNotSame(before.generated().get(JxJavaProjection.path(first)), after.generated().get(JxJavaProjection.path(first)));
        assertTrue(after.generated().get(JxJavaProjection.path(first)).javaSource().contains("Changed"));
        assertSame("An edit in another Maven root must not recompile this module",
                before.generated().get(JxJavaProjection.path(second)), after.generated().get(JxJavaProjection.path(second)));
        replace(ordinary, "1", "2");
        var javaEdit = service.snapshot();
        assertSame("Ordinary Java body edits must not rescan the JX source index", after, javaEdit);
        assertSame(after.generated().get(JxJavaProjection.path(first)), javaEdit.generated().get(JxJavaProjection.path(first)));
        assertSame(after.generated().get(JxJavaProjection.path(second)), javaEdit.generated().get(JxJavaProjection.path(second)));
    }
    public void testSiblingPropChangesInvalidateConsumerAndCompletionCopiesStaySeparate() {
        module("app");
        PsiFile counter = myFixture.addFileToProject("app/src/main/jx/sample/Counter.jx", """
                package sample;
                @jx.Component public class Counter {
                    public record Props(String title) {}
                    public static jx.View render(Props props) { return <span>{props.title()}</span>; }
                }
                """);
        PsiFile app = component("app", "App", "<Counter title={\"Hello\"}/>");
        var service = JxJavaProjection.getInstance(getProject());
        var before = service.forHost(JxJavaProjection.host(app)).orElseThrow();
        replace(counter, "String title", "int title");
        var after = service.forHost(JxJavaProjection.host(app)).orElseThrow();
        assertNotSame("Sibling prop metadata must invalidate the consumer", before, after);
        PsiFile copy = (PsiFile) app.copy();
        var copiedHost = JxJavaProjection.host(copy);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> { copiedHost.updateText(copiedHost.getText().replace("Hello", "CompletionCopy")); });
        var temporary = service.forHost(copiedHost).orElseThrow();
        assertTrue(temporary.javaSource().contains("CompletionCopy"));
        assertSame(temporary, service.forHost(copiedHost).orElseThrow());
        var canonical = service.forHost(JxJavaProjection.host(app)).orElseThrow();
        assertFalse(canonical.javaSource().contains("CompletionCopy"));
        assertTrue(canonical.javaSource().contains("Hello"));
    }
    public void testFileAddRenameDeleteAndClosedFileChangeInvalidateSnapshots() {
        module("app");
        PsiFile first = component("app", "First", "<span>Before</span>");
        var service = JxJavaProjection.getInstance(getProject());
        var initial = service.snapshot();
        PsiFile added = component("app", "Added", "<span>Added</span>");
        var addedPath = JxJavaProjection.path(added);
        assertFalse(initial.generated().containsKey(addedPath));
        assertTrue(service.snapshot().generated().containsKey(addedPath));
        WriteCommandAction.runWriteCommandAction(getProject(), () -> { added.setName("Renamed.jx"); });
        assertFalse(service.snapshot().generated().containsKey(addedPath));
        assertTrue(service.snapshot().generated().containsKey(JxJavaProjection.path(added)));
        var renamedPath = JxJavaProjection.path(added);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> { added.delete(); });
        assertFalse(service.snapshot().generated().containsKey(renamedPath));
        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveAllDocuments();
        String updated = first.getText().replace("Before", "After");
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            try { first.getVirtualFile().setBinaryContent(updated.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            // The fixture's in-memory VFS has no disk watcher. Deliver the same PSI reload
            // the IDE performs after a closed file changes on disk, before reading projections.
            com.intellij.psi.PsiManager.getInstance(getProject()).reloadFromDisk(first);
        });
        com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertTrue("VFS write must reach the PSI source: " + first.getText(), first.getText().contains("After"));
        var changed = service.snapshot().generated().get(JxJavaProjection.path(first));
        assertTrue(changed.javaSource(), changed.javaSource().contains("After"));
    }
}
