package jx.intellij;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jx.compiler.EditorProjection;
import jx.compiler.GeneratedSource;
import jx.compiler.SourceFile;

/** Read-action-only, project-owned source snapshots; never executes a build or project code. */
@Service(Service.Level.PROJECT)
public final class JxJavaProjection {
    private static final Logger LOG = Logger.getInstance(JxJavaProjection.class);
    private static final int MAX_FILES = 10_000, MAX_SOURCE_CHARS = 4 * 1024 * 1024;
    private static final long MAX_TOTAL_CHARS = 64L * 1024 * 1024;
    private static final long MAX_RETAINED_CHARS = 64L * 1024 * 1024;
    private static final long MAX_TRANSIENT_CHARS = 16L * 1024 * 1024;
    private static final Key<CachedValue<Snapshot>> SNAPSHOT = Key.create("jx.java.projection.snapshot");
    private final Project project;
    // Keys contain immutable snapshots, never PSI. Compiler work runs outside these short
    // cache locks; competing read actions may compute twice but cannot publish stale content.
    private final Map<Path, Batch> moduleCache = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<VariantKey, Variant> variantCache = new LinkedHashMap<>(8, 0.75f, true);
    private record Batch(List<SourceFile> sources, List<GeneratedSource> generated, long weight) {}
    private record VariantKey(Path path, List<SourceFile> sources) {}
    private record Variant(Optional<GeneratedSource> generated, long weight) {}
    public JxJavaProjection(Project project) { this.project = project; }
    public static JxJavaProjection getInstance(Project project) { return project.getService(JxJavaProjection.class); }

