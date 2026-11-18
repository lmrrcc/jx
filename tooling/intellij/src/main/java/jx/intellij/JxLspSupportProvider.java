package jx.intellij;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.LspServerSupportProvider;
import org.jetbrains.annotations.NotNull;

public final class JxLspSupportProvider implements LspServerSupportProvider {
    @Override public void fileOpened(@NotNull Project project, @NotNull VirtualFile file, @NotNull LspServerStarter starter) {
        if ("jx".equals(file.getExtension()) && TrustedProjects.isTrusted(project)) {
            starter.ensureServerStarted(new JxLspServerDescriptor(project));
        }
    }
}
