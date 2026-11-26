package jx.browser;

import jx.Cancellation;
import java.util.Objects;
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
    public record Request(Method method, String url, Json body, int timeoutMillis, int maxBytes) {
        public Request {
            Objects.requireNonNull(method); Objects.requireNonNull(url);
            if (!url.startsWith("/") || url.startsWith("//") || url.contains("\\") || url.length() > 8192) throw new IllegalArgumentException("HTTP requires a bounded same-origin absolute path");
            if (method == Method.GET && body != null) throw new IllegalArgumentException("GET cannot have a body");
            if (timeoutMillis < 1 || timeoutMillis > 60_000 || maxBytes < 1 || maxBytes > 8_388_608) throw new IllegalArgumentException("Invalid HTTP limits");
        }
        public Request(Method method, String url, Json body) { this(method, url, body, 10_000, 1_048_576); }
        public static Request get(String url) { return new Request(Method.GET, url, null); }
        public static Request post(String url, Json body) { return new Request(Method.POST, url, Objects.requireNonNull(body)); }
        public static Request put(String url, Json body) { return new Request(Method.PUT, url, Objects.requireNonNull(body)); }
        public static Request delete(String url) { return new Request(Method.DELETE, url, null); }
    }
    public static <T> Cancellation get(String url, Function<Json, T> decoder, Consumer<Result<T>> completion) { return send(Request.get(url), decoder, completion); }
    public static <T> Cancellation send(Request request, Function<Json, T> decoder, Consumer<Result<T>> completion) {
        Objects.requireNonNull(request); Objects.requireNonNull(decoder); Objects.requireNonNull(completion);
        JSObject handle = fetch(request.url(), request.method().name(), request.body() == null ? null : request.body().stringify(), request.timeoutMillis(), request.maxBytes(), (status, kind, body) -> {
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
    @JSBody(params = {"url", "method", "body", "timeout", "limit", "done"}, script = """
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
                headers: body === null ? {Accept: 'application/json'} : {Accept: 'application/json', 'Content-Type': 'application/json'},
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
    private static native JSObject fetch(String url, String method, String body, int timeout, int limit, Completed done);
    @JSBody(params = "handle", script = "handle.cancel();") private static native void cancel(JSObject handle);
}