    record Snapshot(Map<Path, GeneratedSource> generated, Map<Path, VirtualFile> files,
                    Map<Path, List<SourceFile>> batches, Map<Path, Path> roots) {
        Snapshot { generated = Map.copyOf(generated); files = Map.copyOf(files); batches = Map.copyOf(batches); roots = Map.copyOf(roots); }
    }
    Snapshot snapshot() {
        return CachedValuesManager.getManager(project).getCachedValue(project, SNAPSHOT,
                () -> CachedValueProvider.Result.create(readSnapshot(),
                        PsiModificationTracker.getInstance(project).forLanguage(JxLanguage.INSTANCE),
                        // Closed-file VFS writes need not produce a language PSI event.
                        // Content-keyed batches keep unrelated VFS events from compiling JX.
                        VirtualFileManager.getInstance(),
                        ProjectRootModificationTracker.getInstance(project), DumbService.getInstance(project).getModificationTracker()), false);
    }
    Optional<GeneratedSource> forHost(JxJavaInjectionHost host) {
        String text = host.getText();
        if (text.length() > MAX_SOURCE_CHARS) return Optional.empty();
        Path path = path(host.getContainingFile());
        Snapshot state = snapshot();
        GeneratedSource existing = state.generated().get(path);
        if (existing != null && existing.source().content().equals(text)) return Optional.of(existing);
        // Completion analyzes a temporary copy containing the IDE's dummy identifier. Preserve
        // that exact snapshot, while supplying sibling component declarations for prop typing.
        Path root = state.roots().get(path);
        var sources = new ArrayList<>(root == null ? List.<SourceFile>of() : state.batches().getOrDefault(root, List.of()));
        sources.removeIf(source -> source.path().equals(path));
        sources.add(new SourceFile(path, text));
        var key = new VariantKey(path, ordered(sources));
        synchronized (variantCache) {
            Variant cached = variantCache.get(key);
            if (cached != null) return cached.generated();
        }
        var generated = EditorProjection.project(key.sources()).stream().filter(source -> source.source().path().equals(path)).findFirst();
        long weight = sourceWeight(key.sources()) + generated.map(JxJavaProjection::generatedWeight).orElse(0L);
        if (weight <= MAX_TRANSIENT_CHARS) synchronized (variantCache) {
            Variant existingVariant = variantCache.get(key);
            if (existingVariant != null) return existingVariant.generated();
            variantCache.put(key, new Variant(generated, weight));
            long retained = variantCache.values().stream().mapToLong(Variant::weight).sum();
            var entries = variantCache.entrySet().iterator();
            while (entries.hasNext() && (variantCache.size() > 8 || retained > MAX_TRANSIENT_CHARS)) {
                retained -= entries.next().getValue().weight(); entries.remove();
            }
        }
        return generated;
    }
    static Path path(PsiFile file) {
        VirtualFile virtual = file.getOriginalFile().getVirtualFile();
        if (virtual == null) virtual = file.getVirtualFile();
        return Path.of(virtual == null ? file.getName() : virtual.getPath()).normalize();
    }
    static JxJavaInjectionHost host(PsiFile file) { return PsiTreeUtil.getChildOfType(file, JxJavaInjectionHost.class); }
    static PsiJavaFile javaFile(PsiFile file) {
        JxJavaInjectionHost host = host(file);
        if (host == null) return null;
        var injected = InjectedLanguageManager.getInstance(file.getProject()).getInjectedPsiFiles(host);
        if (injected == null) return null;
        return injected.stream().map(pair -> pair.first).filter(PsiJavaFile.class::isInstance)
                .map(PsiJavaFile.class::cast).findFirst().orElse(null);
    }
    static boolean inScope(Project project, GlobalSearchScope scope, VirtualFile file) {
        if (scope.contains(file)) return true;
        // Maven's generated Java root does not register src/{main,test}/jx as a Java root.
        // Include that authored source only when the requesting scope admits its module and
        // source kind; never widen visibility to unrelated modules or excluded content.
        var index = ProjectFileIndex.getInstance(project);
        var module = index.getModuleForFile(file);
        if (module == null || index.isExcluded(file)) return false;
        for (var directory = file.getParent(); directory != null; directory = directory.getParent()) {
            var kind = directory.getParent();
            if (directory.getName().equals("jx") && kind != null && kind.getParent() != null
                    && kind.getParent().getName().equals("src")
                    && (kind.getName().equals("main") || kind.getName().equals("test")))
                return scope.isSearchInModuleContent(module, kind.getName().equals("test"));
        }
        return false;
    }
    private Snapshot readSnapshot() {
        var files = new LinkedHashMap<Path, VirtualFile>();
        var roots = new LinkedHashMap<Path, Path>();
        var batches = new LinkedHashMap<Path, List<SourceFile>>();
        if (!DumbService.isDumb(project)) {
            long total = 0;
            for (VirtualFile virtual : FileTypeIndex.getFiles(JxFileType.INSTANCE, GlobalSearchScope.projectScope(project))) {
                ProgressManager.checkCanceled();
                if (files.size() >= MAX_FILES || total > MAX_TOTAL_CHARS) {
                    LOG.warn("JX native Java project analysis exceeds its source budget; remaining files use local analysis"); break;
                }
                PsiFile file = PsiManager.getInstance(project).findFile(virtual);
                if (file == null || file.getTextLength() > MAX_SOURCE_CHARS) continue;
                Path path = path(file); Path root = moduleRoot(virtual);
                // PSI owns leading/trailing whitespace outside this host. Native injection
                // uses the host snapshot, so indexing must use that same coordinate space.
                JxJavaInjectionHost host = host(file);
                if (host == null) continue;
                String text = host.getText();
                if (total + text.length() > MAX_TOTAL_CHARS) {
                    LOG.warn("JX native Java project analysis exceeds its source budget; remaining files use local analysis"); break;
                }
                total += text.length();
                files.put(path, virtual); roots.put(path, root);
                batches.computeIfAbsent(root, ignored -> new ArrayList<>()).add(new SourceFile(path, text));
            }
        }
        var generated = new LinkedHashMap<Path, GeneratedSource>();
        batches.replaceAll((root, batch) -> ordered(batch));
        for (var entry : batches.entrySet()) {
            ProgressManager.checkCanceled();
            for (GeneratedSource source : projectBatch(entry.getKey(), entry.getValue())) generated.put(source.source().path(), source);
        }
        synchronized (moduleCache) { moduleCache.keySet().retainAll(batches.keySet()); }
        return new Snapshot(generated, files, batches, roots);
    }
    private List<GeneratedSource> projectBatch(Path root, List<SourceFile> sources) {
        synchronized (moduleCache) {
            Batch cached = moduleCache.get(root);
            if (cached != null && cached.sources().equals(sources)) return cached.generated();
        }
        List<GeneratedSource> generated = EditorProjection.project(sources);
        long weight = sourceWeight(sources) + generated.stream().mapToLong(JxJavaProjection::generatedWeight).sum();
        synchronized (moduleCache) {
            Batch cached = moduleCache.get(root);
            if (cached != null && cached.sources().equals(sources)) return cached.generated();
            moduleCache.remove(root);
            if (weight <= MAX_RETAINED_CHARS) {
                moduleCache.put(root, new Batch(sources, generated, weight));
                long retained = moduleCache.values().stream().mapToLong(Batch::weight).sum();
                var entries = moduleCache.entrySet().iterator();
                while (entries.hasNext() && (moduleCache.size() > 64 || retained > MAX_RETAINED_CHARS)) {
                    retained -= entries.next().getValue().weight(); entries.remove();
                }
            }
        }
        return generated;
    }
    private static List<SourceFile> ordered(List<SourceFile> sources) {
        return sources.stream().sorted(Comparator.comparing(source -> source.path().toString())).toList();
    }
    private static long sourceWeight(List<SourceFile> sources) {
        return sources.stream().mapToLong(source -> source.content().length()).sum();
    }
    private static long generatedWeight(GeneratedSource source) {
        // Include a conservative allowance for mapping records in addition to text.
        return source.javaSource().length() + 32L * source.sourceMap().segments().size();
    }
    private Path moduleRoot(VirtualFile file) {
        for (VirtualFile directory = file.getParent(); directory != null; directory = directory.getParent()) {
            if (directory.findChild("pom.xml") != null) return Path.of(directory.getPath());
        }
        return Path.of(project.getBasePath() == null ? "." : project.getBasePath());
    }
}
