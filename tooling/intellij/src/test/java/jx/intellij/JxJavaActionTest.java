package jx.intellij;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.nio.file.Path;

/** A visible Java inspection is useful only if its normal quick-fix edits authored source. */
public final class JxJavaActionTest extends LightJavaCodeInsightFixtureTestCase {
    private boolean wasTrusted;
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }
    @Override protected void setUp() throws Exception {
        super.setUp(); myFixture.setCaresAboutInjection(false);
        // This fixture verifies Java's quick fix independently of the LSP. A real LSP
        // startup restarts the daemon asynchronously and belongs to JxEditorTest instead.
        wasTrusted = com.intellij.ide.impl.TrustedProjects.isTrusted(getProject());
        com.intellij.ide.impl.TrustedProjects.setTrusted(getProject(), false);
        var servers = com.intellij.platform.lsp.api.LspServerManager.getInstance(getProject());
        servers.stopServers(JxLspSupportProvider.class);
        com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching("Native Java fixture must not run the LSP",
                () -> servers.getServersForProvider(JxLspSupportProvider.class).stream().noneMatch(server ->
                        server.getState() == com.intellij.platform.lsp.api.LspServerState.Initializing
                                || server.getState() == com.intellij.platform.lsp.api.LspServerState.Running), 10);
        com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        Path core = Path.of(System.getProperty("jx.repositoryRoot")).resolve("jx-core/target/classes");
        PsiTestUtil.addLibrary(getTestRootDisposable(), getModule(), "jx-core", core.getParent().toString(), core.getFileName().toString());
    }
    @Override protected void tearDown() throws Exception {
        try { com.intellij.ide.impl.TrustedProjects.setTrusted(getProject(), wasTrusted); }
        finally { super.tearDown(); }
    }
    public void testJavaInspectionQuickFixInsideMarkup() {
        myFixture.enableInspections(new com.siyeh.ig.bugs.StringEqualityInspection());
        var file = myFixture.addFileToProject("App.jx", """
                class App {
                    Object render(String left, String right) {
                        return <span title="unchanged">{left == right}</span>;
                    }
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(file.getText().indexOf("=="));
        assertFalse(myFixture.doHighlighting(HighlightSeverity.WARNING).isEmpty());
        var actions = myFixture.getAvailableIntentions();
        var fix = actions.stream().filter(action -> action.getText().contains("equals()"))
                .findFirst().orElseThrow(() -> new AssertionError("No equality quick fix: " + actions.stream().map(action -> action.getText()).toList()));
        myFixture.launchAction(fix);
        assertTrue(file.getText(), file.getText().contains("Objects.equals(left, right)"));
        assertTrue(file.getText(), file.getText().contains("import java.util.Objects;"));
        assertTrue(file.getText(), file.getText().contains("<span title=\"unchanged\">"));
        assertTrue(file.getText(), file.getText().contains("</span>"));
    }
}
