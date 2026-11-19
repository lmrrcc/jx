package jx.intellij;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiPackageImpl;
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

    /** A declared JX package may have no .java stub and its source directory need not be a
     * Java source root. Expose the same package names as our classes, including ancestors. */
    @Override public PsiPackage findPackage(@NotNull String name) {
        if (DumbService.isDumb(project)) return null;
        return packageNames(GlobalSearchScope.projectScope(project)).contains(name)
                ? new PsiPackageImpl(PsiManager.getInstance(project), name) : null;
    }

    @Override public PsiPackage @NotNull [] getSubPackages(@NotNull PsiPackage parent, @NotNull GlobalSearchScope scope) {
        if (DumbService.isDumb(project)) return PsiPackage.EMPTY_ARRAY;
        String prefix = parent.getQualifiedName().isEmpty() ? "" : parent.getQualifiedName() + ".";
        return packageNames(scope).stream().filter(name -> name.startsWith(prefix) && name.length() > prefix.length()
                        && name.indexOf('.', prefix.length()) < 0)
                .map(name -> new PsiPackageImpl(PsiManager.getInstance(project), name)).toArray(PsiPackage[]::new);
    }

    private Set<String> packageNames(GlobalSearchScope scope) {
        var result = new HashSet<String>();
        var state = JxJavaProjection.getInstance(project).snapshot();
        for (var entry : state.generated().entrySet()) {
            ProgressManager.checkCanceled();
            var file = state.files().get(entry.getKey());
            if (file == null || !JxJavaProjection.inScope(project, scope, file)) continue;
            for (String type : entry.getValue().topLevelTypes()) {
                int dot = type.lastIndexOf('.');
                String name = dot < 0 ? "" : type.substring(0, dot);
                while (true) {
                    result.add(name);
                    if (name.isEmpty()) break;
                    int parent = name.lastIndexOf('.');
                    name = parent < 0 ? "" : name.substring(0, parent);
                }
            }
        }
        return result;
    }

    @Override public boolean processPackageDirectories(@NotNull PsiPackage pkg, @NotNull GlobalSearchScope scope,
            @NotNull com.intellij.util.Processor<? super PsiDirectory> processor, boolean includeLibrarySources) {
        if (DumbService.isDumb(project)) return true;
        var state = JxJavaProjection.getInstance(project).snapshot();
        var seen = new HashSet<com.intellij.openapi.vfs.VirtualFile>();
        for (var entry : state.generated().entrySet()) {
            ProgressManager.checkCanceled();
            var file = state.files().get(entry.getKey());
            if (file == null || !JxJavaProjection.inScope(project, scope, file)) continue;
            for (String type : entry.getValue().topLevelTypes()) {
                int dot = type.lastIndexOf('.');
                String name = dot < 0 ? "" : type.substring(0, dot);
                var directory = file.getParent();
                while (directory != null) {
                    if (name.equals(pkg.getQualifiedName())) {
                        if (seen.add(directory)) {
                            var psiDirectory = PsiManager.getInstance(project).findDirectory(directory);
                            if (psiDirectory != null && !processor.process(psiDirectory)) return false;
                        }
                        break;
                    }
                    if (name.isEmpty()) break;
                    int parent = name.lastIndexOf('.');
                    // Only infer physical ancestor directories when their segment agrees.
                    if (!directory.getName().equals(name.substring(parent + 1))) break;
                    name = parent < 0 ? "" : name.substring(0, parent);
                    directory = directory.getParent();
                }
            }
        }
        return true;
    }
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
