package jx.intellij;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** One native Java compilation unit, with only exact copied source ranges editable. */
public final class JxJavaInjector implements MultiHostInjector {
    @Override public @NotNull List<Class<? extends PsiElement>> elementsToInjectIn() { return List.of(JxJavaInjectionHost.class); }
    @Override public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof JxJavaInjectionHost host)) return;
        var projected = JxJavaProjection.getInstance(host.getProject()).forHost(host);
        if (projected.isEmpty()) return;
        var source = projected.orElseThrow();
        var copied = source.sourceMap().segments().stream().filter(segment -> segment.linear() && segment.length() > 0).toList();
        if (copied.isEmpty()) return;
        registrar.startInjecting(JavaLanguage.INSTANCE);
        int generatedOffset = 0;
        for (int index = 0; index < copied.size(); index++) {
            ProgressManager.checkCanceled();
            var segment = copied.get(index);
            String prefix = source.javaSource().substring(generatedOffset, segment.generatedStart());
            generatedOffset = segment.generatedStart() + segment.length();
            String suffix = index == copied.size() - 1 ? source.javaSource().substring(generatedOffset) : "";
            registrar.addPlace(prefix, suffix, host, new TextRange(segment.sourceStart(), segment.sourceStart() + segment.length()));
        }
        registrar.doneInjecting();
    }
}
