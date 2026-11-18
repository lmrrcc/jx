package jx.intellij;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import jx.compiler.SourceSpan;
import org.jetbrains.annotations.NotNull;

/** An authored class-name declaration, distinct from the generated Java class containing it. */
final class JxClassName extends LightElement implements PsiNamedElement {
    private final SmartPsiElementPointer<PsiElement> identifier;
    private final String qualifiedName;
    private JxClassName(PsiClass type, PsiElement identifier) {
        super(type.getManager(), JxLanguage.INSTANCE);
        this.identifier = SmartPointerManager.getInstance(type.getProject()).createSmartPsiElementPointer(identifier);
        qualifiedName = type.getQualifiedName();
    }
    static JxClassName from(PsiClass type) {
        var manager = InjectedLanguageManager.getInstance(type.getProject());
        if (!(manager.getInjectionHost(type) instanceof JxJavaInjectionHost host) || type.getNameIdentifier() == null || type.getQualifiedName() == null)
            throw new IncorrectOperationException("Expected an authored JX class declaration");
        TextRange range = type.getNameIdentifier().getTextRange();
        var projection = JxJavaProjection.getInstance(type.getProject()).forHost(host).orElseThrow();
        var original = projection.sourceMap().editableSourceSpan(new SourceSpan(range.getStartOffset(), range.getEndOffset()))
                .orElseThrow(() -> new IncorrectOperationException("Generated class names cannot be renamed"));
        PsiElement identifier = host.getContainingFile().findElementAt(original.start());
        if (identifier == null || !identifier.getTextRange().equals(new TextRange(original.start(), original.end())))
            throw new IncorrectOperationException("The authored class identifier cannot be mapped exactly");
        return new JxClassName(type, identifier);
    }
    PsiClass javaClass() {
        var java = JxJavaProjection.javaFile(getContainingFile());
        if (java != null) for (PsiClass type : java.getClasses()) {
            var found = find(type, qualifiedName); if (found != null) return found;
        }
        throw new IncorrectOperationException("The class changed; invoke rename again");
    }
    JxClassName renamed(PsiFile file, String name) {
        String next = qualifiedName.substring(0, qualifiedName.lastIndexOf('.') + 1) + name;
        var java = JxJavaProjection.javaFile(file);
        if (java != null) for (PsiClass type : java.getClasses()) {
            var found = find(type, next); if (found != null) return from(found);
        }
        throw new IncorrectOperationException("The renamed JX class cannot be resolved");
    }
    private static PsiClass find(PsiClass type, String qualifiedName) {
        if (qualifiedName != null && qualifiedName.equals(type.getQualifiedName())) return type;
        for (PsiClass nested : type.getInnerClasses()) { var found = find(nested, qualifiedName); if (found != null) return found; }
        return null;
    }
    private PsiElement identifier() {
        var element = identifier.getElement();
        if (element == null) throw new IncorrectOperationException("The class declaration changed; invoke rename again");
        return element;
    }
    @Override public String getName() { return identifier().getText(); }
    @Override public PsiElement setName(@NotNull String name) {
        throw new IncorrectOperationException("Rename JX classes with the mapped class rename processor");
    }
    @Override public PsiFile getContainingFile() { return identifier().getContainingFile(); }
    @Override public PsiElement getParent() { return identifier().getParent(); }
    @Override public TextRange getTextRange() { return identifier().getTextRange(); }
    @Override public int getTextOffset() { return identifier().getTextOffset(); }
    @Override public String getText() { return getName(); }
    @Override public boolean isValid() { return identifier.getElement() != null; }
    @Override public boolean isWritable() { return isValid() && identifier().isWritable(); }
    @Override public boolean isPhysical() { return true; }
    @Override public @NotNull PsiElement getNavigationElement() { return identifier(); }
    @Override public @NotNull SearchScope getUseScope() { return GlobalSearchScope.projectScope(getProject()); }
    @Override public String toString() { return "JX class name: " + qualifiedName; }
}
