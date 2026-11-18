package jx.intellij;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.platform.lsp.api.LspServerManager;
import org.jetbrains.annotations.NotNull;

public final class RestartServerAction extends AnAction {
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    @Override public void update(@NotNull AnActionEvent event) {
        var project = event.getProject();
        event.getPresentation().setEnabled(project != null && TrustedProjects.isTrusted(project));
    }
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        var project = event.getProject();
        if (project != null && TrustedProjects.isTrusted(project)) LspServerManager.getInstance(project).stopAndRestartIfNeeded(JxLspSupportProvider.class);
    }
}
