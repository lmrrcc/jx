package jx.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ResolveScopeEnlarger;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

/** Makes conventional JX sources visible to native Java without changing Maven/JPS source roots. */
public final class JxResolveScopeEnlarger extends ResolveScopeEnlarger {
    @Override public SearchScope getAdditionalResolveScope(@NotNull VirtualFile file, @NotNull Project project) {
        var index = ProjectFileIndex.getInstance(project);
        var module = index.getModuleForFile(file);
        if (module == null || index.isExcluded(file)) return null;
        boolean tests = index.isInTestSourceContent(file) || file.getPath().contains("/src/test/jx/");
        var dependencies = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, tests);
        return new GlobalSearchScope(project) {
            @Override public boolean contains(@NotNull VirtualFile candidate) {
                return candidate.getFileType() == JxFileType.INSTANCE && JxJavaProjection.inScope(project, dependencies, candidate);
            }
            @Override public boolean isSearchInModuleContent(@NotNull com.intellij.openapi.module.Module candidate) {
                return dependencies.isSearchInModuleContent(candidate);
            }
            @Override public boolean isSearchInModuleContent(@NotNull com.intellij.openapi.module.Module candidate, boolean testSources) {
                return dependencies.isSearchInModuleContent(candidate, testSources);
            }
            @Override public boolean isSearchInLibraries() { return false; }
        };
    }
}
