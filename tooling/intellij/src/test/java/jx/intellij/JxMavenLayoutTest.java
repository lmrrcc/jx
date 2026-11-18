package jx.intellij;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/** Maven registers generated Java, while authored src/main/jx initially remains plain content. */
public final class JxMavenLayoutTest extends LightJavaCodeInsightFixtureTestCase {
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }
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
