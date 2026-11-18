package jx.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public final class StopDevelopmentAction extends AnAction implements DumbAware {
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    @Override public void update(@NotNull AnActionEvent event) {
        var project = event.getProject();
        event.getPresentation().setEnabled(project != null && !project.isDisposed() && JxDevelopmentService.getInstance(project).isRunning());
    }
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        if (event.getProject() != null) JxDevelopmentService.getInstance(event.getProject()).stop();
    }
}
