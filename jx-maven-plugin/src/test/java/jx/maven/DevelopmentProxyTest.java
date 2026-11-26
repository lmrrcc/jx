package jx.maven;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DevelopmentProxyTest {
    @Test void onlyExplicitLoopbackHttpOriginsAreAccepted() {
        for (String origin : List.of("", "http://example.com:8181", "http://127.0.0.2:8181", "http://[::1]:8181", "https://localhost:8181",
                "http://localhost", "http://localhost:0", "http://localhost:65536", "http://user@localhost:8181",
                "http://localhost:8181/api", "http://localhost:8181?token=secret", "http://localhost:8181#fragment"))
            assertThrows(IllegalArgumentException.class, () -> new DevelopmentProxy(origin), origin);
        try (var proxy = new DevelopmentProxy("http://localhost:8181/")) { assertNotNull(proxy); }
    }

    @Test void forwardsMethodsQueryBodyAndStatusesButStripsCredentialsAndPrivateHeaders() throws Exception {
        var received = new AtomicReference<String>();
        try (var fixture = new Fixture(exchange -> {
            received.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            assertNull(exchange.getRequestHeaders().getFirst("Cookie"));
            assertNull(exchange.getRequestHeaders().getFirst("X-Private-Token"));
            assertNull(exchange.getRequestHeaders().getFirst("Origin"));
            assertEquals("application/json", exchange.getRequestHeaders().getFirst("Accept"));
            assertEquals("identity", exchange.getRequestHeaders().getFirst("Accept-Encoding"));
            exchange.getResponseHeaders().set("Set-Cookie", "session=secret");
            exchange.getResponseHeaders().set("X-Private-Token", "secret");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("ETag", "\"v1\"");
            reply(exchange, exchange.getRequestMethod().equals("HEAD") ? 204 : 201, "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        })) {
            for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")) {
                String body = method.equals("GET") || method.equals("HEAD") ? "" : "{\"title\":\"Save\"}";
                HttpResponse<byte[]> response = fixture.send(fixture.request("/api/todos?search=a%20b&count=2", method, body.getBytes(StandardCharsets.UTF_8))
                        .header("Origin", fixture.origin()).header("Accept", "application/json").header("Content-Type", "application/json")
                        .header("Authorization", "Bearer secret").header("Cookie", "session=secret").header("X-Private-Token", "secret"));
                assertEquals(method.equals("HEAD") ? 204 : 201, response.statusCode());
                assertEquals(method + " /api/todos?search=a%20b&count=2 " + body, received.get());
                assertEquals("\"v1\"", response.headers().firstValue("ETag").orElseThrow());
                assertTrue(response.headers().firstValue("Set-Cookie").isEmpty());
                assertTrue(response.headers().firstValue("X-Private-Token").isEmpty());
                assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            }
        }
    }

    @Test void rejectsCrossOriginWritesAndEscapingPathsWithoutContactingBackend() throws Exception {
        var calls = new AtomicInteger();
        try (var fixture = new Fixture(exchange -> { calls.incrementAndGet(); reply(exchange, 200, new byte[0]); })) {
            assertEquals(403, fixture.send(fixture.request("/api/todos", "POST", new byte[0])).statusCode());
            for (String origin : List.of("http://evil.example", "null", "http://localhost:" + fixture.port()))
                assertEquals(403, fixture.send(fixture.request("/api/todos", "POST", new byte[0]).header("Origin", origin)).statusCode());
            assertEquals(403, fixture.send(fixture.request("/api/todos", "GET", new byte[0]).header("Sec-Fetch-Site", "cross-site")).statusCode());
            assertEquals(403, fixture.send(fixture.request("/api/todos", "POST", new byte[0]).header("Origin", fixture.origin()).header("Origin", fixture.origin())).statusCode());
            for (String path : List.of("/other", "/api/../private", "/api/%2e%2e/private", "/api/%252e%252e/private", "/api/%2fprivate", "/api/a%5cb", "/api/a%00b"))
                assertEquals(400, fixture.send(fixture.request(path, "GET", new byte[0])).statusCode(), path);
            assertEquals(405, fixture.send(fixture.request("/api/todos", "TRACE", new byte[0])).statusCode());
            assertEquals(0, calls.get());
        }
    }

    @Test void boundsRequestAndResponseBytesIncludingChunkedMessages() throws Exception {
        var responseSize = new AtomicInteger(1);
        var requestSize = new AtomicInteger();
        try (var fixture = new Fixture(exchange -> {
            requestSize.set(exchange.getRequestBody().readAllBytes().length);
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(new byte[responseSize.get()]);
        })) {
            assertEquals(200, fixture.send(fixture.request("/api/upload", "POST", new byte[DevelopmentProxy.MAX_REQUEST_BYTES])
                    .header("Origin", fixture.origin())).statusCode());
            assertEquals(DevelopmentProxy.MAX_REQUEST_BYTES, requestSize.get());
            assertEquals(413, fixture.send(fixture.request("/api/upload", "POST", new byte[DevelopmentProxy.MAX_REQUEST_BYTES + 1])
                    .header("Origin", fixture.origin())).statusCode());
            var chunked = HttpRequest.newBuilder(URI.create(fixture.origin() + "/api/upload")).timeout(Duration.ofSeconds(5))
                    .header("Origin", fixture.origin()).POST(HttpRequest.BodyPublishers.ofInputStream(
                            () -> new java.io.ByteArrayInputStream(new byte[DevelopmentProxy.MAX_REQUEST_BYTES + 1])));
            assertEquals(413, fixture.send(chunked).statusCode());
            responseSize.set(DevelopmentProxy.MAX_RESPONSE_BYTES);
            assertEquals(DevelopmentProxy.MAX_RESPONSE_BYTES, fixture.send(fixture.request("/api/large", "GET", new byte[0])).body().length);
            responseSize.incrementAndGet();
            var oversized = fixture.send(fixture.request("/api/large", "GET", new byte[0]));
            assertEquals(502, oversized.statusCode());
            assertTrue(new String(oversized.body(), StandardCharsets.UTF_8).contains("4 MiB"));
        }
    }

    @Test void preservesBackendErrorsAndDoesNotFollowOrExposeRedirects() throws Exception {
        var calls = new AtomicInteger();
        try (var fixture = new Fixture(exchange -> {
            calls.incrementAndGet();
            if (exchange.getRequestURI().getPath().equals("/api/redirect")) {
                exchange.getResponseHeaders().set("Location", "http://example.invalid/private"); reply(exchange, 302, new byte[0]);
            } else reply(exchange, 409, "Conflict".getBytes(StandardCharsets.UTF_8));
        })) {
            var redirect = fixture.send(fixture.request("/api/redirect", "GET", new byte[0]));
            assertEquals(302, redirect.statusCode()); assertTrue(redirect.headers().firstValue("Location").isEmpty());
            var conflict = fixture.send(fixture.request("/api/conflict", "GET", new byte[0]));
            assertEquals(409, conflict.statusCode()); assertEquals("Conflict", new String(conflict.body(), StandardCharsets.UTF_8));
            assertEquals(2, calls.get());
            fixture.backend.stop(0);
            assertEquals(502, fixture.send(fixture.request("/api/unavailable", "GET", new byte[0])).statusCode());
        }
    }

    @Test void deadlineIncludesAnUpstreamBodyThatStallsAfterHeaders() throws Exception {
        var sentHeaders = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var fixture = new Fixture(exchange -> {
            exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().write('x'); exchange.getResponseBody().flush();
            sentHeaders.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }, Duration.ofMillis(350))) {
            long started = System.nanoTime();
            var response = fixture.send(fixture.request("/api/stall", "GET", new byte[0]));
            assertEquals(0, sentHeaders.getCount()); assertEquals(504, response.statusCode());
            assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(3)) < 0);
        } finally { release.countDown(); }
    }

    private static void reply(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (exchange.getRequestMethod().equals("HEAD") || body.length == 0) exchange.sendResponseHeaders(status, -1);
        else { exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); }
    }
    @Test void sessionOptInForwardsOnlyOneCookieAndCsrfWithSafeResponseAttributes() throws Exception {
        var calls = new AtomicInteger();
        var output = new AtomicReference<>("JXSESSION=next; Path=/; HttpOnly; SameSite=Lax; Secure");
        var location = new AtomicReference<>("/todos");
        try (var fixture = new Fixture(exchange -> {
            calls.incrementAndGet();
            assertEquals("JXSESSION=owned", exchange.getRequestHeaders().getFirst("Cookie"));
            assertEquals("masked", exchange.getRequestHeaders().getFirst("X-CSRF-TOKEN"));
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().add("Set-Cookie", "other=secret; Domain=example.com");
            exchange.getResponseHeaders().add("Set-Cookie", output.get());
            exchange.getResponseHeaders().set("Location", location.get());
            reply(exchange, 303, new byte[0]);
        }, Duration.ofSeconds(15), "JXSESSION")) {
            var request = fixture.request("/api/session/login", "POST", new byte[0]).header("Origin", fixture.origin())
                    .header("Cookie", "other=private; JXSESSION=owned").header("X-CSRF-TOKEN", "masked")
                    .header("Authorization", "Bearer private");
            var response = fixture.send(request);
            assertEquals(303, response.statusCode());
            assertEquals(List.of(output.get()), response.headers().allValues("Set-Cookie"));
            assertEquals("/todos", response.headers().firstValue("Location").orElseThrow());
            for (String invalid : List.of("JXSESSION=x; Path=/; HttpOnly; SameSite=None", "JXSESSION=x; Path=/; SameSite=Lax",
                    "JXSESSION=x; Path=/api; HttpOnly; SameSite=Lax", "JXSESSION=x; Path=/; HttpOnly; SameSite=Lax; Domain=localhost")) {
                output.set(invalid); var rejected = fixture.send(request);
                assertEquals(502, rejected.statusCode()); assertTrue(rejected.headers().allValues("Set-Cookie").isEmpty());
            }
            output.set("JXSESSION=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax");
            assertEquals(303, fixture.send(request).statusCode());
            for (String invalid : List.of("https://external.invalid/steal", "//external.invalid/steal", "/%2e%2e/private")) {
                location.set(invalid);
                assertEquals(502, fixture.send(request).statusCode());
            }
            int before = calls.get();
            assertEquals(400, fixture.send(request.copy().header("Cookie", "JXSESSION=second")).statusCode());
            assertEquals(403, fixture.send(fixture.request("/api/session/login", "POST", new byte[0]).header("Origin", "http://evil.example")).statusCode());
            assertEquals(before, calls.get());
        }
    }
    private static final class Fixture implements AutoCloseable {
        private final HttpServer backend;
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(6);
        private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        private final DevelopmentProxy proxy;
        private Fixture(HttpHandler handler) throws IOException { this(handler, Duration.ofSeconds(15)); }
        private Fixture(HttpHandler handler, Duration timeout) throws IOException {
            this(handler, timeout, null);
        }
        private Fixture(HttpHandler handler, Duration timeout, String sessionCookie) throws IOException {
            backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
            backend.setExecutor(executor);
            backend.createContext("/", exchange -> {
                try (exchange) { handler.handle(exchange); }
                catch (IOException ignored) { /* The proxy deliberately cancels oversized/stalled responses. */ }
            });
            backend.start();
            proxy = new DevelopmentProxy("http://localhost:" + backend.getAddress().getPort(), timeout, sessionCookie);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
            server.setExecutor(executor);
            server.createContext("/", exchange -> { try (exchange) { proxy.handle(exchange, port()); } });
            server.start();
        }
        private int port() { return server.getAddress().getPort(); }
        private String origin() { return "http://127.0.0.1:" + port(); }
        private HttpRequest.Builder request(String path, String method, byte[] body) {
            return HttpRequest.newBuilder(URI.create(origin() + path)).timeout(Duration.ofSeconds(5))
                    .method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        }
        private HttpResponse<byte[]> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        }
        @Override public void close() {
            server.stop(0); backend.stop(0); proxy.close(); client.shutdownNow(); executor.shutdownNow();
        }
    }
}
