package jx.intellij;

import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class JxRunLineMarkerTest extends BasePlatformTestCase {
    public void testRegisteredMarkerTargetsAuthoredClassOnly() {
        var file = myFixture.addFileToProject("App.jx", """
                @Component public final class App {
                    Object render() { return <App title="class App"/>; }
                }
                """);
        var provider = LineMarkerProviders.getInstance().allForLanguage(JxLanguage.INSTANCE).stream()
                .filter(JxRunLineMarkerProvider.class::isInstance).findFirst().orElseThrow();
        int count = 0;
        for (var leaf = PsiTreeUtil.getDeepestFirst(file); leaf != null; leaf = PsiTreeUtil.nextLeaf(leaf)) {
            var marker = provider.getLineMarkerInfo(leaf);
            if (marker != null) {
                count++;
                assertEquals(file.getText().indexOf("App {"), marker.startOffset);
                assertNotNull(marker.getNavigationHandler());
                assertEquals(JxRunLineMarkerProvider.TOOLTIP, marker.getLineMarkerTooltip());
            }
        }
        assertEquals(1, count);
    }

    public void testMarkupAndStringsDoNotCreateRunMarkers() {
        var file = myFixture.addFileToProject("App.jx", "class Other { String s = \"class App\"; Object view() { return <App/>; } }");
        var provider = new JxRunLineMarkerProvider();
        for (var leaf = PsiTreeUtil.getDeepestFirst(file); leaf != null; leaf = PsiTreeUtil.nextLeaf(leaf))
            assertNull(provider.getLineMarkerInfo(leaf));
    }
}
