package jx.intellij;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;

/** Maven registers generated Java, while authored src/main/jx initially remains plain content. */
public final class JxMavenLayoutTest extends LightJavaCodeInsightFixtureTestCase {
    private record SourceRoot(String contentUrl, String url, JavaSourceRootType type,
                              JavaSourceRootProperties properties) {}
    private List<SourceRoot> originalRoots;
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }

    @Override protected void setUp() throws Exception {
        super.setUp();
        originalRoots = Arrays.stream(ModuleRootManager.getInstance(getModule()).getContentEntries())
                .flatMap(entry -> Arrays.stream(entry.getSourceFolders()).map(source -> {
                    assertTrue(source.getRootType() instanceof JavaSourceRootType);
                    var type = (JavaSourceRootType) source.getRootType();
                    return new SourceRoot(entry.getUrl(), source.getUrl(), type,
                            source.getJpsElement().getProperties(type).createCopy());
                })).toList();
    }

    @Override protected void tearDown() throws Exception {
        try {
            if (originalRoots != null) ModuleRootModificationUtil.updateModel(getModule(), model -> {
                for (var entry : model.getContentEntries()) {
                    entry.clearSourceFolders();
                    for (var source : originalRoots) {
                        if (entry.getUrl().equals(source.contentUrl())) {
                            entry.addSourceFolder(source.url(), source.type(), source.properties());
                        }
                    }
                }
            });
        } finally {
            originalRoots = null;
            super.tearDown();
        }
    }
    public void testJavaResolvesUnbuiltJxOutsideJavaSourceRoot() {
        myFixture.addFileToProject("pom.xml", "<project><modelVersion>4.0.0</modelVersion><groupId>sample</groupId><artifactId>app</artifactId><version>1</version></project>");
        var authored = myFixture.addFileToProject("src/main/jx/sample/Counter.jx", """
                package sample;
                public final class Counter {
                    public static String title() { return "Counter"; }
                    public static Object render() { return <span>{title()}</span>; }
                }
                """);
        var caller = myFixture.addFileToProject("src/main/java/sample/Caller.java", """
                package sample;
                public class Caller { public String title() { return Counter.title(); } }
                """);
        myFixture.addFileToProject("src/test/jx/sample/TestOnly.jx", "package sample; public class TestOnly {}");
        var javaRoot = caller.getVirtualFile().getParent().getParent();
        ModuleRootModificationUtil.updateModel(getModule(), model -> {
            for (var entry : model.getContentEntries()) {
                for (var source : entry.getSourceFolders()) entry.removeSourceFolder(source);
            }
            model.getContentEntries()[0].addSourceFolder(javaRoot, false);
        });
        var resolved = JavaPsiFacade.getInstance(getProject()).findClass("sample.Counter", caller.getResolveScope());
        assertNotNull("An unbuilt src/main/jx class must resolve from normal Maven Java sources", resolved);
        var host = InjectedLanguageManager.getInstance(getProject()).getInjectionHost(resolved);
        assertNotNull(host); assertEquals(authored, host.getContainingFile());
        var reference = caller.findReferenceAt(caller.getText().indexOf("Counter.title"));
        assertNotNull(reference); assertEquals(resolved, reference.resolve());
        assertNull("Production Java must not gain visibility of test-only JX classes",
                JavaPsiFacade.getInstance(getProject()).findClass("sample.TestOnly", caller.getResolveScope()));
        assertNull("Production JX must also retain production-only resolution",
                JavaPsiFacade.getInstance(getProject()).findClass("sample.TestOnly", JxJavaProjection.javaFile(authored).getResolveScope()));
    }
}
