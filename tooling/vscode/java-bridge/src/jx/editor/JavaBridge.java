package jx.editor;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand;
import org.eclipse.jdt.ls.core.internal.handlers.*;
import org.eclipse.lsp4j.*;

/** Native JDT working-copy adapter. No generated buffer is committed or exposed as a dirty editor. */
public final class JavaBridge implements IDelegateCommandHandler {
    private record FileInput(String sourceUri, String relativeJavaPath, String javaText) {}
    private record SyncInput(String session, String projectUri, List<FileInput> files) {}
    private record FileResult(String sourceUri, String javaUri) {}
    private record DiagnosticResult(String uri, List<Diagnostic> diagnostics) {}
    private record Owned(String sourceUri, ICompilationUnit unit) {}
    private record SourceRoot(IPackageFragmentRoot root, boolean test) {}
    private static final Map<String, Map<String, Owned>> SESSIONS = new LinkedHashMap<>();

    @Override public Object executeCommand(String command, List<Object> arguments, IProgressMonitor monitor) throws Exception {
        if (arguments == null || arguments.size() != 1) throw new IllegalArgumentException("One typed argument is required");
        synchronized (SESSIONS) {
            if (monitor.isCanceled()) throw new org.eclipse.core.runtime.OperationCanceledException();
            Object input = arguments.getFirst();
            var preferences = JavaLanguageServerPlugin.getPreferencesManager();
            return switch (command) {
                case "jx.java.info" -> Map.of("protocolVersion", 1, "javaVersion", Runtime.version().feature());
                case "jx.java.sync" -> sync(JSONUtility.toModel(input, SyncInput.class), monitor);
                case "jx.java.close" -> { release(JSONUtility.toModel(input, String.class)); yield true; }
                case "jx.java.completion" -> {
                    var result = new CompletionHandler(preferences).completion(JSONUtility.toLsp4jModel(input, CompletionParams.class), monitor);
                    yield result.isRight() ? result.getRight() : new CompletionList(result.getLeft());
                }
                case "jx.java.resolveCompletion" -> new CompletionResolveHandler(preferences).resolve(JSONUtility.toLsp4jModel(input, CompletionItem.class), monitor);
                case "jx.java.hover" -> new HoverHandler(preferences).hover(JSONUtility.toLsp4jModel(input, TextDocumentPositionParams.class), monitor);
                case "jx.java.definition" -> new NavigateToDefinitionHandler(preferences).definition(JSONUtility.toLsp4jModel(input, TextDocumentPositionParams.class), monitor);
                case "jx.java.signatureHelp" -> new SignatureHelpHandler(preferences).signatureHelp(JSONUtility.toLsp4jModel(input, SignatureHelpParams.class), monitor);
                case "jx.java.references" -> new ReferencesHandler(preferences).findReferences(JSONUtility.toLsp4jModel(input, ReferenceParams.class), monitor);
                case "jx.java.prepareRename" -> new PrepareRenameHandler(preferences).prepareRename(JSONUtility.toLsp4jModel(input, TextDocumentPositionParams.class), monitor);
                case "jx.java.rename" -> new RenameHandler(preferences).rename(JSONUtility.toLsp4jModel(input, RenameParams.class), monitor);
                case "jx.java.diagnostics" -> diagnostics(JSONUtility.toModel(input, String.class), monitor);
                default -> throw new IllegalArgumentException("Unknown JX Java command: " + command);
            };
        }
    }

