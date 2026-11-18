package jx.intellij;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameJavaClassProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import java.util.*;
import javax.lang.model.SourceVersion;
import jx.compiler.SourceSpan;
import org.jetbrains.annotations.NotNull;

/** Renames authored identifiers atomically, without attempting to mutate compiler scaffolding. */
public final class JxRenameClassProcessor extends RenamePsiElementProcessor {
    private static final Set<String> RESTRICTED_TYPE_NAMES = Set.of("var", "yield", "record", "sealed", "permits");
    private record Edit(PsiFile file, TextRange range) {}
    @Override public boolean canProcessElement(@NotNull PsiElement element) {
        return element instanceof JxClassName || element instanceof PsiClass type && type.getQualifiedName() != null &&
                InjectedLanguageManager.getInstance(element.getProject()).getInjectionHost(element) instanceof JxJavaInjectionHost;
    }
    @Override public PsiElement substituteElementToRename(@NotNull PsiElement element, Editor editor) {
        return element instanceof PsiClass type ? JxClassName.from(type) : element;
    }
    @Override public Collection<PsiReference> findReferences(@NotNull PsiElement element, @NotNull SearchScope scope, boolean comments) {
        PsiClass type = element instanceof JxClassName name ? name.javaClass() : (PsiClass) element;
        // Compiler-created references are regenerated from markup. Explicit component-tag
        // references cover authored tag names; only copied Java participates in editing.
        return ReferencesSearch.search(type, scope, false).findAll().stream()
                .filter(reference -> !(InjectedLanguageManager.getInstance(element.getProject())
                        .getInjectionHost(reference.getElement()) instanceof JxJavaInjectionHost)
                        || mapped(reference.getElement(), reference.getRangeInElement()) != null).toList();
    }
    @Override public void findExistingNameConflicts(@NotNull PsiElement element, @NotNull String name,
                                                   @NotNull MultiMap<PsiElement, String> conflicts) {
        if (!(element instanceof JxClassName target)) return;
        if (!validName(name)) {
            conflicts.putValue(element, "A JX class name must be a Java identifier"); return;
        }
        if (!Character.isUpperCase(name.codePointAt(0)) && ReferencesSearch.search(target.javaClass()).findAll().stream()
                .anyMatch(reference -> reference.getElement() instanceof JxJavaInjectionHost host
                        && (reference.getRangeInElement().getStartOffset() == 0
                        || host.getText().charAt(reference.getRangeInElement().getStartOffset() - 1) != '.'))) {
            conflicts.putValue(element, "An unqualified component tag must begin with an uppercase letter; lowercase tags denote HTML elements");
            return;
        }
        new RenameJavaClassProcessor().findExistingNameConflicts(target.javaClass(), name, conflicts);
        var file = target.getContainingFile();
        if (renamesFile(target) && file.getContainingDirectory() != null) {
            var existing = file.getContainingDirectory().findFile(name + ".jx");
            if (existing != null && existing != file) conflicts.putValue(element, "The file " + name + ".jx already exists");
        }
    }
    private static boolean renamesFile(JxClassName target) {
        return target.javaClass().getContainingClass() == null && target.getContainingFile().getName().equals(target.getName() + ".jx");
    }
    @Override public void renameElement(@NotNull PsiElement element, @NotNull String name, UsageInfo @NotNull [] usages,
                                        RefactoringElementListener listener) {
        if (!(element instanceof JxClassName target))
            throw new IncorrectOperationException("Start JX class rename from the authored declaration or a resolved reference");
        if (!validName(name))
            throw new IncorrectOperationException("A JX class name must be a Java identifier");
        var conflicts = new MultiMap<PsiElement, String>(); findExistingNameConflicts(target, name, conflicts);
        if (!conflicts.isEmpty()) throw new IncorrectOperationException(String.join("; ", conflicts.values()));
        String oldName = target.getName(); PsiFile sourceFile = target.getContainingFile();
        String oldFileName = sourceFile.getName(); boolean renameFile = renamesFile(target);
        var edits = new LinkedHashSet<Edit>(); edits.add(new Edit(sourceFile, target.getTextRange()));
        for (PsiMethod constructor : target.javaClass().getConstructors()) {
            var identifier = constructor.getNameIdentifier();
            if (identifier != null) edits.add(required(identifier, new TextRange(0, identifier.getTextLength())));
        }
        for (UsageInfo usage : usages) {
            ProgressManager.checkCanceled();
            PsiReference reference = usage.getReference();
            if (reference == null) throw new IncorrectOperationException("A reference changed; invoke rename again");
            edits.add(required(reference.getElement(), reference.getRangeInElement()));
        }
        var documents = new LinkedHashMap<Document, List<TextRange>>();
        var manager = PsiDocumentManager.getInstance(element.getProject());
        for (Edit edit : edits) {
            if (!edit.file().isWritable()) throw new IncorrectOperationException("A referenced source file is read-only");
            Document document = manager.getDocument(edit.file());
            if (document == null || !oldName.equals(document.getText(edit.range())))
                throw new IncorrectOperationException("An edit cannot be mapped to an unchanged class identifier; rename cancelled");
            documents.computeIfAbsent(document, ignored -> new ArrayList<>()).add(edit.range());
        }
        // Validate every range before the first mutation. The normal IDE command supplies undo.
        var snapshots = new LinkedHashMap<Document, String>();
        documents.forEach((document, ranges) -> {
            ranges.sort(Comparator.comparingInt(TextRange::getStartOffset).reversed());
            for (int i = 1; i < ranges.size(); i++) if (ranges.get(i).getEndOffset() > ranges.get(i - 1).getStartOffset())
                throw new IncorrectOperationException("Overlapping source edits; rename cancelled");
            snapshots.put(document, document.getText());
        });
        try {
            documents.forEach((document, ranges) -> ranges.forEach(range -> document.replaceString(range.getStartOffset(), range.getEndOffset(), name)));
            manager.commitAllDocuments();
            if (renameFile) sourceFile.setName(name + ".jx");
        } catch (RuntimeException failure) {
            snapshots.forEach(Document::setText); manager.commitAllDocuments();
            if (!sourceFile.getName().equals(oldFileName)) sourceFile.setName(oldFileName);
            throw failure;
        }
        if (listener != null) listener.elementRenamed(target.renamed(sourceFile, name));
    }
    private static Edit required(PsiElement element, TextRange range) {
        Edit edit = mapped(element, range);
        if (edit == null) throw new IncorrectOperationException("This edit touches generated Java; rename cancelled");
        return edit;
    }
    private static boolean validName(String name) {
        return SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name) && !RESTRICTED_TYPE_NAMES.contains(name);
    }
    private static Edit mapped(PsiElement element, TextRange relative) {
        PsiFile file = element.getContainingFile();
        TextRange range = relative.shiftRight(element.getTextRange().getStartOffset());
        var injected = InjectedLanguageManager.getInstance(element.getProject());
        var host = injected.getInjectionHost(element);
        if (host instanceof JxJavaInjectionHost jx) {
            var projection = JxJavaProjection.getInstance(element.getProject()).forHost(jx).orElseThrow();
            return projection.sourceMap().editableSourceSpan(new SourceSpan(range.getStartOffset(), range.getEndOffset()))
                    .map(span -> new Edit(jx.getContainingFile(), new TextRange(span.start(), span.end()))).orElse(null);
        }
        if (injected.isInjectedFragment(file)) return null;
        return new Edit(file, range);
    }
}
