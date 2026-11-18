package jx.lsp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CancellationRegressionTest {
    @TempDir Path directory;
    private static final String SOURCE = """
            import jx.*;
            @Component public final class App {
                public static View render() { return <button title={\"ready\"}>Ready</button>; }
            }
            """;

    @Test void supersedingDiagnosticsNeverInterruptsTheAnalysisWorker() throws Exception {
        PausingExecutor executor = new PausingExecutor();
        var client = new LanguageServerTest.Client();
        try (var server = server(directory, executor, client)) {
            String uri = directory.resolve("App.jx").toUri().toString();
            server.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "jx", 1, SOURCE)));
            executor.awaitEntered();
            change(server, uri, 2, SOURCE.replace("Ready", "Latest"));
            executor.release.countDown();
            var diagnostics = client.await(result -> Integer.valueOf(2).equals(result.getVersion()));
            assertTrue(diagnostics.getDiagnostics().isEmpty(), diagnostics.toString());
            assertFalse(executor.interrupted.get(), "Superseding diagnostics must not interrupt javac's worker");
        } finally { executor.release.countDown(); }
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test void cancellingARequestNeverInterruptsTheAnalysisWorker() throws Exception {
        PausingExecutor executor = new PausingExecutor();
        var client = new LanguageServerTest.Client();
        try (var server = server(directory, executor, client)) {
            String uri = directory.resolve("App.jx").toUri().toString();
            var cancelled = server.editorProjection(new EditorProjectionParams(uri, null));
            executor.awaitEntered();
            assertTrue(cancelled.cancel(true));
            var next = server.editorProjection(new EditorProjectionParams(uri, null));
            executor.release.countDown();
            assertEquals(SOURCE, next.get(10, TimeUnit.SECONDS).files().getFirst().sourceText());
            assertFalse(executor.interrupted.get(), "Client cancellation must not interrupt javac's worker");
        } finally { executor.release.countDown(); }
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test void shutdownCancelsRunningAndQueuedRequestsWithoutInterruptingOwnedWork() throws Exception {
        PausingExecutor executor = new PausingExecutor();
        var client = new LanguageServerTest.Client();
        try (var server = server(directory, executor, client)) {
            String uri = directory.resolve("App.jx").toUri().toString();
            var running = server.editorProjection(new EditorProjectionParams(uri, null));
            executor.awaitEntered();
            var queued = server.editorProjection(new EditorProjectionParams(uri, null));
            server.close();
            assertTrue(running.isCancelled(), "Shutdown must settle running request futures");
            assertTrue(queued.isCancelled(), "Shutdown must settle queued request futures");
            assertTrue(executor.isShutdown());
            executor.release.countDown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            assertFalse(executor.interrupted.get(), "Shutdown must allow compiler resource cleanup without interruption");
        } finally { executor.release.countDown(); }
    }

    @Test void freshJvmKeepsJava25PlatformAvailableAfterRapidChangesAndCancellation() throws Exception {
        // ct.sym release discovery is process-global. A fresh JVM is essential: an earlier compiler
        // test may already have initialized its cache and would conceal the interruption defect.
        for (int attempt = 0; attempt < 2; attempt++) {
            Path project = Files.createDirectory(directory.resolve("fresh-" + attempt));
            Path log = directory.resolve("fresh-" + attempt + ".log");
            Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"), FreshProcessProbe.class.getName(), project.toString())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Fresh compiler cancellation probe timed out");
                assertEquals(0, process.exitValue(), Files.readString(log));
            } finally {
                if (process.isAlive()) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static JxLanguageServer server(Path project, PausingExecutor executor, LanguageServerTest.Client client) throws Exception {
        Files.writeString(project.resolve("App.jx"), SOURCE);
        var server = new JxLanguageServer(() -> {}, executor);
        initialize(server, project, client);
        return server;
    }

    private static void initialize(JxLanguageServer server, Path project, LanguageServerTest.Client client) throws Exception {
        server.connect(client);
        var initialize = new InitializeParams();
        initialize.setWorkspaceFolders(List.of(new WorkspaceFolder(project.toUri().toString(), "cancellation")));
        server.initialize(initialize).get(5, TimeUnit.SECONDS);
    }

    private static void change(JxLanguageServer server, String uri, int version, String text) {
        server.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri, version),
                List.of(new TextDocumentContentChangeEvent(text))));
    }

    /** Stops inside the submitted FutureTask, so cancel(true) demonstrably interrupts the same worker
     * that owns compiler calls. The compiler itself is real; only scheduling is controlled. */
    private static final class PausingExecutor extends ThreadPoolExecutor {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicBoolean interrupted = new AtomicBoolean();
        final AtomicBoolean first = new AtomicBoolean(true);
        PausingExecutor() {
            super(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                    Thread.ofPlatform().name("jx-analysis-regression").daemon().factory());
        }
        @Override protected <T> RunnableFuture<T> newTaskFor(Runnable task, T result) {
            return super.newTaskFor(() -> {
                if (first.compareAndSet(true, false)) {
                    entered.countDown();
                    boolean waiting = true;
                    while (waiting) {
                        try { release.await(); waiting = false; }
                        catch (InterruptedException failure) { interrupted.set(true); }
                    }
                }
                task.run();
            }, result);
        }
        void awaitEntered() throws InterruptedException {
            assertTrue(entered.await(5, TimeUnit.SECONDS), "Analysis did not start");
        }
    }

    /** Launched only by the fresh-JVM regression above. */
    public static final class FreshProcessProbe {
        public static void main(String[] arguments) throws Exception {
            Path project = Path.of(arguments[0]);
            Files.writeString(project.resolve("App.jx"), SOURCE);
            var client = new LanguageServerTest.Client();
            try (var server = new JxLanguageServer()) {
                initialize(server, project, client);
                String uri = project.resolve("App.jx").toUri().toString();
                server.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "jx", 1, SOURCE)));
                for (int version = 2; version < 80; version++) {
                    CompletableFuture<EditorProjectionResult> request = server.editorProjection(new EditorProjectionParams(uri, version - 1));
                    request.cancel(true);
                    change(server, uri, version, SOURCE.replace("Ready", "Version " + version));
                    Thread.sleep(1);
                }
                String latest = SOURCE.replace("Ready", "Latest");
                change(server, uri, 80, latest);
                var projection = server.editorProjection(new EditorProjectionParams(uri, 80)).get(20, TimeUnit.SECONDS);
                if (!projection.files().getFirst().sourceText().equals(latest)) throw new AssertionError("Stale projection");
                var diagnostics = client.await(result -> Integer.valueOf(80).equals(result.getVersion()));
                if (!diagnostics.getDiagnostics().isEmpty()) throw new AssertionError(diagnostics.toString());
            }
        }
    }
}
