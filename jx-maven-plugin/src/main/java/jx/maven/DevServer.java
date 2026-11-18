package jx.maven;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/** Loopback development asset server. Never part of a production application's dependencies. */
final class DevServer implements AutoCloseable {
    record Status(long version, String state) {}
    private static final long MAX_ASSET_BYTES = 32L * 1024 * 1024;
    private static final String RELOAD_SCRIPT = """
            <script>
            (() => {
              let version;
              async function check() {
                try {
                  const response = await fetch('/__jx_dev/version', {cache: 'no-store'});
                  const next = await response.json();
                  if (version === undefined) version = next.version;
                  if (next.state === 'ready' && next.version !== version) { location.reload(); return; }
                  let notice = document.getElementById('__jx_dev_notice');
                  if (next.state === 'failed') {
                    if (!notice) { notice = document.createElement('div'); notice.id = '__jx_dev_notice';
                      notice.setAttribute('role', 'alert');
                      notice.style.cssText = 'position:fixed;inset:0 0 auto;background:#8b1020;color:white;padding:12px;z-index:2147483647;font:16px sans-serif';
                      document.body.appendChild(notice); }
                    notice.textContent = 'JX build failed. See original-source diagnostics in the Maven terminal.';
                  } else if (notice) notice.remove();
                } catch (_) {}
                setTimeout(check, 500);
              }
              check();
            })();
            </script>
            """;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Path assets;
    private final AtomicReference<Status> status;
    DevServer(Path assets, int port, AtomicReference<Status> status) throws IOException {
        this.assets = assets.toAbsolutePath().normalize(); this.status = status;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 32);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }
    int port() { return server.getAddress().getPort(); }
    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (!("127.0.0.1:" + port()).equals(host) && !("localhost:" + port()).equals(host)) {
                respond(exchange, 403, "text/plain", "Unexpected development host".getBytes(StandardCharsets.UTF_8)); return;
            }
            if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                respond(exchange, 405, "text/plain", new byte[0]); return;
            }
            String requested = exchange.getRequestURI().getPath();
            if (requested.equals("/__jx_dev/version")) {
                Status current = status.get();
                respond(exchange, 200, "application/json", ("{\"version\":" + current.version() + ",\"state\":\"" + current.state() + "\"}").getBytes(StandardCharsets.UTF_8));
                return;
            }
            Path file;
            try { file = assets.resolve(requested.substring(1)).normalize(); }
            catch (RuntimeException ex) { respond(exchange, 400, "text/plain", new byte[0]); return; }
            if (!file.startsWith(assets)) { respond(exchange, 403, "text/plain", new byte[0]); return; }
            if (requested.endsWith("/")) file = file.resolve("index.html");
            if (!Files.isRegularFile(file) && !file.getFileName().toString().contains(".")) file = assets.resolve("index.html");
            if (!Files.isRegularFile(file)) {
                respond(exchange, status.get().state().equals("ready") ? 404 : 503, "text/html", ("<!doctype html><title>JX development build</title><p>Asset unavailable. See Maven diagnostics.</p>" + reloadScript()).getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (!file.toRealPath().startsWith(assets.toRealPath())) { respond(exchange, 403, "text/plain", new byte[0]); return; }
            if (Files.size(file) > MAX_ASSET_BYTES) { respond(exchange, 413, "text/plain", new byte[0]); return; }
            String type = contentType(file);
            byte[] bytes = Files.readAllBytes(file);
            if (type.equals("text/html")) {
                String html = new String(bytes, StandardCharsets.UTF_8);
                int body = html.toLowerCase(Locale.ROOT).lastIndexOf("</body>");
                html = body < 0 ? html + reloadScript() : html.substring(0, body) + reloadScript() + html.substring(body);
                bytes = html.getBytes(StandardCharsets.UTF_8);
            }
            respond(exchange, 200, type, bytes);
        } catch (IOException ex) {
            // A disconnected browser owns its failed exchange; the watcher and other clients continue.
        }
    }
    private String reloadScript() { return RELOAD_SCRIPT.replace("let version;", "let version = " + status.get().version() + ";"); }
    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html";
        if (name.endsWith(".js") || name.endsWith(".mjs")) return "text/javascript";
        if (name.endsWith(".css")) return "text/css";
        if (name.endsWith(".json") || name.endsWith(".map")) return "application/json";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".woff2")) return "font/woff2";
        if (name.endsWith(".wasm")) return "application/wasm";
        return "application/octet-stream";
    }
    private static void respond(HttpExchange exchange, int code, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type + (type.startsWith("text/") || type.equals("application/json") ? "; charset=utf-8" : ""));
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(body.length));
            exchange.sendResponseHeaders(code, -1);
        } else {
            exchange.sendResponseHeaders(code, body.length);
            exchange.getResponseBody().write(body);
        }
    }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
