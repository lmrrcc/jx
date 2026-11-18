package jx.intellij;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import jx.compiler.JxCompiler;
import jx.compiler.SourceFile;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;

/** A normal Maven build leaves generated Java on an indexed source root. Native editing must keep
 * treating the .jx declaration as the source of truth and must never refactor the build output. */
public final class JxGeneratedSourcesTest extends LightJavaCodeInsightFixtureTestCase {
    private static final String COUNTER = """
            package sample;
            import jx.*;
            @Component public final class Counter {
                public static String greet(String name) { return "Hello " + name; }
                public static View render() { return <button>{greet("self")}</button>; }
            }
            """;
    private PsiFile authored, generated, javaConsumer, jxConsumer;
    private String generatedText;

    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }

    @Override protected void setUp() throws Exception {
        super.setUp();
        myFixture.setCaresAboutInjection(false);
        Path core = Path.of(System.getProperty("jx.repositoryRoot")).resolve("jx-core/target/classes");
        assertTrue("Build the actual JX core before IDE acceptance tests", Files.isRegularFile(core.resolve("jx/ComponentType.class")));
        PsiTestUtil.addLibrary(getTestRootDisposable(), getModule(), "jx-core", core.getParent().toString(), core.getFileName().toString());
        authored = myFixture.addFileToProject("sample/Counter.jx", COUNTER);
        var compilation = new JxCompiler().compile(List.of(new SourceFile(Path.of(authored.getVirtualFile().getPath()), COUNTER)));
        assertTrue(compilation.diagnostics().toString(), compilation.successful());
        generatedText = compilation.generatedSources().getFirst().javaSource();
        myFixture.addFileToProject("target/jx/main/outputs.tsv", "jx-outputs-v1\nsample/Counter.java\tsample.Counter\n");
        myFixture.addFileToProject("target/jx/main/sample/Counter.jxmap", authored.getVirtualFile().getUrl() + "\n"
                + compilation.generatedSources().getFirst().sourceMap().serialize());
        generated = myFixture.addFileToProject("target/generated-sources/jx/sample/Counter.java", generatedText);
        var generatedRoot = generated.getVirtualFile().getParent().getParent();
        PsiTestUtil.addSourceRoot(getModule(), generatedRoot, JavaSourceRootType.SOURCE, new JavaSourceRootProperties("", true));
        javaConsumer = myFixture.addFileToProject("sample/JavaConsumer.java", """
                package sample;
                public final class JavaConsumer {
                    public String title() { return Counter.greet("java"); }
                }
                """);
        jxConsumer = myFixture.addFileToProject("sample/App.jx", """
                package sample;
                import jx.*;
                @Component public final class App {
                    public static View render() { return <main>{Counter.greet("jx")}<Counter/></main>; }
                }
                """);
        myFixture.configureFromExistingVirtualFile(authored.getVirtualFile());
    }

    public void testNativeClassFinderAndShortNamesPreferAuthoredDeclaration() {
        assertEquals("Physical Java remains available to the Maven/JPS compiler", com.intellij.ide.highlighter.JavaFileType.INSTANCE,
                generated.getVirtualFile().getFileType());
        assertFalse("Generated counterparts do not create duplicate Java PSI", generated instanceof com.intellij.psi.PsiJavaFile);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(getProject());
        PsiClass resolved = JavaPsiFacade.getInstance(getProject()).findClass("sample.Counter", scope);
        assertNotNull("Java must resolve the source component after Maven generation", resolved);
        assertAuthored(resolved);
        PsiClass[] all = JavaPsiFacade.getInstance(getProject()).findClasses("sample.Counter", scope);
        assertEquals("Generated output must not produce a second class candidate", 1, all.length);
        assertAuthored(all[0]);
        PsiClass[] shortNames = PsiShortNamesCache.getInstance(getProject()).getClassesByName("Counter", scope);
        assertEquals("Go to Class must present one authored component", 1, shortNames.length);
        assertAuthored(shortNames[0]);
    }

    public void testUnownedGeneratedAndOrdinaryJavaKeepTheirJavaLanguage() {
        var substitutor = new JxGeneratedJavaLanguageSubstitutor();
        assertNull(substitutor.getLanguage(javaConsumer.getVirtualFile(), getProject()));
        PsiFile unowned = myFixture.addFileToProject("target/generated-sources/jx/sample/Handwritten.java",
                "package sample; public class Handwritten {}");
        assertNull(substitutor.getLanguage(unowned.getVirtualFile(), getProject()));
        assertTrue(unowned instanceof com.intellij.psi.PsiJavaFile);
        assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("sample.Handwritten", GlobalSearchScope.projectScope(getProject())));
    }

    public void testNativeUsagesAndRenameDoNotIncludeOrChangeGeneratedJava() {
        var java = JxJavaProjection.javaFile(authored);
        assertNotNull("Authored component must have native Java PSI", java);
        PsiMethod[] declarations = java.getClasses()[0].findMethodsByName("greet", false);
        assertEquals(1, declarations.length);
        PsiMethod method = declarations[0];
        var usages = myFixture.findUsages(method);
        assertEquals("Expected the self-call, ordinary Java call, and JX expression call", 3, usages.size());
        for (var usage : usages) {
            assertNotNull(usage.getElement());
            PsiFile host = InjectedLanguageManager.getInstance(getProject()).getTopLevelFile(usage.getElement());
            assertFalse("Generated Java must not be returned as an editable usage", host.getVirtualFile().equals(generated.getVirtualFile()));
        }
        myFixture.renameElement(method, "welcome");
        assertTrue(authored.getText().contains("String welcome(String name)"));
        assertTrue(authored.getText().contains("welcome(\"self\")"));
        assertTrue(javaConsumer.getText().contains("Counter.welcome(\"java\")"));
        assertTrue(jxConsumer.getText().contains("Counter.welcome(\"jx\")"));
        assertEquals("A source rename must leave build output byte-for-byte unchanged", generatedText, generated.getText());
    }

    private void assertAuthored(PsiClass type) {
        PsiFile topLevel = InjectedLanguageManager.getInstance(getProject()).getTopLevelFile(type);
        assertEquals("Navigation must point to the authored JX file", authored.getVirtualFile(), topLevel.getVirtualFile());
    }
}
