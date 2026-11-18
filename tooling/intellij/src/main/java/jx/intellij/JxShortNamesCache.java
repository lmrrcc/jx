package jx.intellij;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.Processor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Supplies source symbols to Java's normal auto-import and Go to Class/Symbol providers. */
public final class JxShortNamesCache extends PsiShortNamesCache {
    private final Project project;
    public JxShortNamesCache(Project project) { this.project = project; }
    private List<PsiClass> classes(GlobalSearchScope scope) {
        var classes = new ArrayList<PsiClass>();
        if (DumbService.isDumb(project)) return classes;
        for (var entry : JxJavaProjection.getInstance(project).snapshot().files().entrySet()) {
            ProgressManager.checkCanceled();
            if (!JxJavaProjection.inScope(project, scope, entry.getValue())) continue;
            var file = PsiManager.getInstance(project).findFile(entry.getValue());
            var java = file == null ? null : JxJavaProjection.javaFile(file);
            if (java != null) for (PsiClass type : java.getClasses()) collect(type, classes);
        }
        return classes;
    }
    private static void collect(PsiClass type, List<PsiClass> classes) {
        classes.add(type); for (PsiClass nested : type.getInnerClasses()) collect(nested, classes);
    }
    @Override public PsiClass @NotNull [] getClassesByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
        return classes(scope).stream().filter(type -> name.equals(type.getName())).toArray(PsiClass[]::new);
    }
    @Override public String @NotNull [] getAllClassNames() {
        return classes(GlobalSearchScope.projectScope(project)).stream().map(PsiClass::getName).filter(java.util.Objects::nonNull).distinct().toArray(String[]::new);
    }
    @Override public PsiMethod @NotNull [] getMethodsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
        return classes(scope).stream().flatMap(type -> Arrays.stream(type.getMethods())).filter(method -> name.equals(method.getName())).toArray(PsiMethod[]::new);
    }
    @Override public PsiMethod @NotNull [] getMethodsByNameIfNotMoreThan(@NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
        var methods = getMethodsByName(name, scope); return methods.length <= maxCount ? methods : PsiMethod.EMPTY_ARRAY;
    }
    @Override public boolean processMethodsWithName(@NotNull String name, @NotNull GlobalSearchScope scope, @NotNull Processor<? super PsiMethod> processor) {
        for (PsiMethod method : getMethodsByName(name, scope)) if (!processor.process(method)) return false;
        return true;
    }
    @Override public String @NotNull [] getAllMethodNames() {
        return classes(GlobalSearchScope.projectScope(project)).stream().flatMap(type -> Arrays.stream(type.getMethods())).map(PsiMethod::getName).distinct().toArray(String[]::new);
    }
    @Override public PsiField @NotNull [] getFieldsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
        return classes(scope).stream().flatMap(type -> Arrays.stream(type.getFields())).filter(field -> name.equals(field.getName())).toArray(PsiField[]::new);
    }
    @Override public PsiField @NotNull [] getFieldsByNameIfNotMoreThan(@NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
        var fields = getFieldsByName(name, scope); return fields.length <= maxCount ? fields : PsiField.EMPTY_ARRAY;
    }
    @Override public String @NotNull [] getAllFieldNames() {
        return classes(GlobalSearchScope.projectScope(project)).stream().flatMap(type -> Arrays.stream(type.getFields())).map(PsiField::getName).distinct().toArray(String[]::new);
    }
}
