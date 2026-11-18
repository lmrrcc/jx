package jx.intellij;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Makes source-declared JX classes visible to Java's normal resolution/completion pipeline. */
public final class JxJavaElementFinder extends PsiElementFinder {
    private final Project project;
    public JxJavaElementFinder(Project project) { this.project = project; }
    @Override public PsiClass findClass(@NotNull String name, @NotNull GlobalSearchScope scope) {
        PsiClass[] classes = findClasses(name, scope); return classes.length == 0 ? null : classes[0];
    }
    @Override public PsiClass @NotNull [] findClasses(@NotNull String name, @NotNull GlobalSearchScope scope) {
        if (DumbService.isDumb(project)) return PsiClass.EMPTY_ARRAY;
        var result = RecursionManager.doPreventingRecursion(new Query(this, name, scope), true, () -> {
            var found = new ArrayList<PsiClass>();
            var state = JxJavaProjection.getInstance(project).snapshot();
            for (var entry : state.generated().entrySet()) {
                ProgressManager.checkCanceled();
                if (entry.getValue().topLevelTypes().stream().noneMatch(type -> name.equals(type) || name.startsWith(type + "."))) continue;
                var virtual = state.files().get(entry.getKey());
                if (virtual == null || !JxJavaProjection.inScope(project, scope, virtual)) continue;
                var file = PsiManager.getInstance(project).findFile(virtual);
                var java = file == null ? null : JxJavaProjection.javaFile(file);
                if (java != null) for (PsiClass declaration : java.getClasses()) collect(declaration, name, found);
            }
            return found.toArray(PsiClass.EMPTY_ARRAY);
        });
        return result == null ? PsiClass.EMPTY_ARRAY : result;
    }
    private record Query(JxJavaElementFinder finder, String name, GlobalSearchScope scope) {}
    private static void collect(PsiClass declaration, String name, ArrayList<PsiClass> found) {
        if (name.equals(declaration.getQualifiedName())) found.add(declaration);
        for (PsiClass nested : declaration.getInnerClasses()) collect(nested, name, found);
    }
    @Override public @NotNull Set<String> getClassNames(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
        var names = new HashSet<String>();
        if (DumbService.isDumb(project)) return names;
        var state = JxJavaProjection.getInstance(project).snapshot();
        for (var entry : state.generated().entrySet()) {
            var virtual = state.files().get(entry.getKey());
            if (virtual == null || !JxJavaProjection.inScope(project, scope, virtual)) continue;
            for (String name : entry.getValue().topLevelTypes()) {
                int dot = name.lastIndexOf('.');
                if (psiPackage.getQualifiedName().equals(dot < 0 ? "" : name.substring(0, dot))) names.add(name.substring(dot + 1));
            }
        }
        return names;
    }
    @Override public PsiClass @NotNull [] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
        var classes = new ArrayList<PsiClass>();
        String prefix = psiPackage.getQualifiedName().isEmpty() ? "" : psiPackage.getQualifiedName() + ".";
        for (String name : getClassNames(psiPackage, scope)) classes.addAll(java.util.List.of(findClasses(prefix + name, scope)));
        return classes.toArray(PsiClass.EMPTY_ARRAY);
    }
}
