package jx.browser;

import jx.Cancellation;
import jx.Task;
import java.util.Objects;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Function;
import org.teavm.jso.*;

/** Same-origin asynchronous JSON HTTP. Requests/responses are bounded, timed, cancellable, and never block.
 * Completion runs once on the browser thread. Cancellation intentionally does not invoke completion.
 * The server remains responsible for authentication, authorization, validation, and CSRF protection. */
public final class Http {
    private Http() {}
    public enum Method { GET, POST, PUT, PATCH, DELETE }
    public enum FailureKind { HTTP, NETWORK, TIMEOUT, PAYLOAD_LIMIT, INVALID_JSON, DECODE }
    public sealed interface Result<T> permits Success, Failure {}
    public record Success<T>(T value, int status) implements Result<T> {}
    public record Failure<T>(FailureKind kind, int status, String message, Json body) implements Result<T> {}
    /** Only same-origin absolute paths are accepted. Body is null for an absent request body.
     * Default limits are ten seconds and one MiB; custom limits may not exceed one minute/eight MiB. */
    /** Bounded application header. Browser-controlled headers and JSON media headers cannot
     * be overridden. Values are never included in diagnostics or the record's string form. */
    public record Header(String name, String value) {
        public Header {
            Objects.requireNonNull(name); Objects.requireNonNull(value);
            if (name.isEmpty() || name.length() > 64 || !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) throw new IllegalArgumentException("Invalid HTTP header name");
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("sec-") || lower.startsWith("proxy-") || switch (lower) {
                case "accept", "content-type", "cookie", "cookie2", "host", "origin", "referer", "content-length",
                     "accept-charset", "accept-encoding", "access-control-request-headers", "access-control-request-method",
                     "connection", "date", "dnt", "expect", "keep-alive", "permissions-policy", "set-cookie",
                     "te", "trailer", "transfer-encoding", "upgrade", "via", "x-http-method", "x-http-method-override", "x-method-override" -> true;
                default -> false;
            }) throw new IllegalArgumentException("Header is controlled by the browser or JSON transport");
            if (value.length() > 8192 || value.chars().anyMatch(c -> c < 32 || c > 126)) throw new IllegalArgumentException("Invalid HTTP header value");
        }
        @Override public String toString() { return "Header[name=" + name + ", value=<redacted>]"; }
    }
    public record Request(Method method, String url, Json body, int timeoutMillis, int maxBytes, List<Header> headers) {
        public Request {
            Objects.requireNonNull(method); Objects.requireNonNull(url);
            if (!url.startsWith("/") || url.startsWith("//") || url.contains("\\") || url.length() > 8192) throw new IllegalArgumentException("HTTP requires a bounded same-origin absolute path");
            if (method == Method.GET && body != null) throw new IllegalArgumentException("GET cannot have a body");
            if (timeoutMillis < 1 || timeoutMillis > 60_000 || maxBytes < 1 || maxBytes > 8_388_608) throw new IllegalArgumentException("Invalid HTTP limits");
            headers = List.copyOf(headers);
            if (headers.size() > 16) throw new IllegalArgumentException("Too many HTTP headers");
            var names = new HashSet<String>();
            int bytes = 0;
            for (Header header : headers) {
                if (!names.add(header.name().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate HTTP header");
                bytes += header.name().length() + header.value().length();
            }
            if (bytes > 32768) throw new IllegalArgumentException("HTTP headers exceed limit");
        }
        public Request(Method method, String url, Json body, int timeoutMillis, int maxBytes) { this(method, url, body, timeoutMillis, maxBytes, List.of()); }
        public Request(Method method, String url, Json body) { this(method, url, body, 10_000, 1_048_576); }
        /** Returns a new request; duplicates are rejected instead of silently replacing credentials. */
        public Request withHeader(String name, String value) {
            var next = new ArrayList<>(headers); next.add(new Header(name, value));
            return new Request(method, url, body, timeoutMillis, maxBytes, next);
        }
        public static Request get(String url) { return new Request(Method.GET, url, null); }
        public static Request post(String url, Json body) { return new Request(Method.POST, url, Objects.requireNonNull(body)); }
        public static Request put(String url, Json body) { return new Request(Method.PUT, url, Objects.requireNonNull(body)); }
        public static Request delete(String url) { return new Request(Method.DELETE, url, null); }
    }
    public static <T> Cancellation get(String url, Function<Json, T> decoder, Consumer<Result<T>> completion) { return send(Request.get(url), decoder, completion); }
    /** A cold task for a resource, loader or command. No request starts until
     * {@link Task#start} is called. Every start owns a new request; the existing
     * structured HTTP failure (including timeout/network/schema categories and
     * body) is preserved. Cancellation suppresses completion. */
    public static <T> Task<T, Failure<T>> task(Request request, Function<Json, T> decoder) {
        Objects.requireNonNull(request); Objects.requireNonNull(decoder);
        return completion -> {
            Objects.requireNonNull(completion);
            return send(request, decoder, result -> {
                switch (result) {
                    case Success<T> success -> completion.accept(new Task.Success<>(success.value()));
                    case Failure<T> failure -> completion.accept(new Task.Failure<>(failure));
                }
            });
        };
    }
    public static <T> Cancellation send(Request request, Function<Json, T> decoder, Consumer<Result<T>> completion) {
        Objects.requireNonNull(request); Objects.requireNonNull(decoder); Objects.requireNonNull(completion);
        JSObject headers = headers(request.body() != null);
        for (Header header : request.headers()) addHeader(headers, header.name(), header.value());
        JSObject handle = fetch(request.url(), request.method().name(), request.body() == null ? null : request.body().stringify(), headers, request.timeoutMillis(), request.maxBytes(), (status, kind, body) -> {
            Json json = new Json(body);
            if (!kind.isEmpty()) {
                FailureKind failure = FailureKind.valueOf(kind);
                completion.accept(new Failure<>(failure, status, failure == FailureKind.HTTP ? "HTTP " + status : failure.name(), json));
                return;
            }
            T value;
            try { value = decoder.apply(json); }
            catch (RuntimeException invalid) { completion.accept(new Failure<>(FailureKind.DECODE, status, invalid.getMessage(), Json.nil())); return; }
            completion.accept(new Success<>(value, status));
        });
        return () -> cancel(handle);
    }
    @JSFunctor interface Completed extends JSObject { void complete(int status, String failureKind, JSObject body); }
    @JSBody(params = "body", script = "var h = new Headers(); h.set('Accept', 'application/json'); if (body) h.set('Content-Type', 'application/json'); return h;")
    private static native JSObject headers(boolean body);
    @JSBody(params = {"headers", "name", "value"}, script = "headers.set(name, value);")
    private static native void addHeader(JSObject headers, String name, String value);
    @JSBody(params = {"url", "method", "body", "headers", "timeout", "limit", "done"}, script = """
        var controller = new AbortController();
        var finished = false;
        var timedOut = false;
        var timer = setTimeout(function() { timedOut = true; controller.abort(); }, timeout);
        function finish(status, kind, json) {
            if (finished) return;
            finished = true; clearTimeout(timer); done(status, kind, json);
        }
        Promise.resolve().then(function() {
            var target = new URL(url, location.href);
            if (target.origin !== location.origin) throw {kind: 'NETWORK'};
            if (body !== null && new TextEncoder().encode(body).length > limit) throw {kind: 'PAYLOAD_LIMIT'};
            return fetch(target.href, {method: method, body: body === null ? undefined : body,
                headers: headers,
                credentials: 'same-origin', redirect: 'error', signal: controller.signal});
        }).then(function(response) {
            if (Number(response.headers.get('Content-Length') || 0) > limit) { controller.abort(); throw {kind: 'PAYLOAD_LIMIT'}; }
            if (!response.body) return {response: response, text: ''};
            var reader = response.body.getReader(); var chunks = []; var length = 0;
            function read() {
                return reader.read().then(function(part) {
                    if (part.done) {
                        var all = new Uint8Array(length); var offset = 0;
                        chunks.forEach(function(chunk) { all.set(chunk, offset); offset += chunk.length; });
                        return {response: response, text: new TextDecoder('utf-8', {fatal: true}).decode(all)};
                    }
                    length += part.value.length;
                    if (length > limit) { controller.abort(); throw {kind: 'PAYLOAD_LIMIT'}; }
                    chunks.push(part.value); return read();
                });
            }
            return read();
        }).then(function(result) {
            var json = null;
            if (result.text.length) {
                try { json = JSON.parse(result.text); }
                catch (error) { finish(result.response.status, result.response.ok ? 'INVALID_JSON' : 'HTTP', null); return; }
            }
            finish(result.response.status, result.response.ok ? '' : 'HTTP', json);
        }).catch(function(error) {
            if (finished) { if (!controller.signal.aborted) setTimeout(function() { throw error; }, 0); return; }
            finish(0, timedOut ? 'TIMEOUT' : error.kind || 'NETWORK', null);
        });
        return {cancel: function() { if (!finished) { finished = true; clearTimeout(timer); controller.abort(); } }};
        """)
    private static native JSObject fetch(String url, String method, String body, JSObject headers, int timeout, int limit, Completed done);
    @JSBody(params = "handle", script = "handle.cancel();") private static native void cancel(JSObject handle);
}
