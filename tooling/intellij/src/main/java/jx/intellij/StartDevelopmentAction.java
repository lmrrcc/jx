package jx.intellij;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ProjectFileIndex;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public final class StartDevelopmentAction extends AnAction implements DumbAware {
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    @Override public void update(@NotNull AnActionEvent event) {
        var project = event.getProject(); var file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        event.getPresentation().setEnabled(project != null && !project.isDisposed() && TrustedProjects.isTrusted(project)
                && file != null && file.isInLocalFileSystem() && "jx".equals(file.getExtension())
                && ProjectFileIndex.getInstance(project).isInContent(file));
    }
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        var project = event.getProject(); var file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project != null && file != null && file.isInLocalFileSystem() && "jx".equals(file.getExtension())
                && ProjectFileIndex.getInstance(project).isInContent(file)) {
            JxDevelopmentService.getInstance(project).start(Path.of(file.getPath()));
        }
    }
}
