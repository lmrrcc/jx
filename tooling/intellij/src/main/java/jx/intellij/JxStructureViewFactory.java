package jx.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Native Structure/File Structure UI over authored declarations, without generated helpers. */
public final class JxStructureViewFactory implements PsiStructureViewFactory {
    @Override public @NotNull TreeBasedStructureViewBuilder getStructureViewBuilder(@NotNull PsiFile file) {
        return new TreeBasedStructureViewBuilder() {
            @Override public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                return new StructureViewModelBase(file, editor, new Entry(file, file)) {
                    @Override public Object getCurrentEditorElement() {
                        if (editor == null) return file;
                        var view = JxSourceNavigation.view(file);
                        if (view.isEmpty()) return file;
                        int offset = editor.getCaretModel().getOffset();
                        return PsiTreeUtil.findChildrenOfType(view.get().javaFile(), PsiNameIdentifierOwner.class).stream()
                                .filter(named -> visible(named, view.get()))
                                .filter(named -> view.get().extent(named).filter(range -> range.contains(offset)).isPresent())
                                .min(Comparator.comparingInt(PsiElement::getTextLength))
                                .<Object>map(named -> named).orElse(file);
                    }
                };
            }
        };
    }

    private static boolean visible(PsiNameIdentifierOwner named, JxSourceNavigation.View view) {
        return (named instanceof PsiClass || named instanceof PsiMethod || named instanceof PsiField
                || named instanceof PsiRecordComponent) && view.copied(named.getNameIdentifier()).isPresent();
    }

    private static final class Entry implements StructureViewTreeElement {
        private final SmartPsiElementPointer<PsiFile> file;
        private final SmartPsiElementPointer<PsiElement> element;
        Entry(PsiFile file, PsiElement element) {
            var manager = SmartPointerManager.getInstance(file.getProject());
            this.file = manager.createSmartPsiElementPointer(file);
            this.element = manager.createSmartPsiElementPointer(element);
        }
        @Override public Object getValue() { return element.getElement(); }
        @Override public TreeElement @NotNull [] getChildren() {
            var authored = file.getElement();
            var current = element.getElement();
            var view = JxSourceNavigation.view(authored);
            if (view.isEmpty() || current == null) return EMPTY_ARRAY;
            var children = new ArrayList<PsiNameIdentifierOwner>();
            if (current instanceof PsiFile) children.addAll(Arrays.asList(view.get().javaFile().getClasses()));
            else if (current instanceof PsiClass type) {
                for (PsiElement child : type.getChildren()) {
                    if (child instanceof PsiNameIdentifierOwner named) children.add(named);
                }
                children.addAll(Arrays.asList(type.getRecordComponents()));
            }
            return children.stream().filter(child -> visible(child, view.get()))
                    .sorted(Comparator.comparingInt(PsiElement::getTextOffset))
                    .map(child -> new Entry(authored, child)).toArray(TreeElement[]::new);
        }
        @Override public @NotNull ItemPresentation getPresentation() {
            return new ItemPresentation() {
                @Override public String getPresentableText() {
                    PsiElement value = element.getElement();
                    if (value instanceof PsiFile source) return source.getName();
                    if (value instanceof PsiMethod method) {
                        String parameters = String.join(", ", Arrays.stream(method.getParameterList().getParameters())
                                .map(parameter -> parameter.getType().getPresentableText()).toList());
                        return method.getName() + "(" + parameters + ")";
                    }
                    if (value instanceof PsiVariable variable) return variable.getName() + ": " + variable.getType().getPresentableText();
                    if (value instanceof PsiNamedElement named) return named.getName();
                    return "";
                }
                @Override public @Nullable String getLocationString() { return null; }
                @Override public @Nullable Icon getIcon(boolean unused) {
                    PsiElement value = element.getElement();
                    if (value instanceof PsiClass) return AllIcons.Nodes.Class;
                    if (value instanceof PsiMethod) return AllIcons.Nodes.Method;
                    if (value instanceof PsiVariable) return AllIcons.Nodes.Field;
                    return JxFileType.INSTANCE.getIcon();
                }
            };
        }
        private OpenFileDescriptor descriptor() {
            var authored = file.getElement();
            var current = element.getElement();
            if (authored == null || authored.getVirtualFile() == null || current == null) return null;
            if (current instanceof PsiFile) return new OpenFileDescriptor(authored.getProject(), authored.getVirtualFile(), 0);
            if (!(current instanceof PsiNameIdentifierOwner named)) return null;
            return JxSourceNavigation.view(authored).flatMap(view -> view.copied(named.getNameIdentifier()))
                    .map(range -> new OpenFileDescriptor(authored.getProject(), authored.getVirtualFile(), range.getStartOffset())).orElse(null);
        }
        @Override public void navigate(boolean requestFocus) {
            var target = descriptor();
            if (target != null) { try { target.navigate(requestFocus); } finally { target.dispose(); } }
        }
        @Override public boolean canNavigate() {
            var target = descriptor();
            if (target == null) return false;
            try { return target.canNavigate(); } finally { target.dispose(); }
        }
        @Override public boolean canNavigateToSource() { return canNavigate(); }
    }
}
