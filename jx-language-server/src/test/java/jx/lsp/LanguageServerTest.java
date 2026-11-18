package jx.lsp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LanguageServerTest {
    @TempDir Path directory;

    @Test void utf16PositionsRoundTripCrLfAndSurrogatePairs() {
        String text = "first\r\n😀name\r\nend";
        for (int i : List.of(0, 5, 7, 8, 9, 13, 15, 18)) {
            assertEquals(i, Positions.offset(text, Positions.position(text, i)));
        }
        assertEquals(9, Positions.offset(text, new Position(1, 2)));
        assertEquals(13, Positions.offset(text, new Position(1, 900)));
    }

    @Test void realProtocolSharesTypesNavigationAndOriginalSourceDiagnostics() throws Exception {
        Path counter = directory.resolve("Counter.jx");
        Path app = directory.resolve("App.jx");
        String counterText = """
                import jx.Component;
                import jx.View;
                @Component public final class Counter {
                    public record Props(int initial, String label) {}
                    public static View render(Props props) { return <button>{props.label()}: {props.initial()}</button>; }
                }
                """;
        String valid = "import jx.*;\r\n// 😀 source location\r\n@Component public final class App {\r\n"
                + " public static View render() { return <Counter initial={1} label=\"Count\" />; }\r\n}\r\n";
        Files.writeString(counter, counterText);
        Files.writeString(app, valid);
        try (var session = new Session()) {
            InitializeParams init = new InitializeParams();
            init.setWorkspaceFolders(List.of(new WorkspaceFolder(directory.toUri().toString(), "fixture")));
            var capabilities = session.remote.initialize(init).get(5, TimeUnit.SECONDS).getCapabilities();
            assertEquals("utf-16", capabilities.getPositionEncoding());
            assertNull(capabilities.getRenameProvider());
            session.remote.initialized(new InitializedParams());
            String uri = app.toUri().toString();
            session.remote.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "jx", 1, valid)));
            PublishDiagnosticsParams initial = session.client.await(d -> uri.equals(d.getUri()) && Integer.valueOf(1).equals(d.getVersion()));
            assertEquals(List.of(), initial.getDiagnostics(), initial.getDiagnostics().toString());

            Position component = Positions.position(valid, valid.indexOf("<Counter") + 2);
            var definition = session.remote.getTextDocumentService().definition(new DefinitionParams(new TextDocumentIdentifier(uri), component)).get(5, TimeUnit.SECONDS);
            assertTrue(definition.isLeft());
            assertEquals(counter.toUri().toString(), definition.getLeft().getFirst().getUri());
            var hover = session.remote.getTextDocumentService().hover(new HoverParams(new TextDocumentIdentifier(uri), Positions.position(valid, valid.indexOf("initial=")))).get(5, TimeUnit.SECONDS);
            assertTrue(hover.getContents().getRight().getValue().contains("int initial"));
            var symbols = session.remote.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(uri))).get(5, TimeUnit.SECONDS);
            assertEquals("App", symbols.getFirst().getRight().getName());

            String invalid = valid.replace("initial={1}", "initial={\"wrong\"}");
            session.change(uri, 2, invalid);
            var diagnostics = session.client.await(d -> uri.equals(d.getUri()) && Integer.valueOf(2).equals(d.getVersion()));
            assertFalse(diagnostics.getDiagnostics().isEmpty());
            assertTrue(diagnostics.getDiagnostics().stream().anyMatch(d -> d.getRange().getStart().getLine() == 3), diagnostics.toString());

            // Delayed old notifications must not replace the current editor snapshot.
            session.change(uri, 1, valid);
            var afterOldChange = session.remote.getTextDocumentService().hover(new HoverParams(new TextDocumentIdentifier(uri), Positions.position(invalid, invalid.indexOf("initial=")))).get(5, TimeUnit.SECONDS);
            assertTrue(afterOldChange.getContents().getRight().getValue().contains("int initial"));

            String incomplete = valid.substring(0, valid.indexOf("<Counter")) + "<Counter ";
            session.change(uri, 3, incomplete);
            var completions = session.remote.getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(uri), Positions.position(incomplete, incomplete.length()))).get(5, TimeUnit.SECONDS);
            assertTrue(completions.getLeft().stream().anyMatch(item -> item.getLabel().equals("initial") && item.getDetail().equals("int")));
            var cancelled = session.remote.getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(uri), Positions.position(incomplete, incomplete.length())));
            cancelled.cancel(true);
            assertTrue(cancelled.isCancelled());
            session.remote.getTextDocumentService().didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
            assertEquals(List.of(), session.client.await(d -> uri.equals(d.getUri()) && d.getVersion() == null).getDiagnostics());
            session.remote.shutdown().get(5, TimeUnit.SECONDS);
            session.remote.exit();
        }
    }

    @Test void discoveryExcludesNestedMavenModules() throws Exception {
        Files.writeString(directory.resolve("pom.xml"), "<project/>");
        Files.writeString(directory.resolve("Own.jx"), "public class Own {}");
        Path nested = directory.resolve("other-module");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("pom.xml"), "<project/>");
        Files.writeString(nested.resolve("Broken.jx"), "unrelated invalid source");
        var snapshot = ProjectSources.discover(directory, java.util.Map.of(), List.of(), List.of(directory));
        assertEquals(List.of("Own.jx"), snapshot.sources().stream().map(source -> source.path().getFileName().toString()).toList());
    }

    @Test void nativeJavaProjectionProtocolUsesUnsavedVersionsAndExactEditableMappings() throws Exception {
        Path app = directory.resolve("App.jx");
        Path sibling = directory.resolve("Other.jx");
        String disk = "import jx.*; @Component public class App { public static View render() { return <div>Disk</div>; } }";
        String text = disk.replace("<div>Disk</div>", "<button onClick={event -> System.out.println(\"😀 unsaved\")}>Unsaved</button>");
        Files.writeString(app, disk);
        Files.writeString(sibling, "import jx.*; @Component public class Other { public static View render() { return <span/>; } }");
        try (var session = new Session()) {
            var init = new InitializeParams();
            init.setWorkspaceFolders(List.of(new WorkspaceFolder(directory.toUri().toString(), "fixture")));
            session.remote.initialize(init).get(5, TimeUnit.SECONDS);
            String uri = app.toUri().toString();
            session.remote.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "jx", 7, text)));
            session.client.await(d -> uri.equals(d.getUri()) && Integer.valueOf(7).equals(d.getVersion()));
            EditorProjectionResult result = session.remote.editorProjection(new EditorProjectionParams(uri, 7)).get(5, TimeUnit.SECONDS);
            assertEquals(1, result.protocolVersion());
            assertEquals(directory.toUri().toString(), result.projectUri());
            assertEquals(2, result.files().size());
            assertTrue(result.unavailable().isEmpty());
            var projection = result.files().stream().filter(file -> file.sourceUri().equals(uri)).findFirst().orElseThrow();
            assertEquals(7, projection.version());
            assertEquals(text, projection.sourceText());
            assertEquals("App.java", projection.relativeJavaPath());
            assertEquals(List.of("App"), projection.topLevelTypes());
            assertTrue(projection.javaText().contains("😀 unsaved"));
            var map = new jx.compiler.SourceMap(projection.segments(), projection.sourceLength());
            String expression = "event -> System.out.println(\"😀 unsaved\")";
            int authored = text.indexOf(expression);
            int generated = map.generatedOffset(authored).orElseThrow();
            assertEquals(expression, projection.javaText().substring(generated, generated + expression.length()));
            assertEquals(new jx.compiler.SourceSpan(authored, authored + expression.length()),
                    map.editableSourceSpan(new jx.compiler.SourceSpan(generated, generated + expression.length())).orElseThrow());
            assertTrue(map.generatedOffset(text.indexOf("<button") + 1).isEmpty());
            assertNull(result.files().stream().filter(file -> file.sourceUri().equals(sibling.toUri().toString())).findFirst().orElseThrow().version());
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> session.remote.editorProjection(new EditorProjectionParams(uri, 6)).get(5, TimeUnit.SECONDS));
            var protocolFailure = assertInstanceOf(org.eclipse.lsp4j.jsonrpc.ResponseErrorException.class, failure.getCause());
            assertEquals(org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.ContentModified.getValue(), protocolFailure.getResponseError().getCode());
            assertEquals(disk, Files.readString(app));
            assertFalse(Files.exists(directory.resolve("App.java")));
        }
    }

    @Test void protocolResolvesImportedNamesAndRetainsOversizedDocumentVersions() throws Exception {
        Path local = directory.resolve("local/Counter.jx"), external = directory.resolve("external/Counter.jx");
        Files.createDirectories(local.getParent()); Files.createDirectories(external.getParent());
        Files.writeString(local, "package local; import jx.*; @Component public class Counter { public record Props(int initial) {} public static View render(Props props) { return <span/>; } }");
        Files.writeString(external, "package external; import jx.*; @Component public class Counter { public record Props(String label) {} public static View render(Props props) { return <span/>; } }");
        Path app = directory.resolve("local/App.jx");
        String valid = "package local; import external.Counter; import jx.*; @Component public class App { public static View render() { return <Counter label=\"hello\"/>; } }";
        Files.writeString(app, valid);
        try (var session = new Session()) {
            InitializeParams init = new InitializeParams();
            init.setWorkspaceFolders(List.of(new WorkspaceFolder(directory.toUri().toString(), "fixture")));
            session.remote.initialize(init).get(5, TimeUnit.SECONDS);
            String uri = app.toUri().toString();
            session.remote.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "jx", 1, valid)));
            assertEquals(List.of(), session.client.await(d -> uri.equals(d.getUri()) && Integer.valueOf(1).equals(d.getVersion())).getDiagnostics());
            var definition = session.remote.getTextDocumentService().definition(new DefinitionParams(new TextDocumentIdentifier(uri), Positions.position(valid, valid.indexOf("<Counter") + 2))).get(5, TimeUnit.SECONDS);
            assertEquals(external.toUri().toString(), definition.getLeft().getFirst().getUri());
            String incomplete = valid.substring(0, valid.indexOf("<Counter")) + "<Counter ";
            session.change(uri, 2, incomplete);
            var completions = session.remote.getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(uri), Positions.position(incomplete, incomplete.length()))).get(5, TimeUnit.SECONDS);
            assertTrue(completions.getLeft().stream().anyMatch(item -> item.getLabel().equals("label")));
            assertFalse(completions.getLeft().stream().anyMatch(item -> item.getLabel().equals("initial")));

            session.change(uri, 4, " ".repeat(ProjectSources.MAX_DOCUMENT_LENGTH + 1));
            assertFalse(session.client.await(d -> uri.equals(d.getUri()) && Integer.valueOf(4).equals(d.getVersion())).getDiagnostics().isEmpty());
            session.change(uri, 3, valid);
            var rejected = session.remote.getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(uri), new Position(0, 0)));
            assertThrows(java.util.concurrent.ExecutionException.class, () -> rejected.get(5, TimeUnit.SECONDS));
            session.change(uri, 5, valid);
            assertEquals(List.of(), session.client.await(d -> uri.equals(d.getUri()) && Integer.valueOf(5).equals(d.getVersion())).getDiagnostics());
        }
    }

    static final class Client implements LanguageClient {
        final LinkedBlockingQueue<PublishDiagnosticsParams> diagnostics = new LinkedBlockingQueue<>();
        @Override public void telemetryEvent(Object object) {}
        @Override public void publishDiagnostics(PublishDiagnosticsParams params) { diagnostics.add(params); }
        @Override public void showMessage(MessageParams params) {}
        @Override public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams params) { return CompletableFuture.completedFuture(null); }
        @Override public void logMessage(MessageParams params) { System.err.println(params.getMessage()); }
        PublishDiagnosticsParams await(Predicate<PublishDiagnosticsParams> predicate) throws InterruptedException {
            long until = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (System.nanoTime() < until) {
                var result = diagnostics.poll(100, TimeUnit.MILLISECONDS);
                if (result != null && predicate.test(result)) return result;
            }
            throw new AssertionError("Expected versioned diagnostics were not published");
        }
    }

    public interface EditorServer extends LanguageServer {
        @org.eclipse.lsp4j.jsonrpc.services.JsonRequest("jx/editorProjection")
        CompletableFuture<EditorProjectionResult> editorProjection(EditorProjectionParams params);
    }

    static final class Session implements AutoCloseable {
        final PipedInputStream serverInput = new PipedInputStream(65_536);
        final PipedInputStream clientInput = new PipedInputStream(65_536);
        final PipedOutputStream clientOutput;
        final PipedOutputStream serverOutput;
        final JxLanguageServer server = new JxLanguageServer();
        final Client client = new Client();
        final EditorServer remote;
        final java.util.concurrent.Future<Void> serverListening;
        final java.util.concurrent.Future<Void> clientListening;
        Session() throws Exception {
            clientOutput = new PipedOutputStream(serverInput);
            serverOutput = new PipedOutputStream(clientInput);
            var serverLauncher = LSPLauncher.createServerLauncher(server, serverInput, serverOutput);
            server.connect(serverLauncher.getRemoteProxy());
            var clientLauncher = Launcher.createLauncher(client, EditorServer.class, clientInput, clientOutput);
            remote = clientLauncher.getRemoteProxy();
            serverListening = serverLauncher.startListening();
            clientListening = clientLauncher.startListening();
        }
        void change(String uri, int version, String text) {
            remote.getTextDocumentService().didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri, version), List.of(new TextDocumentContentChangeEvent(text))));
        }
        @Override public void close() throws Exception {
            server.close();
            clientOutput.close(); serverOutput.close();
            serverListening.cancel(true); clientListening.cancel(true);
            serverInput.close(); clientInput.close();
        }
    }
}
