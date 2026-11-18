package jx.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.indexing.FileBasedIndex;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Tracks only generated Java already encountered by this project's language substitutor. Ownership
 * metadata events invalidate those exact files; no source roots, global caches, or directory scans
 * are modified. The application-bus subscription is disposed with its owning project service. */
@Service(Service.Level.PROJECT)
public final class JxGeneratedJavaLifecycle implements BulkFileListener, Disposable {
    private static final int MAX_TRACKED_FILES = 10_000;
    private record Entry(String manifest, String mapping, String source, boolean owned) {}
    private final Project project;
    private final Map<VirtualFile, Entry> observed = new LinkedHashMap<>();
    private final Set<VirtualFile> pending = new LinkedHashSet<>();
    private boolean scheduled;
    private boolean disposed;

    public JxGeneratedJavaLifecycle(Project project) {
        this.project = project;
        ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, this);
    }
    static JxGeneratedJavaLifecycle getInstance(Project project) { return project.getService(JxGeneratedJavaLifecycle.class); }

    synchronized boolean observe(VirtualFile file, JxGeneratedJavaLanguageSubstitutor.Layout layout, String source, boolean owned) {
        if (disposed || project.isDisposed()) return false;
        Entry previous = observed.get(file);
        if (previous == null && observed.size() >= MAX_TRACKED_FILES) {
            observed.keySet().removeIf(candidate -> !candidate.isValid());
            if (observed.size() >= MAX_TRACKED_FILES) return false;
        }
        // Retain the last source path while it is absent so delete/undo and rename/undo also refresh
        // ownership. A changed, valid source map replaces it on the next targeted reparse.
        String authored = source != null ? source : previous == null ? null : previous.source();
        // Indexing can ask for the new language before existing PSI has been recreated. Preserve
        // the last applied classification until our targeted reparse completes.
        observed.put(file, new Entry(layout.manifestPath(), layout.mappingPath(), authored,
                previous == null ? owned : previous.owned()));
        return true;
    }

    @Override public void before(@NotNull List<? extends VFileEvent> events) { collect(events); }
    @Override public void after(@NotNull List<? extends VFileEvent> events) {
        collect(events);
        synchronized (this) {
            observed.keySet().removeIf(file -> !file.isValid());
            if (disposed || project.isDisposed() || scheduled || pending.isEmpty()) return;
            scheduled = true;
        }
        // PSI must be recreated after the VFS batch commits, not recursively inside its event.
        // The callback itself clears the scheduling flag and checks disposal. An expired queued
        // callback would never clear that flag (also relevant to temporarily disposed IDE projects).
        ApplicationManager.getApplication().invokeLater(this::refresh, ModalityState.nonModal());
    }

    private synchronized void collect(List<? extends VFileEvent> events) {
        if (disposed || project.isDisposed() || observed.isEmpty()) return;
        for (VFileEvent event : events) {
            boolean directory = event instanceof VFileCreateEvent creation ? creation.isDirectory()
                    : event.getFile() != null && event.getFile().isDirectory();
            List<String> paths = paths(event);
            if (!directory && paths.stream().noneMatch(path -> path.endsWith("/outputs.tsv") || path.endsWith(".jxmap")
                    || path.endsWith(".jx") && !(event instanceof VFileContentChangeEvent))) continue;
            for (var entry : observed.entrySet()) {
                Entry watched = entry.getValue();
                if (paths.stream().anyMatch(path -> matches(path, watched.manifest(), directory)
                        || matches(path, watched.mapping(), directory) || matches(path, watched.source(), directory)))
                    pending.add(entry.getKey());
            }
        }
    }

    private static List<String> paths(VFileEvent event) {
        if (event instanceof VFileMoveEvent move) return List.of(move.getOldPath(), move.getNewPath());
        if (event instanceof VFilePropertyChangeEvent rename && rename.isRename()) return List.of(rename.getOldPath(), rename.getNewPath());
        if (event instanceof VFileCopyEvent copy) return List.of(copy.getNewParent().getPath() + "/" + copy.getNewChildName());
        return List.of(event.getPath());
    }
    private static boolean matches(String changed, String watched, boolean directory) {
        return watched != null && (watched.equals(changed) || directory && watched.startsWith(changed + "/"));
    }

    private void refresh() {
        List<VirtualFile> candidates;
        synchronized (this) {
            scheduled = false;
            if (disposed || project.isDisposed()) { pending.clear(); return; }
            candidates = new ArrayList<>(pending.stream().filter(VirtualFile::isValid).toList());
            pending.clear();
        }
        List<VirtualFile> files = new ArrayList<>();
        Map<VirtualFile, Boolean> classifications = new LinkedHashMap<>();
        for (VirtualFile file : candidates) {
            if (project.isDisposed()) return;
            var layout = JxGeneratedJavaLanguageSubstitutor.layout(file);
            if (layout == null) continue;
            var declared = JxGeneratedJavaLanguageSubstitutor.declaredSource(file);
            VirtualFile source = JxGeneratedJavaLanguageSubstitutor.resolveSource(layout, declared);
            synchronized (this) {
                Entry previous = observed.get(file);
                if (previous != null && previous.owned() != (source != null)) {
                    files.add(file); classifications.put(file, source != null);
                }
            }
            observe(file, layout, declared == null ? null : declared.path(), source != null);
        }
        if (files.isEmpty() || project.isDisposed()) return;
        FileContentUtilCore.reparseFiles(files);
        synchronized (this) {
            classifications.forEach((file, owned) -> observed.computeIfPresent(file,
                    (ignored, entry) -> new Entry(entry.manifest(), entry.mapping(), entry.source(), owned)));
        }
        for (VirtualFile file : files) if (file.isValid() && !project.isDisposed()) FileBasedIndex.getInstance().requestReindex(file);
    }

    @Override public synchronized void dispose() {
        disposed = true;
        observed.clear(); pending.clear(); scheduled = false;
    }
}
