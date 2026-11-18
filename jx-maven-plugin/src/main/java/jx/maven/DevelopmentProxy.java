package jx.maven;

import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Optional development-only bridge to one explicitly configured local HTTP API.
 * Owns its HTTP client; the caller owns the exchange and validates its Host header.
 * Cookies, authorization, redirects, and arbitrary headers are never forwarded.
 */
final class DevelopmentProxy implements AutoCloseable {
    static final int MAX_REQUEST_BYTES = 1024 * 1024;
    static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final Set<String> REQUEST_HEADERS = Set.of("Accept", "Content-Type", "If-Match", "If-None-Match");
    private static final Set<String> RESPONSE_HEADERS = Set.of("Content-Type", "ETag", "Last-Modified", "Retry-After", "Allow");
    private final URI origin;
    private final Duration timeout;
    private final HttpClient client;
    private final Semaphore slots = new Semaphore(8);

    DevelopmentProxy(String apiOrigin) { this(apiOrigin, Duration.ofSeconds(15)); }

    /** Shorter deadlines are available to isolated fixture tests. */
    DevelopmentProxy(String apiOrigin, Duration timeout) {
        origin = validateOrigin(apiOrigin);
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(15)) > 0)
            throw new IllegalArgumentException("Development proxy timeout must be positive and at most 15 seconds");
        this.timeout = timeout;
        client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1).proxy(new DirectProxySelector()).build();
    }

    /**
     * Handles only /api/ paths, preserving their raw query, method, status and bounded body.
     * Browser writes require an exact same-origin Origin header; cross-site requests fail
     * before contacting the backend. Read requests may omit Origin for local CLI tooling.
     * The 15-second deadline covers the complete upstream response, including its body.
     */
    void handle(HttpExchange exchange, int devPort) throws IOException {
        URI requested = exchange.getRequestURI();
        if (!validPath(requested)) { error(exchange, 400, "Only normalized /api/ paths can be proxied"); return; }
        String method = exchange.getRequestMethod();
        if (!METHODS.contains(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS");
            error(exchange, 405, "Unsupported API method"); return;
        }
        if (!sameOrigin(exchange, devPort)) { error(exchange, 403, "API requests must come from this development origin"); return; }
        if (!slots.tryAcquire()) { error(exchange, 503, "Too many active API requests"); return; }
        try {
            String length = exchange.getRequestHeaders().getFirst("Content-Length");
            if (length != null) {
                try {
                    long declared = Long.parseLong(length);
                    if (declared < 0) { error(exchange, 400, "Invalid request length"); return; }
                    if (declared > MAX_REQUEST_BYTES) { error(exchange, 413, "API request exceeds 1 MiB"); return; }
                } catch (NumberFormatException ex) { error(exchange, 400, "Invalid request length"); return; }
            }
            byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
            if (body.length > MAX_REQUEST_BYTES) { error(exchange, 413, "API request exceeds 1 MiB"); return; }
            String query = requested.getRawQuery();
            URI target = URI.create(origin + requested.getRawPath() + (query == null ? "" : "?" + query));
            var builder = HttpRequest.newBuilder(target).timeout(timeout).header("Accept-Encoding", "identity")
                    .method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
            for (String name : REQUEST_HEADERS) {
                String value = exchange.getRequestHeaders().getFirst(name);
                if (value != null && value.length() <= 8192) builder.header(name, value);
            }
            var pending = client.sendAsync(builder.build(), ignored -> new BoundedBody());
            try {
                HttpResponse<byte[]> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!response.headers().firstValue("Content-Encoding").orElse("identity").equalsIgnoreCase("identity")) {
                    error(exchange, 502, "API backend ignored the identity encoding request"); return;
                }
                for (String name : RESPONSE_HEADERS) response.headers().firstValue(name)
                        .filter(value -> value.length() <= 8192).ifPresent(value -> exchange.getResponseHeaders().set(name, value));
                respond(exchange, response.statusCode(), response.body());
            } catch (TimeoutException ex) {
                pending.cancel(true); error(exchange, 504, "API backend response timed out; a write may already have completed");
            } catch (InterruptedException ex) {
                pending.cancel(true); Thread.currentThread().interrupt(); error(exchange, 503, "API proxy stopped");
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof java.net.http.HttpTimeoutException)
                    error(exchange, 504, "API backend response timed out; a write may already have completed");
                else if (cause instanceof ResponseTooLarge)
                    error(exchange, 502, "API response exceeds 4 MiB");
                else error(exchange, 502, "Cannot read the configured local API backend");
            }
        } finally { slots.release(); }
    }

    private static URI validateOrigin(String value) {
        URI uri;
        try { uri = URI.create(value == null ? "" : value); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("API origin must be http://127.0.0.1:port or http://localhost:port", ex); }
        if (!"http".equals(uri.getScheme()) || !("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
                || uri.getPort() < 1 || uri.getPort() > 65535 || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || !(uri.getRawPath().isEmpty() || uri.getRawPath().equals("/")))
            throw new IllegalArgumentException("API origin must be http://127.0.0.1:port or http://localhost:port without a path or credentials");
        // Do not resolve a configurable hostname or allow proxy/environment redirection.
        return URI.create("http://127.0.0.1:" + uri.getPort());
    }

    private static boolean validPath(URI uri) {
        if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getFragment() != null) return false;
        String raw = uri.getRawPath();
        if (raw == null || !raw.startsWith("/api/") || raw.length() > 8192
                || (uri.getRawQuery() != null && uri.getRawQuery().length() > 8192)) return false;
        String decoded = uri.getPath();
        if (decoded.indexOf('\\') >= 0 || decoded.indexOf('%') >= 0 || decoded.contains("//")
                || decoded.chars().anyMatch(Character::isISOControl)) return false;
        for (String segment : decoded.split("/")) if (segment.equals(".") || segment.equals("..")) return false;
        return raw.toLowerCase(Locale.ROOT).indexOf("%2f") < 0;
    }

    private static boolean sameOrigin(HttpExchange exchange, int port) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (!("127.0.0.1:" + port).equals(host) && !("localhost:" + port).equals(host)) return false;
        var origins = exchange.getRequestHeaders().get("Origin");
        String method = exchange.getRequestMethod();
        if (origins == null && !(method.equals("GET") || method.equals("HEAD"))) return false;
        if (origins != null && (origins.size() != 1 || !("http://" + host).equals(origins.getFirst()))) return false;
        var sites = exchange.getRequestHeaders().get("Sec-Fetch-Site");
        return sites == null || (sites.size() == 1 && (sites.getFirst().equals("same-origin") || sites.getFirst().equals("none")));
    }

    private static void error(HttpExchange exchange, int status, String message) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        respond(exchange, status, ("JX development proxy: " + message).getBytes(StandardCharsets.UTF_8));
    }
    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (exchange.getRequestMethod().equals("HEAD") || status == 204 || status == 304 || body.length == 0)
            exchange.sendResponseHeaders(status, -1);
        else { exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); }
    }

    private static final class ResponseTooLarge extends IOException {}
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            for (ByteBuffer bytes : buffers) {
                if (bytes.remaining() > MAX_RESPONSE_BYTES - body.size()) {
                    subscription.cancel(); result.completeExceptionally(new ResponseTooLarge()); return;
                }
                byte[] copy = new byte[bytes.remaining()]; bytes.get(copy); body.writeBytes(copy);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable cause) { result.completeExceptionally(cause); }
        @Override public void onComplete() { result.complete(body.toByteArray()); }
    }
    private static final class DirectProxySelector extends ProxySelector {
        @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
        @Override public void connectFailed(URI uri, SocketAddress address, IOException cause) {}
    }
    @Override public void close() { client.shutdownNow(); }
}
