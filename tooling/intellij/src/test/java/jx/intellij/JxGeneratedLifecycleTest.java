package jx.intellij;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ui.UIUtil;
import java.nio.file.Path;
import java.net.URI;
import java.util.List;
import jx.compiler.JxCompiler;
import jx.compiler.SourceFile;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;

/** Exercises real VFS ownership transitions after Java PSI/indexes have already been populated. */
public final class JxGeneratedLifecycleTest extends LightJavaCodeInsightFixtureTestCase {
    private static final String SOURCE = "package sample; public class Counter { public static int value() { return 1; } }";
    private static final String MANIFEST = "jx-outputs-v1\nsample/Counter.java\tsample.Counter\n";
    private VirtualFile authored, generated, manifest, mapping, unowned;
    private PsiFile unownedPsi;
    private String sourceMap;
    private VirtualFile generatedRoot;

    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }

    @Override protected void setUp() throws Exception {
        super.setUp();
        authored = myFixture.addFileToProject("sample/Counter.jx", SOURCE).getVirtualFile();
        var compiled = new JxCompiler().compile(List.of(new SourceFile(Path.of(authored.getPath()), SOURCE)));
        assertTrue(compiled.diagnostics().toString(), compiled.successful());
        sourceMap = compiled.generatedSources().getFirst().sourceMap().serialize();
        generated = myFixture.addFileToProject("target/generated-sources/jx/sample/Counter.java",
                compiled.generatedSources().getFirst().javaSource()).getVirtualFile();
        generatedRoot = generated.getParent().getParent();
        PsiTestUtil.addSourceRoot(getModule(), generatedRoot, JavaSourceRootType.SOURCE,
                new JavaSourceRootProperties("", true));
        unownedPsi = myFixture.addFileToProject("target/generated-sources/jx/sample/Unowned.java",
                "package sample; public class Unowned {}");
        unowned = unownedPsi.getVirtualFile();
        assertJava(true);
    }

    @Override protected void tearDown() throws Exception {
        try {
            if (generatedRoot != null) PsiTestUtil.removeSourceRoot(getModule(), generatedRoot);
        } finally {
            generatedRoot = null;
            super.tearDown();
        }
    }

    public void testLateAndRemovedOwnershipMetadataReclassifiesExistingJavaPsi() throws Exception {
        mapping = myFixture.addFileToProject("target/jx/main/sample/Counter.jxmap", map()).getVirtualFile();
        settle(); assertJava(true);
        manifest = myFixture.addFileToProject("target/jx/main/outputs.tsv", MANIFEST).getVirtualFile();
        settle(); assertJava(false);
        WriteAction.run(() -> mapping.delete(this));
        settle(); assertJava(true);
        mapping = myFixture.addFileToProject("target/jx/main/sample/Counter.jxmap", map()).getVirtualFile();
        settle(); assertJava(false);
        WriteAction.run(() -> manifest.delete(this));
        settle(); assertJava(true);
        manifest = myFixture.addFileToProject("target/jx/main/outputs.tsv", MANIFEST).getVirtualFile();
        settle(); assertJava(false);
        WriteAction.run(() -> VfsUtil.saveText(manifest, "jx-outputs-v1\n"));
        settle(); assertJava(true);
        WriteAction.run(() -> VfsUtil.saveText(manifest, MANIFEST));
        settle(); assertJava(false);
    }

    public void testAuthoredDeletionAndRestorationReclassifiesOnlyItsCounterpart() throws Exception {
        establishOwnership();
        WriteAction.run(() -> authored.delete(this));
        settle(); assertJava(true);
        authored = myFixture.addFileToProject("sample/Counter.jx", SOURCE).getVirtualFile();
        settle(); assertJava(false);
    }

    public void testOwnershipMetadataCanPrecedeTheFirstAuthoredFile() throws Exception {
        String metadata = map();
        WriteAction.run(() -> authored.delete(this));
        manifest = myFixture.addFileToProject("target/jx/main/outputs.tsv", MANIFEST).getVirtualFile();
        mapping = myFixture.addFileToProject("target/jx/main/sample/Counter.jxmap", metadata).getVirtualFile();
        settle(); assertJava(true);
        authored = myFixture.addFileToProject("sample/Counter.jx", SOURCE).getVirtualFile();
        settle(); assertJava(false);
    }

    public void testAuthoredRenameAndMoveFollowUpdatedSourceMaps() throws Exception {
        establishOwnership();
        WriteAction.run(() -> authored.rename(this, "Renamed.jx"));
        settle(); assertJava(true);
        WriteAction.run(() -> VfsUtil.saveText(mapping, map()));
        settle(); assertJava(false);
        VirtualFile destination = myFixture.addFileToProject("moved/Keep.txt", "fixture").getVirtualFile().getParent();
        WriteAction.run(() -> authored.move(this, destination));
        settle(); assertJava(true);
        WriteAction.run(() -> VfsUtil.saveText(mapping, map()));
        settle(); assertJava(false);
        WriteAction.run(() -> authored.delete(this));
        settle(); assertJava(true);
    }

    public void testPercentEncodedOwnershipUrisResolveSpacesAndRejectMalformedUris() throws Exception {
        VirtualFile destination = myFixture.addFileToProject("space dir/Keep.txt", "fixture").getVirtualFile().getParent();
        WriteAction.run(() -> authored.move(this, destination));
        String uri = new URI(authored.getFileSystem().getProtocol(), null, authored.getPath(), null).toASCIIString();
        assertTrue("The fixture must exercise Maven's percent-encoded URI spelling", uri.contains("space%20dir"));
        manifest = myFixture.addFileToProject("target/jx/main/outputs.tsv", MANIFEST).getVirtualFile();
        mapping = myFixture.addFileToProject("target/jx/main/sample/Counter.jxmap", uri + "\n" + sourceMap).getVirtualFile();
        settle(); assertJava(false);
        WriteAction.run(() -> VfsUtil.saveText(mapping, "file://%invalid\n" + sourceMap));
        settle(); assertJava(true);
        WriteAction.run(() -> VfsUtil.saveText(mapping, uri + "\n" + sourceMap));
        settle(); assertJava(false);
        WriteAction.run(() -> authored.delete(this));
        settle(); assertJava(true);
    }

    private void establishOwnership() {
        manifest = myFixture.addFileToProject("target/jx/main/outputs.tsv", MANIFEST).getVirtualFile();
        mapping = myFixture.addFileToProject("target/jx/main/sample/Counter.jxmap", map()).getVirtualFile();
        settle(); assertJava(false);
    }

    private String map() { return authored.getUrl() + "\n" + sourceMap; }

    private void settle() {
        UIUtil.dispatchAllInvocationEvents();
        IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        UIUtil.dispatchAllInvocationEvents();
    }

    private void assertJava(boolean expected) {
        PsiFile current = PsiManager.getInstance(getProject()).findFile(generated);
        assertNotNull(current);
        assertEquals("Existing generated PSI must follow its current proven ownership; metadata="
                + JxGeneratedJavaLanguageSubstitutor.declaredSource(generated) + "; source="
                + JxGeneratedJavaLanguageSubstitutor.authoredSource(generated), expected, current instanceof PsiJavaFile);
        assertEquals("Physical build input type must stay Java", JavaFileType.INSTANCE, generated.getFileType());
        assertSame("Unowned Java must not be reparsed by another file's ownership change", unownedPsi,
                PsiManager.getInstance(getProject()).findFile(unowned));
        assertTrue(unownedPsi.isValid());
        assertTrue(unownedPsi instanceof PsiJavaFile);
        assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("sample.Unowned", GlobalSearchScope.projectScope(getProject())));
    }
}
