package jx.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class JxLspServerDescriptor extends ProjectWideLspServerDescriptor {
    public JxLspServerDescriptor(Project project) { super(project, "JX"); }
    @Override public boolean isSupportedFile(@NotNull VirtualFile file) { return "jx".equals(file.getExtension()); }
    @Override public @NotNull String getLanguageId(@NotNull VirtualFile file) { return "jx"; }
    public record InitializationOptions(List<String> classpath) {}
    @Override public Object createInitializationOptions() { return new InitializationOptions(List.copyOf(JxSettings.getInstance().getState().classpath)); }
    @Override public @NotNull GeneralCommandLine createCommandLine() throws ExecutionException {
        if (!TrustedProjects.isTrusted(getProject())) throw new ExecutionException("JX requires a trusted project before starting Java.");
        String java = JxSettings.getInstance().getState().javaPath;
        if (java == null || java.isBlank()) throw new ExecutionException("Configure a Java 25 executable under Settings > Languages & Frameworks > JX.");
        try {
            Path jar = prepareJar();
            // Project roots arrive through LSP initialization; the process uses a stable
            // plugin-owned directory even while a project is closing or being renamed.
            return new GeneralCommandLine(java, "-jar", jar.toString()).withWorkDirectory(jar.getParent().toFile());
        } catch (IOException failure) { throw new ExecutionException("Cannot prepare the bundled JX language server", failure); }
    }
    private static synchronized Path prepareJar() throws IOException {
        try (var resource = JxLspServerDescriptor.class.getResourceAsStream("/server/jx-language-server.jar")) {
            if (resource == null) throw new IOException("The plugin is missing its bundled language server.");
            byte[] content = resource.readAllBytes();
            String hash;
            try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
            catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("Java requires SHA-256", impossible); }
            Path directory = Path.of(PathManager.getSystemPath(), "jx", "0.1.0");
            Files.createDirectories(directory);
            Path jar = directory.resolve("language-server-" + hash + ".jar");
            if (Files.isRegularFile(jar)) return jar;
            Path temporary = Files.createTempFile(directory, "language-server-", ".jar");
            try {
                Files.write(temporary, content);
                Files.move(temporary, jar, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temporary); }
            return jar;
        }
    }
}
