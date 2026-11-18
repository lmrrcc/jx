package jx.maven;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/** Loopback development server. Publishes complete successful builds atomically. */
final class DevServer implements AutoCloseable {
    record Status(long version, String state) {}
    private record Assets(Map<String, byte[]> files, Set<String> forbidden) {}
    private static final long MAX_ASSET_BYTES = 32L * 1024 * 1024;
    private final String session = UUID.randomUUID().toString();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Path assets;
    private final AtomicReference<Assets> published = new AtomicReference<>(new Assets(Map.of(), Set.of()));
    private final AtomicReference<Status> status;
    private final String client;
    private final DevelopmentProxy proxy;

    DevServer(Path assets, int port, AtomicReference<Status> status) throws IOException {
        this(assets, port, status, null);
    }
    DevServer(Path assets, int port, AtomicReference<Status> status, String apiOrigin) throws IOException {
        this.assets = assets.toAbsolutePath().normalize(); this.status = status;
        try (var input = DevServer.class.getResourceAsStream("/jx-dev-client.js")) {
            if (input == null) throw new IOException("Missing JX development client");
            client = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (status.get().state().equals("ready")) publish();
        proxy = apiOrigin == null || apiOrigin.isBlank() ? null : new DevelopmentProxy(apiOrigin);
        try { this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 32); }
        catch (IOException | RuntimeException failure) { if (proxy != null) proxy.close(); executor.shutdownNow(); throw failure; }
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }
    int port() { return server.getAddress().getPort(); }

    /** No partial build output is visible; failed publication leaves the previous generation running. */
    void publish() throws IOException {
        Map<String, byte[]> next = new HashMap<>();
        Set<String> forbidden = new java.util.HashSet<>();
        Path realRoot = assets.toRealPath();
        try (var paths = Files.walk(assets)) {
            var files = paths.filter(Files::isRegularFile).limit(10_001).toList();
            if (files.size() > 10_000) throw new IOException("Development assets exceed 10,000 files");
            long total = 0;
            for (Path file : files) {
                String name = "/" + assets.relativize(file).toString().replace('\\', '/');
                if (!file.toRealPath().startsWith(realRoot)) { forbidden.add(name); continue; }
                long size = Files.size(file);
                total += size;
                if (size > MAX_ASSET_BYTES || total > 128L * 1024 * 1024)
                    throw new IOException("Development assets exceed 32 MiB per file or 128 MiB total: " + name);
                next.put(name, Files.readAllBytes(file));
            }
        }
        if (!next.containsKey("/index.html")) throw new IOException("Development build did not produce index.html");
        published.set(new Assets(Map.copyOf(next), Set.copyOf(forbidden)));
    }

    private String statusJson() {
        Status current = status.get();
        return "{\"version\":" + current.version() + ",\"state\":\"" + current.state() + "\",\"session\":\"" + session + "\"}";
    }
    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (!("127.0.0.1:" + port()).equals(host) && !("localhost:" + port()).equals(host)) {
                respond(exchange, 403, "text/plain", "Unexpected development host".getBytes(StandardCharsets.UTF_8)); return;
            }
            if (proxy != null && exchange.getRequestURI().getPath().startsWith("/api/")) {
                proxy.handle(exchange, port()); return;
            }
            if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                respond(exchange, 405, "text/plain", new byte[0]); return;
            }
            String requested = exchange.getRequestURI().getPath();
            if (requested.equals("/__jx_dev/version")) {
                respond(exchange, 200, "application/json", statusJson().getBytes(StandardCharsets.UTF_8)); return;
            }
            if (requested.equals("/__jx_dev/client.js")) {
                respond(exchange, 200, "text/javascript", client.replace("__JX_INITIAL_STATUS__", statusJson()).getBytes(StandardCharsets.UTF_8)); return;
            }
            Path file;
            try { file = assets.resolve(requested.substring(1)).normalize(); }
            catch (RuntimeException ex) { respond(exchange, 400, "text/plain", new byte[0]); return; }
            if (!file.startsWith(assets)) { respond(exchange, 403, "text/plain", new byte[0]); return; }
            Assets current = published.get();
            String name = "/" + assets.relativize(file).toString().replace('\\', '/');
            if (current.forbidden().contains(name)) { respond(exchange, 403, "text/plain", new byte[0]); return; }
            if (requested.endsWith("/")) name = name.endsWith("/") ? name + "index.html" : name + "/index.html";
            if (!current.files().containsKey(name) && !file.getFileName().toString().contains(".")) name = "/index.html";
            byte[] bytes = current.files().get(name);
            if (bytes == null) {
                respond(exchange, current.files().isEmpty() ? 503 : 404, "text/html",
                        ("<!doctype html><head>" + script() + "<title>JX development build</title></head><body data-jx-waiting><p>Waiting for a successful JX build. See Maven diagnostics.</p></body>").getBytes(StandardCharsets.UTF_8));
                return;
            }
            String type = contentType(name);
            if (type.equals("text/html")) {
                String html = new String(bytes, StandardCharsets.UTF_8);
                int head = html.toLowerCase(Locale.ROOT).indexOf("<head>");
                int offset = head < 0 ? 0 : head + "<head>".length();
                html = html.substring(0, offset) + script() + html.substring(offset);
                bytes = html.getBytes(StandardCharsets.UTF_8);
            }
            respond(exchange, 200, type, bytes);
        } catch (IOException ex) {
            // A disconnected browser owns its failed exchange; other clients and the watcher continue.
        }
    }
    private String script() {
        return "<script src=\"/__jx_dev/client.js\"></script>"
                + (published.get().files().containsKey("/jx-vendor.js") ? "<script src=\"/jx-vendor.js\"></script>" : "");
    }
    private static String contentType(String file) {
        String name = file.toLowerCase(Locale.ROOT);
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
    @Override public void close() { server.stop(0); executor.shutdownNow(); if (proxy != null) proxy.close(); }
}
