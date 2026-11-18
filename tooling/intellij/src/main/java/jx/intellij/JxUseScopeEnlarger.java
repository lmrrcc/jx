package jx.intellij;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import org.jetbrains.annotations.NotNull;

/** Injected public declarations are real project symbols, not local string-literal declarations. */
public final class JxUseScopeEnlarger extends UseScopeEnlarger {
    @Override public SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
        if (!(element instanceof PsiMember)) return null;
        var host = InjectedLanguageManager.getInstance(element.getProject()).getInjectionHost(element);
        return host instanceof JxJavaInjectionHost ? GlobalSearchScope.projectScope(element.getProject()) : null;
    }
}
