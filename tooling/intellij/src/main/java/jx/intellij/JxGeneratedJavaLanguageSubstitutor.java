package jx.intellij;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.LanguageSubstitutor;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

/** Keeps proven Maven-generated counterparts out of Java PSI/indexes without changing source roots
 * or the physical Java file type used by compilers. Unowned Java always remains ordinary Java. */
public final class JxGeneratedJavaLanguageSubstitutor extends LanguageSubstitutor {
    private static final Logger LOG = Logger.getInstance(JxGeneratedJavaLanguageSubstitutor.class);
    @Override public Language getLanguage(@NotNull VirtualFile file, @NotNull Project project) {
        Layout layout = layout(file);
        if (layout == null) return null;
        SourceReference declared = declaredSource(file);
        VirtualFile authored = resolveSource(layout, declared);
        if (project.isDisposed() || !JxGeneratedJavaLifecycle.getInstance(project)
                .observe(file, layout, declared == null ? null : declared.path(), authored != null)) return null;
        return authored == null ? null : PlainTextLanguage.INSTANCE;
    }

    record Layout(VirtualFile target, String phase, String relative) {
        String manifestPath() { return target.getPath() + "/jx/" + phase + "/outputs.tsv"; }
        String mappingPath() { return target.getPath() + "/jx/" + phase + "/" + relative.substring(0, relative.length() - 5) + ".jxmap"; }
    }

    static Layout layout(VirtualFile file) {
        if (!"java".equals(file.getExtension())) return null;
        for (VirtualFile root = file.getParent(); root != null; root = root.getParent()) {
            if (!root.getName().equals("jx")) continue;
            VirtualFile kind = root.getParent();
            if (kind == null) continue;
            String phase = switch (kind.getName()) {
                case "generated-sources" -> "main";
                case "generated-test-sources" -> "test";
                default -> null;
            };
            VirtualFile target = kind.getParent();
            if (phase == null || target == null || !target.getName().equals("target")) continue;
            String relative = VfsUtilCore.getRelativePath(file, root, '/');
            if (relative == null) return null;
            return new Layout(target, phase, relative);
        }
        return null;
    }

    static VirtualFile authoredSource(VirtualFile file) {
        Layout layout = layout(file);
        return layout == null ? null : resolveSource(layout, declaredSource(file));
    }

    record SourceReference(String url, String path) {}

    static VirtualFile resolveSource(Layout layout, SourceReference reference) {
        if (reference == null) return null;
        VirtualFile source = VirtualFileManager.getInstance().findFileByUrl(reference.url());
        return source != null && source.isValid() && "jx".equals(source.getExtension())
                && layout.target().getParent() != null && VfsUtilCore.isAncestor(layout.target().getParent(), source, true) ? source : null;
    }

    /** Validated metadata target even before the authored file appears; this is a bounded local VFS
     * watch relationship, not permission to resolve or read a path outside the Maven module. */
    static SourceReference declaredSource(VirtualFile file) {
        Layout layout = layout(file);
        if (layout == null) return null;
        VirtualFile target = layout.target();
        String relative = layout.relative();
        VirtualFile manifest = target.findFileByRelativePath("jx/" + layout.phase() + "/outputs.tsv");
        VirtualFile mapping = target.findFileByRelativePath("jx/" + layout.phase() + "/"
                + relative.substring(0, relative.length() - 5) + ".jxmap");
        if (manifest == null || mapping == null || manifest.getLength() > 2 * 1024 * 1024) return null;
        try {
            byte[] bytes;
            try (var input = manifest.getInputStream()) { bytes = input.readNBytes(2 * 1024 * 1024 + 1); }
            if (bytes.length > 2 * 1024 * 1024) return null;
            String ownership = new String(bytes, StandardCharsets.UTF_8);
            if (!ownership.startsWith("jx-outputs-v1\n") || ownership.lines().skip(1)
                    .noneMatch(line -> line.startsWith(relative + "\t"))) return null;
            String header;
            try (var input = mapping.getInputStream()) {
                header = new String(input.readNBytes(16 * 1024), StandardCharsets.UTF_8);
            }
            int newline = header.indexOf('\n');
            if (newline < 0 || !header.startsWith("jx-map-v1\t", newline + 1)) return null;
            URI sourceUri = URI.create(header.substring(0, newline));
            if (sourceUri.getScheme() == null || sourceUri.getPath() == null
                    || sourceUri.getQuery() != null || sourceUri.getFragment() != null
                    || sourceUri.getAuthority() != null && !sourceUri.getAuthority().isEmpty()
                    || target.getParent() == null
                    || !sourceUri.getScheme().equals(target.getFileSystem().getProtocol())) return null;
            Path sourcePath = Path.of(sourceUri.getPath()).normalize();
            Path modulePath = Path.of(target.getParent().getPath()).normalize();
            if (!sourcePath.startsWith(modulePath) || sourcePath.equals(modulePath)
                    || !sourcePath.toString().endsWith(".jx")) return null;
            // Maven writes Path.toUri() (percent-encoded); VFS URLs use a decoded path. Retain the
            // protocol so IDE fixture/non-local filesystems also resolve through their own VFS.
            String path = sourcePath.toString().replace('\\', '/');
            return new SourceReference(VirtualFileManager.constructUrl(sourceUri.getScheme(), path), path);
        } catch (IOException | IllegalArgumentException failure) {
            LOG.debug("Cannot establish JX generated-source ownership for " + file, failure);
            return null;
        }
    }
}
