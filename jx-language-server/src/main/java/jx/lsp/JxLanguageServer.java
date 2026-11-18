package jx.lsp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import jx.compiler.Compilation;
import jx.compiler.ComponentMetadata;
import jx.compiler.JxCompiler;
import jx.compiler.SourceFile;
import jx.compiler.SourceContext;
import jx.compiler.SourceSpan;
import jx.compiler.Syntax;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/** Compiler-backed UTF-16 language services. Project code and annotation processors are never executed. */
public final class JxLanguageServer implements LanguageServer, TextDocumentService, WorkspaceService, AutoCloseable {
    private record Document(Path path, String text, int version) {}
    private record Analysis(ProjectSources.Snapshot project, Compilation compilation) {}
    private record Reference(ComponentMetadata component, ComponentMetadata.Prop property, SourceSpan span) {}
    private record TagContext(String name, boolean namePosition) {}
    private static final List<String> HTML = List.of("a", "article", "aside", "button", "div", "form", "h1", "h2", "header", "input", "label", "li", "main", "nav", "ol", "option", "p", "section", "select", "span", "svg", "path", "textarea", "ul");
    private static final List<String> ATTRIBUTES = List.of("id", "className", "title", "role", "aria-label", "data-testid", "onClick", "onChange", "onSubmit", "value", "checked", "disabled", "type", "name", "htmlFor", "key", "ref");
    private final Object lock = new Object();
    private final Map<String, Document> documents = new LinkedHashMap<>();
    private final Map<String, Integer> latestVersions = new LinkedHashMap<>();
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("jx-analysis").daemon().factory());
    private final Runnable exitAction;
    private List<Path> roots = List.of();
    private List<Path> classpath = List.of();
    private volatile LanguageClient client;
    private Future<?> diagnosticsTask;
    private long epoch;
    private volatile boolean closed;

    public JxLanguageServer() { this(() -> {}); }
    public JxLanguageServer(Runnable exitAction) { this.exitAction = java.util.Objects.requireNonNull(exitAction); }
    public void connect(LanguageClient client) { this.client = java.util.Objects.requireNonNull(client); }

    @Override public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        List<Path> discoveredRoots = new ArrayList<>();
        if (params.getWorkspaceFolders() != null) {
            for (var folder : params.getWorkspaceFolders()) discoveredRoots.add(filePath(folder.getUri()));
        }
        if (discoveredRoots.isEmpty() && params.getRootUri() != null) discoveredRoots.add(filePath(params.getRootUri()));
        List<Path> configured = new ArrayList<>();
        if (params.getInitializationOptions() instanceof JsonObject options && options.has("classpath")) {
            if (!(options.get("classpath") instanceof JsonArray array)) throw new IllegalArgumentException("initializationOptions.classpath must be an array");
            for (var value : array) {
                Path entry = Path.of(value.getAsString()).toAbsolutePath().normalize();
                if (!Files.exists(entry)) throw new IllegalArgumentException("Classpath entry does not exist: " + entry);
                configured.add(entry);
            }
        }
        synchronized (lock) { roots = List.copyOf(discoveredRoots); classpath = List.copyOf(configured); }
        var capabilities = new ServerCapabilities();
        capabilities.setPositionEncoding("utf-16");
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        capabilities.setCompletionProvider(new CompletionOptions(false, List.of("<", " ", ".")));
        capabilities.setHoverProvider(true);
        capabilities.setDefinitionProvider(true);
        capabilities.setDocumentSymbolProvider(true);
        return CompletableFuture.completedFuture(new InitializeResult(capabilities, new ServerInfo("JX", "0.1.0-SNAPSHOT")));
    }

    @Override public CompletableFuture<Object> shutdown() { close(); return CompletableFuture.completedFuture(null); }
    @Override public void exit() { close(); exitAction.run(); }
    @Override public TextDocumentService getTextDocumentService() { return this; }
    @Override public WorkspaceService getWorkspaceService() { return this; }
    @Override public void didChangeConfiguration(DidChangeConfigurationParams params) { /* restart applies classpath changes */ }
    @Override public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) { scheduleDiagnostics(); }

    @Override public void didOpen(DidOpenTextDocumentParams params) {
        var item = params.getTextDocument();
        putDocument(item.getUri(), item.getText(), item.getVersion());
    }

    @Override public void didChange(DidChangeTextDocumentParams params) {
        if (params.getContentChanges().isEmpty()) return;
        if (params.getContentChanges().stream().anyMatch(change -> change.getRange() != null)) {
            log(MessageType.Error, "JX negotiated full document synchronization; incremental change rejected");
            return;
        }
        putDocument(params.getTextDocument().getUri(), params.getContentChanges().getLast().getText(), params.getTextDocument().getVersion());
    }

    private void putDocument(String uri, String text, int version) {
        boolean oversized = text.length() > ProjectSources.MAX_DOCUMENT_LENGTH;
        synchronized (lock) {
            Integer previous = latestVersions.get(uri);
            if (closed || previous != null && version <= previous) return;
            Path path = filePath(uri);
            latestVersions.put(uri, version);
            if (oversized) {
                documents.remove(uri); epoch++;
                publish(uri, version, List.of(new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)), "Document exceeds the 1 MiB analysis limit", DiagnosticSeverity.Error, "jx")));
            } else documents.put(uri, new Document(path, text, version));
        }
        if (oversized) { log(MessageType.Error, "JX document exceeds the 1 MiB analysis limit: " + uri); return; }
        scheduleDiagnostics();
    }

    @Override public void didClose(DidCloseTextDocumentParams params) {
        synchronized (lock) {
            documents.remove(params.getTextDocument().getUri());
            latestVersions.remove(params.getTextDocument().getUri());
            epoch++;
        }
        publish(params.getTextDocument().getUri(), null, List.of());
        scheduleDiagnostics();
    }

    @Override public void didSave(DidSaveTextDocumentParams params) { scheduleDiagnostics(); }

    private void scheduleDiagnostics() {
        synchronized (lock) {
            if (closed) return;
            long revision = ++epoch;
            if (diagnosticsTask != null) diagnosticsTask.cancel(true);
            var captured = Map.copyOf(documents);
            diagnosticsTask = analysisExecutor.submit(() -> {
                var projects = new LinkedHashMap<Path, Analysis>();
                for (var entry : captured.entrySet()) {
                    if (Thread.currentThread().isInterrupted()) return;
                    try {
                        Path project = ProjectSources.projectRoot(entry.getValue().path(), roots);
                        Analysis analysis = projects.get(project);
                        if (analysis == null) {
                            analysis = analyze(project, captured, true);
                            projects.put(project, analysis);
                        }
                        SourceFile source = new SourceFile(entry.getValue().path(), entry.getValue().text());
                        var result = new ArrayList<Diagnostic>();
                        for (var diagnostic : analysis.compilation().diagnostics()) {
                            if (!diagnostic.source().toAbsolutePath().normalize().equals(source.path())) continue;
                            var mapped = new Diagnostic(Positions.range(source, diagnostic.span()), diagnostic.message(),
                                    diagnostic.severity() == jx.compiler.Diagnostic.Severity.ERROR ? DiagnosticSeverity.Error : DiagnosticSeverity.Warning, "jx");
                            mapped.setCode(diagnostic.code());
                            result.add(mapped);
                        }
                        synchronized (lock) {
                            if (closed || revision != epoch) return;
                            publish(entry.getKey(), entry.getValue().version(), result);
                        }
                    } catch (IOException | RuntimeException failure) {
                        if (Thread.currentThread().isInterrupted()) return;
                        synchronized (lock) {
                            if (closed || revision != epoch) return;
                            publish(entry.getKey(), entry.getValue().version(), List.of(new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)),
                                    "Analysis failed: " + failure.getMessage(), DiagnosticSeverity.Error, "jx")));
                        }
                        log(MessageType.Error, "JX analysis failed: " + failure);
                    }
                }
            });
        }
    }

    private Analysis analyze(Path project, Map<String, Document> captured, boolean semantic) throws IOException {
        Map<Path, String> overlays = new LinkedHashMap<>();
        captured.values().forEach(document -> overlays.put(document.path(), document.text()));
        var snapshot = ProjectSources.discover(project, overlays, classpath, roots);
        var compiler = new JxCompiler();
        Compilation compilation = compiler.compile(snapshot.sources());
        if (semantic && compilation.successful() && !Thread.currentThread().isInterrupted()) {
            Path classes = Files.createTempDirectory("jx-lsp-check-");
            try {
                var diagnostics = compiler.check(compilation, snapshot.javaSources(), snapshot.classpath(), classes);
                compilation = new Compilation(compilation.generatedSources(), compilation.components(), diagnostics);
            } finally {
                try (var paths = Files.walk(classes)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
        return new Analysis(snapshot, compilation);
    }

    private <T> CompletableFuture<T> request(String uri, Function<Analysis, T> operation) {
        final Map<String, Document> captured;
        final long revision;
        synchronized (lock) {
            if (latestVersions.containsKey(uri) && !documents.containsKey(uri))
                return CompletableFuture.failedFuture(new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams,
                        "Document exceeds the analysis limit: " + uri, null)));
            captured = Map.copyOf(documents); revision = epoch;
        }
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Language server is shut down"));
        var result = new CompletableFuture<T>();
        Future<?> task = analysisExecutor.submit(() -> {
            if (result.isCancelled()) return;
            try {
                Path project = ProjectSources.projectRoot(filePath(uri), roots);
                Analysis analysis = analyze(project, captured, false);
                T value = operation.apply(analysis);
                synchronized (lock) {
                    if (revision != epoch || closed) { result.cancel(false); return; }
                    if (!result.isCancelled()) result.complete(value);
                }
            } catch (IOException | RuntimeException failure) { result.completeExceptionally(failure); }
        });
        result.whenComplete((ignored, failure) -> { if (result.isCancelled()) task.cancel(true); });
        return result;
    }

    @Override public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        String uri = params.getTextDocument().getUri();
        return request(uri, analysis -> {
            SourceFile source = source(analysis, uri);
            int offset = Positions.offset(source.content(), params.getPosition());
            TagContext tag = tagContext(source.content(), offset);
            List<CompletionItem> items = new ArrayList<>();
            if (tag != null && !tag.namePosition()) {
                ComponentMetadata component = component(analysis, new JxCompiler().context(source), tag.name());
                if (component != null) {
                    for (var prop : component.props()) items.add(completion(prop.name(), CompletionItemKind.Property, prop.javaType(), prop.name() + "={}"
                    ));
                    items.add(completion("key", CompletionItemKind.Property, "React component identity", "key={}"));
                } else for (String attribute : ATTRIBUTES) items.add(completion(attribute, CompletionItemKind.Property, "React DOM attribute", attribute + "="));
            } else if (tag != null) {
                for (String name : HTML) items.add(completion(name, CompletionItemKind.Keyword, "HTML/SVG", name));
                for (var component : analysis.compilation().components()) items.add(completion(component.simpleName(), CompletionItemKind.Class, component.qualifiedName(), component.simpleName()));
            }
            return Either.forLeft(items);
        });
    }

    private static CompletionItem completion(String name, CompletionItemKind kind, String detail, String insert) {
        var item = new CompletionItem(name); item.setKind(kind); item.setDetail(detail); item.setInsertText(insert); return item;
    }

    @Override public CompletableFuture<Hover> hover(HoverParams params) {
        String uri = params.getTextDocument().getUri();
        return request(uri, analysis -> {
            SourceFile source = source(analysis, uri);
            Reference reference = reference(analysis, source, Positions.offset(source.content(), params.getPosition()));
            if (reference == null) return null;
            String text = reference.property() == null ? "Java component " + reference.component().qualifiedName() + "\nProps: "
                    + reference.component().props().stream().map(p -> p.javaType() + " " + p.name()).collect(java.util.stream.Collectors.joining(", "))
                    : reference.property().javaType() + " " + reference.property().name();
            return new Hover(new MarkupContent("plaintext", text), Positions.range(source, reference.span()));
        });
    }

    @Override public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        String uri = params.getTextDocument().getUri();
        return request(uri, analysis -> {
            SourceFile source = source(analysis, uri);
            Reference reference = reference(analysis, source, Positions.offset(source.content(), params.getPosition()));
            if (reference == null) return Either.forLeft(List.of());
            SourceFile destination = source(analysis, reference.component().source().toUri().toString());
            SourceSpan span = reference.property() == null ? reference.component().declaration() : reference.property().declaration();
            return Either.forLeft(List.of(new Location(destination.path().toUri().toString(), Positions.range(destination, span))));
        });
    }

    @Override public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        String uri = params.getTextDocument().getUri();
        return request(uri, analysis -> {
            SourceFile source = source(analysis, uri);
            List<Either<SymbolInformation, DocumentSymbol>> symbols = new ArrayList<>();
            for (var component : analysis.compilation().components()) {
                if (!component.source().equals(source.path())) continue;
                Range selection = Positions.range(source, component.declaration());
                Range whole = Positions.range(source, new SourceSpan(0, source.content().length()));
                var symbol = new DocumentSymbol(component.simpleName(), SymbolKind.Class, whole, selection);
                symbol.setDetail("Java functional component");
                symbol.setChildren(component.props().stream().map(prop -> {
                    Range range = Positions.range(source, prop.declaration());
                    var child = new DocumentSymbol(prop.name(), SymbolKind.Property, range, range);
                    child.setDetail(prop.javaType()); return child;
                }).toList());
                symbols.add(Either.forRight(symbol));
            }
            return symbols;
        });
    }

    private static Reference reference(Analysis analysis, SourceFile source, int offset) {
        var parsed = new JxCompiler().parse(source);
        Reference found = referenceNodes(analysis, new JxCompiler().context(source), parsed.nodes(), offset);
        if (found != null) return found;
        for (var component : analysis.compilation().components()) {
            if (!component.source().equals(source.path())) continue;
            for (var prop : component.props()) if (prop.declaration().contains(offset)) return new Reference(component, prop, prop.declaration());
        }
        return null;
    }

    private static Reference referenceNodes(Analysis analysis, SourceContext context, List<Syntax.Node> nodes, int offset) {
        for (var node : nodes) {
            if (!node.span().contains(offset)) continue;
            if (node instanceof Syntax.Element element) {
                ComponentMetadata component = component(analysis, context, element.name());
                if (component != null) {
                    if (element.nameSpan().contains(offset)) return new Reference(component, null, element.nameSpan());
                    for (var attribute : element.attributes()) {
                        if (attribute.nameSpan().contains(offset)) {
                            for (var prop : component.props()) if (prop.name().equals(attribute.name())) return new Reference(component, prop, attribute.nameSpan());
                        }
                    }
                }
                Reference child = referenceNodes(analysis, context, element.children(), offset);
                if (child != null) return child;
                for (var attribute : element.attributes()) if (attribute.expression() != null) {
                    child = referenceNodes(analysis, context, attribute.expression().expression(), offset);
                    if (child != null) return child;
                }
            } else if (node instanceof Syntax.Interpolation expression) {
                Reference child = referenceNodes(analysis, context, expression.expression(), offset);
                if (child != null) return child;
            }
        }
        return null;
    }

    private static ComponentMetadata component(Analysis analysis, SourceContext context, String name) {
        return new JxCompiler().resolveComponent(analysis.compilation(), context, name).orElse(null);
    }

    /** Recovery for an unfinished opening tag; semantic information still comes from the compiler. */
    private static TagContext tagContext(String text, int offset) {
        int start = text.lastIndexOf('<', Math.max(0, offset - 1));
        if (start < 0 || text.substring(start, offset).indexOf('>') >= 0) return null;
        int at = start + 1;
        if (at < offset && text.charAt(at) == '/') at++;
        int nameStart = at;
        while (at < offset && (Character.isJavaIdentifierPart(text.charAt(at)) || text.charAt(at) == '.')) at++;
        if (at == offset) return new TagContext(text.substring(nameStart, at), true);
        if (at == nameStart || !Character.isWhitespace(text.charAt(at))) return null;
        return new TagContext(text.substring(nameStart, at), false);
    }

    private static SourceFile source(Analysis analysis, String uri) {
        Path path = filePath(uri);
        return analysis.project().sources().stream().filter(source -> source.path().equals(path)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Document is not in the project: " + uri));
    }

    private static Path filePath(String uri) {
        URI parsed = URI.create(uri);
        if (!"file".equals(parsed.getScheme())) throw new IllegalArgumentException("JX supports file: workspace documents");
        return Path.of(parsed).toAbsolutePath().normalize();
    }

    private void publish(String uri, Integer version, List<Diagnostic> diagnostics) {
        LanguageClient current = client;
        if (current != null) current.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics, version));
    }

    private void log(MessageType type, String message) {
        LanguageClient current = client;
        if (current != null) current.logMessage(new MessageParams(type, message));
        else System.err.println(message);
    }

    @Override public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true; epoch++;
            if (diagnosticsTask != null) diagnosticsTask.cancel(true);
            documents.clear();
            latestVersions.clear();
        }
        analysisExecutor.shutdownNow();
    }
}
