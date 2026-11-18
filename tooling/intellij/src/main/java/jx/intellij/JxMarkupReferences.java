package jx.intellij;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import java.util.ArrayList;
import java.util.List;
import jx.compiler.JxCompiler;
import jx.compiler.SourceFile;
import jx.compiler.Syntax;
import org.jetbrains.annotations.NotNull;

/** References in markup complement Java's native references in editable projection fragments. */
public final class JxMarkupReferences extends PsiReferenceContributor {
    @Override public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(JxJavaInjectionHost.class), new PsiReferenceProvider() {
            @Override public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                JxJavaInjectionHost host = (JxJavaInjectionHost) element;
                return CachedValuesManager.getCachedValue(host,
                        () -> CachedValueProvider.Result.create(references(host), host));
            }
        });
    }
    private static PsiReference[] references(JxJavaInjectionHost host) {
        var references = new ArrayList<PsiReference>();
        String text = host.getText();
        var lexer = new JxLexer(); lexer.start(text);
        while (lexer.getTokenType() != null) {
            if (lexer.getTokenType() == JxLexer.TAG) {
                String name = text.substring(lexer.getTokenStart(), lexer.getTokenEnd());
                if (component(name)) references.add(new Reference(host,
                        new TextRange(lexer.getTokenStart() + name.lastIndexOf('.') + 1, lexer.getTokenEnd()), name, null));
            }
            lexer.advance();
        }
        var parsed = new JxCompiler().parse(new SourceFile(JxJavaProjection.path(host.getContainingFile()), text));
        properties(host, parsed.nodes(), references);
        return references.toArray(PsiReference.EMPTY_ARRAY);
    }
    private static boolean component(String name) {
        return !name.isEmpty() && (Character.isUpperCase(name.charAt(0)) || name.contains("."));
    }
    private static void properties(JxJavaInjectionHost host, List<Syntax.Node> nodes, List<PsiReference> references) {
        for (Syntax.Node node : nodes) {
            if (node instanceof Syntax.Interpolation interpolation) properties(host, interpolation.expression(), references);
            else if (node instanceof Syntax.Element element) {
                for (var attribute : element.attributes()) {
                    if (component(element.name()) && !attribute.name().equals("key") && !attribute.name().equals("ref"))
                        references.add(new Reference(host, new TextRange(attribute.nameSpan().start(), attribute.nameSpan().end()),
                                element.name(), attribute.name()));
                    if (attribute.expression() != null) properties(host, attribute.expression().expression(), references);
                }
                properties(host, element.children(), references);
            }
        }
    }
    private static final class Reference extends PsiReferenceBase<JxJavaInjectionHost> {
        private final String componentName, propertyName;
        Reference(JxJavaInjectionHost host, TextRange range, String componentName, String propertyName) {
            super(host, range, false); this.componentName = componentName; this.propertyName = propertyName;
        }
        @Override public PsiElement resolve() {
            var java = JxJavaProjection.javaFile(myElement.getContainingFile());
            if (java == null) return null;
            var resolved = JavaPsiFacade.getElementFactory(myElement.getProject()).createReferenceFromText(componentName, java).resolve();
            if (!(resolved instanceof PsiClass type)) return null;
            if (propertyName == null) return type;
            PsiClass props = type.findInnerClassByName("Props", false);
            if (props != null) for (var property : props.getRecordComponents())
                if (propertyName.equals(property.getName())) return property;
            return null;
        }
        @Override public @NotNull PsiElement handleElementRename(@NotNull String name) throws IncorrectOperationException {
            if (!javax.lang.model.SourceVersion.isIdentifier(name) || javax.lang.model.SourceVersion.isKeyword(name))
                throw new IncorrectOperationException("JX component and property names must be Java identifiers");
            String text = myElement.getText(); TextRange range = getRangeInElement();
            return myElement.updateText(text.substring(0, range.getStartOffset()) + name + text.substring(range.getEndOffset()));
        }
        @Override public @NotNull PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
            if (element instanceof PsiClass type && type.getName() != null) return handleElementRename(type.getName());
            throw new IncorrectOperationException("Expected a Java component class");
        }
    }
}