    private static List<FileResult> sync(SyncInput input, IProgressMonitor monitor) throws Exception {
        Objects.requireNonNull(input.session(), "session");
        if (input.session().length() > 256 || input.files() == null || input.files().size() > 256)
            throw new IllegalArgumentException("Invalid JX projection session or file count");
        if (!SESSIONS.containsKey(input.session()) && SESSIONS.size() >= 32)
            throw new IllegalStateException("Too many active JX projection sessions");
        var project = ProjectCommand.getJavaProjectFromUri(input.projectUri());
        if (project == null || !project.exists()) throw new IllegalStateException("Import this Maven project with Java before requesting JX Java services");
        List<SourceRoot> roots = new ArrayList<>();
        for (IClasspathEntry entry : project.getRawClasspath()) {
            if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
                var resource = ResourcesPlugin.getWorkspace().getRoot().getFolder(entry.getPath());
                roots.add(new SourceRoot(project.getPackageFragmentRoot(resource), entry.isTest()));
            }
        }
        roots.sort(java.util.Comparator.comparingInt(value -> {
            String name = value.root().getPath().toPortableString();
            return name.endsWith(value.test() ? "/generated-test-sources/jx" : "/generated-sources/jx") ? 0
                : name.endsWith(value.test() ? "/src/test/java" : "/src/main/java") ? 1 : 2;
        }));
        if (roots.isEmpty()) throw new IllegalStateException("The Java project has no source roots");
        var owned = SESSIONS.computeIfAbsent(input.session(), ignored -> new LinkedHashMap<>());
        var retained = new java.util.HashSet<String>();
        List<FileResult> result = new ArrayList<>();
        int size = 0;
        for (FileInput file : input.files()) {
            if (monitor.isCanceled()) throw new org.eclipse.core.runtime.OperationCanceledException();
            Path source = Path.of(URI.create(file.sourceUri())).toAbsolutePath().normalize();
            Path projectPath = Path.of(project.getProject().getLocationURI()).toAbsolutePath().normalize();
            if (!source.startsWith(projectPath) || !source.toString().endsWith(".jx"))
                throw new IllegalArgumentException("Projection source must be a .jx file in the imported project");
            if (file.javaText() == null || file.javaText().length() > 1_048_576 || (size += file.javaText().length()) > 16_777_216)
                throw new IllegalArgumentException("Projection exceeds the supported size");
            Path relative = Path.of(file.relativeJavaPath()).normalize();
            if (relative.isAbsolute() || relative.startsWith("..") || !relative.toString().endsWith(".java"))
                throw new IllegalArgumentException("Invalid projected Java path");
            retained.add(file.sourceUri());
            boolean test = source.startsWith(projectPath.resolve("src/test"));
            IPackageFragmentRoot root = roots.stream().filter(value -> value.test() == test).map(SourceRoot::root).findFirst()
                .orElseThrow(() -> new IllegalStateException("The imported Maven project has no " + (test ? "test" : "main") + " Java source root"));
            String packageName = relative.getParent() == null ? "" : relative.getParent().toString().replace('/', '.').replace('\\', '.');
            ICompilationUnit unit = root.getPackageFragment(packageName).getCompilationUnit(relative.getFileName().toString());
            Owned item = owned.get(file.sourceUri());
            if (item != null && !item.unit().getPath().equals(unit.getPath())) {
                item.unit().discardWorkingCopy();
                owned.remove(file.sourceUri());
                item = null;
            }
            if (item == null) {
                if (unit.isWorkingCopy()) throw new IllegalStateException("Projected Java file is already edited by another owner: " + unit.getPath());
                if (Files.exists(Path.of(unit.getResource().getLocationURI())) && !ownedOutput(root, relative, source))
                    throw new IllegalStateException("Projection would hide authored or unowned Java source: " + unit.getPath());
                unit.becomeWorkingCopy(monitor);
                item = new Owned(file.sourceUri(), unit);
                owned.put(file.sourceUri(), item);
            }
            if (!file.javaText().equals(item.unit().getBuffer().getContents())) {
                item.unit().getBuffer().setContents(file.javaText());
                item.unit().makeConsistent(monitor);
            }
            String javaUri = item.unit().getResource().getLocationURI().toString();
            if (JDTUtils.resolveCompilationUnit(javaUri) != item.unit())
                throw new IllegalStateException("JDT cannot resolve the owned Java working copy: " + javaUri);
            result.add(new FileResult(file.sourceUri(), javaUri));
        }
        discard(owned, owned.keySet().stream().filter(uri -> !retained.contains(uri)).toList());
        return List.copyOf(result);
    }

    private static List<DiagnosticResult> diagnostics(String session, IProgressMonitor monitor) throws Exception {
        List<DiagnosticResult> result = new ArrayList<>();
        for (Owned owned : SESSIONS.getOrDefault(session, Map.of()).values()) {
            var ast = owned.unit().reconcile(AST.getJLSLatest(), true, null, monitor);
            List<Diagnostic> diagnostics = new ArrayList<>();
            if (ast != null) for (var problem : ast.getProblems()) {
                if (!problem.isError() && !problem.isWarning()) continue;
                int start = Math.max(0, problem.getSourceStart());
                var diagnostic = new Diagnostic(JDTUtils.toRange(owned.unit(), start, Math.max(1, problem.getSourceEnd() - start + 1)),
                    problem.getMessage(), problem.isError() ? DiagnosticSeverity.Error : DiagnosticSeverity.Warning, "Java");
                diagnostic.setCode(Integer.toString(problem.getID()));
                diagnostics.add(diagnostic);
            }
            result.add(new DiagnosticResult(owned.unit().getResource().getLocationURI().toString(), List.copyOf(diagnostics)));
        }
        return List.copyOf(result);
    }

    /** Directory names alone do not confer ownership. Both generator metadata files must agree. */
    private static boolean ownedOutput(IPackageFragmentRoot root, Path relative, Path source) throws Exception {
        Path output = Path.of(root.getResource().getLocationURI()).toAbsolutePath().normalize();
        if (!output.getFileName().toString().equals("jx") || output.getParent() == null) return false;
        String phase = switch (output.getParent().getFileName().toString()) {
            case "generated-sources" -> "main";
            case "generated-test-sources" -> "test";
            default -> null;
        };
        if (phase == null) return false;
        Path metadata = output.getParent().getParent().resolve("jx").resolve(phase);
        Path manifest = metadata.resolve("outputs.tsv");
        String javaPath = relative.toString().replace('\\', '/');
        Path mapping = metadata.resolve(javaPath.substring(0, javaPath.length() - 5) + ".jxmap");
        if (!Files.isRegularFile(manifest) || !Files.isRegularFile(mapping)
                || Files.size(manifest) > 4_194_304 || Files.size(mapping) > 16_777_216) return false;
        List<String> outputs = Files.readAllLines(manifest);
        if (outputs.isEmpty() || !outputs.getFirst().equals("jx-outputs-v1")
                || outputs.stream().skip(1).noneMatch(line -> line.startsWith(javaPath + "\t"))) return false;
        try (var reader = Files.newBufferedReader(mapping)) {
            String uri = reader.readLine(), header = reader.readLine();
            if (uri == null || header == null || !header.startsWith("jx-map-v1\t")) return false;
            try { return Path.of(URI.create(uri)).toAbsolutePath().normalize().equals(source); }
            catch (IllegalArgumentException invalid) { return false; }
        }
    }

    private static void release(String session) throws Exception {
        Map<String, Owned> owned = SESSIONS.get(session);
        if (owned == null) return;
        try { discard(owned, List.copyOf(owned.keySet())); }
        finally { if (owned.isEmpty()) SESSIONS.remove(session); }
    }

    private static void discard(Map<String, Owned> owned, List<String> sources) throws Exception {
        Exception failure = null;
        for (String source : sources) {
            try { owned.get(source).unit().discardWorkingCopy(); owned.remove(source); }
            catch (Exception exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }
}
