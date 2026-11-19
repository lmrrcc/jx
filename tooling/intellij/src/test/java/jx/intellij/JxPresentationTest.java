package jx.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.awt.Color;
import java.nio.file.Path;

/** Exercises the daemon's actual injected-background pass, not only lexer token keys. */
public final class JxPresentationTest extends LightJavaCodeInsightFixtureTestCase {
    private boolean wasTrusted;
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }
    @Override protected void setUp() throws Exception {
        super.setUp();
        myFixture.setCaresAboutInjection(false);
        wasTrusted = TrustedProjects.isTrusted(getProject());
        TrustedProjects.setTrusted(getProject(), false);
        var servers = com.intellij.platform.lsp.api.LspServerManager.getInstance(getProject());
        servers.stopServers(JxLspSupportProvider.class);
        PlatformTestUtil.waitWithEventsDispatching("Native presentation fixture must not run the LSP", () ->
                servers.getServersForProvider(JxLspSupportProvider.class).stream().noneMatch(server ->
                        server.getState() == com.intellij.platform.lsp.api.LspServerState.Initializing
                                || server.getState() == com.intellij.platform.lsp.api.LspServerState.Running), 10);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        Path core = Path.of(System.getProperty("jx.repositoryRoot")).resolve("jx-core/target/classes");
        PsiTestUtil.addLibrary(getTestRootDisposable(), getModule(), "jx-core", core.getParent().toString(), core.getFileName().toString());
    }
    @Override protected void tearDown() throws Exception {
        try { TrustedProjects.setTrusted(getProject(), wasTrusted); }
        finally { super.tearDown(); }
    }

    public void testJavaFragmentsKeepSemanticColorsAndErrorsWithoutTintingSourceBackground() throws Exception {
        verifyNativePresentation(EditorColorsManager.getInstance().getGlobalScheme(), "");
    }

    public void testDarculaKeepsNativeJavaAndMarkupColorsWithoutFragmentBackground() throws Exception {
        var darcula = EditorColorsManager.getInstance().getScheme("Darcula");
        assertNotNull("The supported IDEA distribution must provide its Darcula editor scheme", darcula);
        verifyNativePresentation(darcula, "dark-");
    }

    private void verifyNativePresentation(EditorColorsScheme originalScheme, String artifactPrefix) throws Exception {
        var file = myFixture.addFileToProject("App.jx", """
                class App {
                    Object render(String label) {
                        int count = "wrong type";
                        return <button class="counter" title={label}>{count}</button>;
                    }
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        var background = EditorColors.createInjectedLanguageFragmentKey(JxLanguage.INSTANCE);
        var global = EditorColorsManager.getInstance().getGlobalScheme();
        var previousGlobal = global.getAttributes(background) == null ? null : global.getAttributes(background).clone();
        var previousOriginal = originalScheme.getAttributes(background) == null ? null : originalScheme.getAttributes(background).clone();
        var scheme = (EditorColorsScheme) originalScheme.clone();
        scheme.setEditorFontSize(18);
        // The selected theme has a conspicuous injection color. Suppression must be local
        // to JX's host, not achieved by clearing the user's global color preference.
        scheme.setAttributes(background, new TextAttributes(null, Color.MAGENTA, null, null, 0));
        ((EditorEx) myFixture.getEditor()).setColorsScheme(scheme);
        var highlights = myFixture.doHighlighting(HighlightInfoType.INJECTED_FRAGMENT_SYNTAX_SEVERITY);
        var fragments = highlights.stream().filter(info -> info.type == HighlightInfoType.INJECTED_LANGUAGE_BACKGROUND).toList();
        assertFalse("The real injected daemon pass must have run", fragments.isEmpty());
        var painted = captureEditor(artifactPrefix + (fragments.stream().anyMatch(info -> info.forcedTextAttributesKey != null) ? "before" : "after"));
        for (var fragment : fragments) {
            assertNull("JX must not attach the theme's injected-fragment background", fragment.forcedTextAttributesKey);
            var attributes = fragment.getTextAttributes(file, scheme);
            assertTrue("Injected Java must not tint the authored document", attributes == null || attributes.getBackgroundColor() == null);
        }
        assertTrue("Native Java diagnostics must remain active", highlights.stream().anyMatch(info ->
                info.getSeverity() == HighlightSeverity.ERROR && info.getStartOffset() <= file.getText().indexOf("\"wrong type\"")
                        && info.getEndOffset() > file.getText().indexOf("\"wrong type\"")));
        assertTrue("Injected Java syntax coloring must remain active: " + highlights, highlights.stream().anyMatch(info ->
                info.type == HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT));
        assertEquals("The user's global scheme is untouched", previousGlobal, global.getAttributes(background));
        assertEquals("The selected theme is untouched", previousOriginal, originalScheme.getAttributes(background));
        assertEquals("Other injected languages retain the user's configured color", Color.MAGENTA,
                scheme.getAttributes(background).getBackgroundColor());
        assertEquals("Painted Java line background must match the active editor theme", scheme.getDefaultBackground().getRGB(),
                painted.getRGB(1000, myFixture.getEditor().getLineHeight() * 2 + 1));
    }

    private java.awt.image.BufferedImage captureEditor(String stage) throws Exception {
        var component = myFixture.getEditor().getContentComponent();
        component.setSize(1100, 420);
        component.doLayout();
        var image = new java.awt.image.BufferedImage(1100, 420, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { component.paint(graphics); }
        finally { graphics.dispose(); }
        Path directory = Path.of(System.getProperty("jx.repositoryRoot")).resolve("tooling/intellij/build/reports/presentation");
        java.nio.file.Files.createDirectories(directory);
        javax.imageio.ImageIO.write(image, "png", directory.resolve("jx-editor-" + stage + ".png").toFile());
        return image;
    }
}
