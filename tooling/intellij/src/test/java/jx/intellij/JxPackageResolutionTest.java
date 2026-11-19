package jx.intellij;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.util.Arrays;

/** JX declarations must expose packages even outside registered Java source directories. */
public final class JxPackageResolutionTest extends LightJavaCodeInsightFixtureTestCase {
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }

    public void testJavaImportResolvesPackageContainingOnlyJx() {
        myFixture.addFileToProject("src/main/jx/widgets/Counter.jx", "package widgets; public class Counter {}");
        myFixture.configureByText("Use.java", "package client; import widgets.Counter; class Use { Counter field; }");
        var errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
        assertTrue(errors.toString(), errors.isEmpty());
        var pkg = JavaPsiFacade.getInstance(getProject()).findPackage("widgets");
        assertNotNull(pkg);
        assertTrue(Arrays.stream(pkg.getClasses(GlobalSearchScope.projectScope(getProject())))
                .anyMatch(type -> "widgets.Counter".equals(type.getQualifiedName())));
    }

    public void testAncestorPackagesAndAuthoredDirectoriesAreVisible() {
        var source = myFixture.addFileToProject("src/main/jx/widgets/controls/Button.jx",
                "package widgets.controls; public class Button {}");
        var facade = JavaPsiFacade.getInstance(getProject());
        var parent = facade.findPackage("widgets");
        assertNotNull(parent);
        var children = parent.getSubPackages(GlobalSearchScope.projectScope(getProject()));
        assertTrue(Arrays.toString(children), Arrays.stream(children).anyMatch(pkg -> pkg.getQualifiedName().equals("widgets.controls")));
        var pkg = facade.findPackage("widgets.controls");
        assertNotNull(pkg);
        assertTrue(Arrays.stream(pkg.getDirectories(GlobalSearchScope.projectScope(getProject())))
                .anyMatch(directory -> directory.getVirtualFile().equals(source.getVirtualFile().getParent())));
    }

    public void testPackageLookupRefreshesAfterUnsavedDeclarationChange() {
        var source = myFixture.addFileToProject("src/main/jx/widgets/Counter.jx", "package widgets; public class Counter {}");
        var facade = JavaPsiFacade.getInstance(getProject());
        assertNotNull(facade.findPackage("widgets"));
        assertNull(facade.findPackage("freshwidgets"));
        var manager = PsiDocumentManager.getInstance(getProject());
        var document = manager.getDocument(source);
        assertNotNull(document);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("package freshwidgets; public class Counter {}"));
        manager.commitAllDocuments();
        assertNotNull("A formerly absent package must appear after editing its JX declaration", facade.findPackage("freshwidgets"));
    }
}
