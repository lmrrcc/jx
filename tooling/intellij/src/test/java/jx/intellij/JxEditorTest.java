package jx.intellij;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.platform.lsp.api.LspServerManager;
import com.intellij.platform.lsp.api.LspServerState;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;

public final class JxEditorTest extends BasePlatformTestCase {
    @Override protected com.intellij.testFramework.fixtures.TempDirTestFixture createTempDirTestFixture() {
        return new com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl();
    }
    @Override protected void tearDown() throws Exception {
        try {
            var manager = LspServerManager.getInstance(getProject());
            manager.stopServers(JxLspSupportProvider.class);
            PlatformTestUtil.waitWithEventsDispatching("JX server did not stop", () -> manager.getServersForProvider(JxLspSupportProvider.class).stream().noneMatch(server -> server.getState() == LspServerState.Initializing || server.getState() == LspServerState.Running), 10);
        } finally { super.tearDown(); }
    }
    public void testJxFileRegistrationAndHighlighting() {
        var file = myFixture.configureByText("Counter.jx", "public final class Counter { String label = \"<not a tag>\"; /* <nor this> */ return <button>Count</button>; }");
        assertSame(JxFileType.INSTANCE, file.getFileType());
        assertSame(JxLanguage.INSTANCE, file.getLanguage());
        var lexer = new JxLexer();
        lexer.start(file.getText());
        int strings = 0, comments = 0, tags = 0, length = 0;
        while (lexer.getTokenType() != null) {
            if (lexer.getTokenType() == JxLexer.STRING) strings++;
            if (lexer.getTokenType() == JxLexer.COMMENT) comments++;
            if (lexer.getTokenType() == JxLexer.TAG) tags++;
            length += lexer.getTokenEnd() - lexer.getTokenStart(); lexer.advance();
        }
        assertEquals(1, strings); assertEquals(1, comments); assertEquals(2, tags);
        assertEquals(file.getTextLength(), length);
    }
    public void testDescriptorSupportsOnlyJx() {
        var descriptor = new JxLspServerDescriptor(getProject());
        assertTrue(descriptor.isSupportedFile(myFixture.configureByText("App.jx", "").getVirtualFile()));
        assertFalse(descriptor.isSupportedFile(myFixture.configureByText("App.java", "").getVirtualFile()));
    }
    public void testBundledServerStartsThroughIntellijAndCompletes() {
        TrustedProjects.setTrusted(getProject(), true);
        var file = myFixture.addFileToProject("src/main/jx/EditorProof.jx", "import jx.JxComponent;\nimport jx.View;\n@JxComponent\npublic final class EditorProof { public static View render() { return (<div>Hello</div>); } }");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        var manager = LspServerManager.getInstance(getProject());
        manager.ensureServerStarted(JxLspSupportProvider.class, new JxLspServerDescriptor(getProject()));
        try {
            PlatformTestUtil.waitWithEventsDispatching("JX language server did not initialize", () -> manager.getServersForProvider(JxLspSupportProvider.class).stream().anyMatch(server -> server.getState() == LspServerState.Running), 30);
            var server = manager.getServersForProvider(JxLspSupportProvider.class).stream().filter(candidate -> candidate.getState() == LspServerState.Running).findFirst().orElseThrow();
            assertNotNull(server.getInitializeResult().getCapabilities().getCompletionProvider());
            var params = new CompletionParams(new TextDocumentIdentifier(file.getVirtualFile().getUrl()), new Position(3, 72));
            var response = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() -> server.sendRequestSync(10_000, remote -> remote.getTextDocumentService().completion(params))), 15_000);
            assertNotNull(response);
            assertFalse(response.isLeft() ? response.getLeft().isEmpty() : response.getRight().getItems().isEmpty());
        } finally { manager.stopServers(JxLspSupportProvider.class); }
    }
}
