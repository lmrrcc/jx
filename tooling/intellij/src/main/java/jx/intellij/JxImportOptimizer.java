package jx.intellij;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import java.util.HashSet;
import java.util.Set;
import jx.compiler.JxCompiler;
import jx.compiler.SourceSpan;
import org.jetbrains.annotations.NotNull;

/** Makes the ordinary Optimize Imports action available from markup, using Java's optimizer. */
public final class JxImportOptimizer implements ImportOptimizer {
    private static final Runnable NO_CHANGE = () -> {};

    @Override public boolean supports(@NotNull PsiFile file) {
        return file.getLanguage() == JxLanguage.INSTANCE;
    }

    @Override public @NotNull Runnable processFile(@NotNull PsiFile file) {
        if (!supports(file) || !file.isWritable() || DumbService.isDumb(file.getProject())) return NO_CHANGE;
        var host = JxJavaProjection.host(file);
        if (host == null) return NO_CHANGE;
        var generated = JxJavaProjection.getInstance(file.getProject()).forHost(host).orElse(null);
        var java = JxJavaProjection.javaFile(file);
        if (generated == null || java == null || !java.getText().equals(generated.javaSource())) return NO_CHANGE;
        // Incomplete markup may hide references from the recovered Java view. Do not remove
        // imports based on a temporary parse recovery while a tag or expression is being typed.
        if (!new JxCompiler().parse(generated.source()).diagnostics().isEmpty()) return NO_CHANGE;
        PsiImportList original = java.getImportList();
        if (original == null || original.getTextLength() == 0) return NO_CHANGE;
        var range = original.getTextRange();
        var mapped = generated.sourceMap().editableSourceSpan(new SourceSpan(range.getStartOffset(), range.getEndOffset()));
        if (mapped.isEmpty()) return NO_CHANGE;
        PsiImportList optimized = JavaCodeStyleManager.getInstance(file.getProject()).prepareOptimizeImportsResult(java);
        if (optimized == null || removesMarkupImport(host.getText(), original, optimized)) return NO_CHANGE;
        // Like JavaImportOptimizer, include the prepared list's sibling comments. Replacing
        // just getText() of the import list would silently discard comments moved outside it.
        String imports = optimized.getParent().getText();
        String snapshot = host.getText();
        SourceSpan sourceRange = mapped.orElseThrow();
        String replacement = snapshot.substring(0, sourceRange.start()) + imports + snapshot.substring(sourceRange.end());
        if (replacement.equals(snapshot)) return NO_CHANGE;
        return () -> {
            if (host.isValid() && host.isWritable() && host.getText().equals(snapshot)) host.updateText(replacement);
        };
    }

    /** Unknown component metadata can lower to null. Never let that remove a markup-only import. */
    private static boolean removesMarkupImport(String source, PsiImportList before, PsiImportList after) {
        Set<String> componentRoots = new HashSet<>();
        var lexer = new JxLexer();
        lexer.start(source);
        while (lexer.getTokenType() != null) {
            if (lexer.getTokenType() == JxLexer.TAG) {
                String name = source.substring(lexer.getTokenStart(), lexer.getTokenEnd());
                if (!name.isEmpty() && (Character.isUpperCase(name.charAt(0)) || name.indexOf('.') >= 0))
                    componentRoots.add(name.split("\\.", 2)[0]);
            }
            lexer.advance();
        }
        if (componentRoots.isEmpty()) return false;
        Set<String> retained = new HashSet<>();
        for (PsiImportStatementBase statement : after.getAllImportStatements()) retained.add(statement.getText());
        for (PsiImportStatementBase statement : before.getAllImportStatements()) {
            if (retained.contains(statement.getText())) continue;
            var reference = statement.getImportReference();
            if (statement.isOnDemand() || reference != null && componentRoots.contains(reference.getReferenceName())) return true;
        }
        return false;
    }
}
