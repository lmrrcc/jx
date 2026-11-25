package jx.react;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import jx.Hooks;
import jx.Promise;
import jx.View;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** React's synchronous server rendering: a view to an HTML string. It runs wherever the compiled
 * application runs with React's server renderer available, normally a Node host; it needs no
 * document. Effects do not run, event handlers are not attached, and a component that suspends
 * renders its {@code React.suspense} fallback. Rendering must not depend on state shared between
 * requests: everything a request needs is passed in through props or context. */
public final class ReactServer {
    private ReactServer() {}
    /** The version exported by the loaded {@code react-dom/server} entry point. */
    @JSBody(script = "return ReactDOMServer.version;", imports = @JSBodyImport(alias = "ReactDOMServer", fromModule = "react-dom/server"))
    public static native String version();

    /** React's {@code renderToString}: HTML with the markers hydration needs. */
    public static String renderToString(View view) { return renderToString(view, null); }

    /** As {@link #renderToString(View)}; {@code identifierPrefix} is put in front of every
     * {@link Hooks#useId()} and must equal the prefix of the root that hydrates the HTML. Null
     * means none. */
    public static String renderToString(View view, String identifierPrefix) {
        Hooks.installRuntime(ReactHooks.INSTANCE);
        return stringNative(React.element(Objects.requireNonNull(view)), identifierPrefix);
    }

    /** React's {@code renderToStaticMarkup}: plain HTML without React's markers, for pages that
     * are not hydrated. */
    public static String renderToStaticMarkup(View view) { return renderToStaticMarkup(view, null); }

    /** As {@link #renderToStaticMarkup(View)}, with an identifier prefix; null means none. */
    public static String renderToStaticMarkup(View view, String identifierPrefix) {
        Hooks.installRuntime(ReactHooks.INSTANCE);
        return staticNative(React.element(Objects.requireNonNull(view)), identifierPrefix);
    }

    /** Settings of the streaming renderers; a null component is left out. {@code bootstrapScripts}
     * and {@code bootstrapModules} are script URLs React adds to the page to start the browser
     * application. {@code onError} hears of every error thrown while rendering, also the ones a
     * {@code React.suspense} boundary absorbs by sending its fallback; it runs outside rendering. */
    public record StreamOptions(String identifierPrefix, List<String> bootstrapScripts, List<String> bootstrapModules,
            Consumer<Throwable> onError, String bootstrapScriptContent, Nonce nonce) {
        public StreamOptions {
            bootstrapScripts = bootstrapScripts == null ? null : List.copyOf(bootstrapScripts);
            bootstrapModules = bootstrapModules == null ? null : List.copyOf(bootstrapModules);
        }
        /** Retains the original constructor; inline bootstrap and CSP nonces are absent. */
        public StreamOptions(String identifierPrefix, List<String> bootstrapScripts, List<String> bootstrapModules,
                Consumer<Throwable> onError) {
            this(identifierPrefix, bootstrapScripts, bootstrapModules, onError, null, null);
        }
        public static StreamOptions defaults() { return new StreamOptions(null, null, null, null); }
        public StreamOptions withIdentifierPrefix(String prefix) { return new StreamOptions(prefix, bootstrapScripts, bootstrapModules, onError, bootstrapScriptContent, nonce); }
        public StreamOptions withBootstrapScripts(List<String> urls) { return new StreamOptions(identifierPrefix, urls, bootstrapModules, onError, bootstrapScriptContent, nonce); }
        public StreamOptions withBootstrapModules(List<String> urls) { return new StreamOptions(identifierPrefix, bootstrapScripts, urls, onError, bootstrapScriptContent, nonce); }
        public StreamOptions withError(Consumer<Throwable> callback) { return new StreamOptions(identifierPrefix, bootstrapScripts, bootstrapModules, callback, bootstrapScriptContent, nonce); }
        /** Trusted JavaScript source emitted by React in an inline bootstrap script. Null omits it;
         * an empty string is retained. React escapes HTML script terminators, but does not sanitize
         * executable code or serialize application data: never concatenate untrusted input here.
         * Set this on the initial render/prerender; resume uses the postponed bootstrap state. */
        public StreamOptions withBootstrapScriptContent(String source) {
            return new StreamOptions(identifierPrefix, bootstrapScripts, bootstrapModules, onError, source, nonce);
        }
        /** React's string nonce contract: scripts only, without a style nonce. Null clears both.
         * The host must generate a fresh unpredictable nonce per response and send a matching CSP
         * header. JX does not generate nonces or configure headers. Not valid for static prerendering. */
        public StreamOptions withNonce(String value) {
            return new StreamOptions(identifierPrefix, bootstrapScripts, bootstrapModules, onError,
                    bootstrapScriptContent, value == null ? null : new ScriptNonce(value));
        }
        /** Separate script/style nonces for request-time streaming and resume. Null clears both.
         * These values authorize React-managed resources; they do not change explicit element props. */
        public StreamOptions withNonces(Nonces value) {
            return new StreamOptions(identifierPrefix, bootstrapScripts, bootstrapModules, onError, bootstrapScriptContent, value);
        }
    }

    /** React's documented string or script/style object option. The variants retain their native
     * representation rather than assuming React treats the two shapes identically. */
    public sealed interface Nonce permits ScriptNonce, Nonces {}

    /** A string nonce authorizing scripts only. Empty is passed unchanged; null is invalid. */
    public record ScriptNonce(String value) implements Nonce {
        public ScriptNonce { Objects.requireNonNull(value); }
    }

    /** Per-response CSP values. A null field is omitted, an empty string is passed unchanged.
     * Both-null is an error; use null StreamOptions.nonce to omit the option entirely.
     * Validation of entropy, lifetime and CSP syntax belongs to the HTTP host. */
    public record Nonces(String script, String style) implements Nonce {
        public Nonces {
            if (script == null && style == null) throw new IllegalArgumentException("At least one CSP nonce is required");
        }
    }

    /** Receives decoded HTML synchronously on the Node event loop. The sink owns any storage it
     * retains; this adapter does not buffer the document or provide asynchronous backpressure.
     * Use the native Writable overload for an HTTP response or asynchronous destination.
     * {@code end} is called once after the last successful write. A thrown write/end failure
     * rejects the delivery promise, preserving an original Java throwable, and stops callbacks.
     * End does not run after a failed write. */
    public interface HtmlSink {
        void write(String html);
        void end();
    }

    /** The result of {@link #renderToPipeableStream}: a render that is still running. */
    public interface PipeableStream {
        /** Settled when everything outside {@code React.suspense} boundaries is rendered; it fails
         * when that part threw, and then nothing can be sent. */
        Promise<Void> shellReady();
        /** Settled when React finishes its server work, for crawlers and static output.
         * A boundary abandoned after an error or abort may still contain its fallback;
         * readiness does not guarantee that every boundary produced content.
         * Rejects when the shell fails, since no output can be produced. */
        Promise<Void> allReady();
        /** Starts sending: what is ready now, then each boundary's content as it arrives. Call it
         * once, after {@link #shellReady()} or {@link #allReady()}. The non-null result resolves
         * only after {@link HtmlSink#end()} returns; it rejects on a write/end failure, fatal
         * renderer destruction or a repeated pipe call. Observe this result to distinguish
         * delivery from renderer readiness. Aborting can successfully deliver fallback HTML;
         * successful delivery does not guarantee that every boundary rendered its content. */
        Promise<Void> pipe(HtmlSink sink);
        /** Starts sending to a borrowed Node {@code Writable}, such as an HTTP response. The host
         * owns completion/error/close observation, backpressure and disposal of that Writable. */
        void pipe(JSObject writable);
        /** Stops rendering; boundaries still waiting send their fallback for the browser to finish. */
        void abort();
    }

    /** The result of {@link #renderToReadableStream}: the shell is rendered and the stream is open. */
    public interface ReadableStream {
        /** The web {@code ReadableStream} of HTML bytes, for a {@code Response}. */
        JSObject stream();
        /** Settled when every boundary has its content. */
        Promise<Void> allReady();
        /** Reads the stream to its end as text. Use either this or {@link #stream()}, not both. */
        Promise<String> text();
        /** Stops rendering, as {@link PipeableStream#abort()}. */
        void abort();
    }

    /** React's {@code renderToPipeableStream}: streaming rendering for a Node host. Content under
     * a {@code React.suspense} boundary that is not ready is sent later, after its fallback. */
    public static PipeableStream renderToPipeableStream(View view, StreamOptions options) {
        return pipeable(view, options, null);
    }

    /** React's {@code resumeToPipeableStream}: continues a prerender that was aborted, sending
     * what {@code postponed} left out. {@code view} must be the view that was prerendered. */
    public static PipeableStream resumeToPipeableStream(View view, ReactStatic.Postponed postponed, StreamOptions options) {
        return pipeable(view, options, Objects.requireNonNull(postponed));
    }

    /** React's {@code resume}: as {@link #resumeToPipeableStream}, to a web stream. */
    public static Promise<ReadableStream> resume(View view, ReactStatic.Postponed postponed, StreamOptions options) {
        return readable(view, options, Objects.requireNonNull(postponed));
    }

    private static PipeableStream pipeable(View view, StreamOptions options, ReactStatic.Postponed postponed) {
        Hooks.installRuntime(ReactHooks.INSTANCE);
        JSObject settings = settings(Objects.requireNonNull(options));
        Signal shell = new Signal();
        Signal all = new Signal();
        setCall(settings, "onShellReady", () -> shell.resolver.resolve(null));
        setError(settings, "onShellError", error -> {
            Throwable failure = React.throwable(error);
            shell.resolver.reject(failure);
            all.resolver.reject(failure);
        });
        setCall(settings, "onAllReady", () -> all.resolver.resolve(null));
        JSObject element = React.element(Objects.requireNonNull(view));
        JSObject stream = postponed == null ? pipeableNative(element, settings) : resumePipeableNative(element, postponed.state, settings);
        return new PipeableStream() {
            @Override public Promise<Void> shellReady() { return shell.promise; }
            @Override public Promise<Void> allReady() { return all.promise; }
            @Override public Promise<Void> pipe(HtmlSink sink) {
                Objects.requireNonNull(sink);
                return React.promise(pipeSink(stream, sink::write, sink::end)).map(ignored -> null);
            }
            @Override public void pipe(JSObject writable) { pipeNative(stream, Objects.requireNonNull(writable)); }
            @Override public void abort() { abortNative(stream); }
        };
    }

    /** React's {@code renderToReadableStream}: streaming rendering to a web stream. The promise is
     * settled when the shell is rendered and fails when the shell threw. */
    public static Promise<ReadableStream> renderToReadableStream(View view, StreamOptions options) {
        return readable(view, options, null);
    }

    private static Promise<ReadableStream> readable(View view, StreamOptions options, ReactStatic.Postponed postponed) {
        Hooks.installRuntime(ReactHooks.INSTANCE);
        JSObject settings = settings(Objects.requireNonNull(options));
        JSObject controller = controller(settings);
        JSObject element = React.element(Objects.requireNonNull(view));
        return React.promise(postponed == null ? readableNative(element, settings) : resumeNative(element, postponed.state, settings)).map(stream -> new ReadableStream() {
            @Override public JSObject stream() { return stream; }
            @Override public Promise<Void> allReady() { return React.promise(allReadyNative(stream)).map(ignored -> null); }
            @Override public Promise<String> text() { return React.promise(readNative(stream)).map(ReactServer::string); }
            @Override public void abort() { abortNative(controller); }
        });
    }

    private static final class Signal {
        Promise.Resolver<Void> resolver;
        final Promise<Void> promise = Promise.create(created -> resolver = created);
    }

    @JSFunctor interface Call extends JSObject { void call(); }
    @JSFunctor interface Failure extends JSObject { void fail(JSObject error); }
    @JSFunctor interface Chunk extends JSObject { void write(String html); }

    static JSObject settings(StreamOptions options) {
        JSObject settings = object();
        if (options.identifierPrefix() != null) setText(settings, "identifierPrefix", options.identifierPrefix());
        if (options.bootstrapScripts() != null) setList(settings, "bootstrapScripts", options.bootstrapScripts().toArray(String[]::new));
        if (options.bootstrapModules() != null) setList(settings, "bootstrapModules", options.bootstrapModules().toArray(String[]::new));
        if (options.bootstrapScriptContent() != null) setText(settings, "bootstrapScriptContent", options.bootstrapScriptContent());
        switch (options.nonce()) {
            case null -> { }
            case ScriptNonce single -> setText(settings, "nonce", single.value());
            case Nonces separate -> setNonces(settings, separate.script(), separate.style());
        }
        Consumer<Throwable> onError = options.onError();
        if (onError != null) setError(settings, "onError", error -> onError.accept(React.throwable(error)));
        return settings;
    }

    @JSBody(script = "return {};") private static native JSObject object();
    @JSBody(params = {"settings", "script", "style"}, script = "var nonce = {}; if (script !== null) nonce.script = script; if (style !== null) nonce.style = style; settings.nonce = nonce;")
    private static native void setNonces(JSObject settings, String script, String style);
    @JSBody(params = {"object", "name", "value"}, script = "object[name] = value;") private static native void setText(JSObject object, String name, String value);
    @JSBody(params = {"object", "name", "values"}, script = "object[name] = Array.prototype.slice.call(values);") private static native void setList(JSObject object, String name, String[] values);
    @JSBody(params = {"object", "name", "call"}, script = "object[name] = function() { call(); };") private static native void setCall(JSObject object, String name, Call call);
    @JSBody(params = {"object", "name", "fail"}, script = "object[name] = function(error) { fail(error); };") private static native void setError(JSObject object, String name, Failure fail);
    @JSBody(params = "settings", script = "var controller = new AbortController(); settings.signal = controller.signal; return controller;") static native JSObject controller(JSObject settings);
    @JSBody(params = "target", script = "target.abort();") static native void abortNative(JSObject target);
    @JSBody(params = {"element", "settings"}, script = "return ReactDOMServer.renderToPipeableStream(element, settings);", imports = @JSBodyImport(alias = "ReactDOMServer", fromModule = "react-dom/server"))
    private static native JSObject pipeableNative(JSObject element, JSObject settings);
    @JSBody(params = {"element", "settings"}, script = "return ReactDOMServer.renderToReadableStream(element, settings);", imports = @JSBodyImport(alias = "ReactDOMServer", fromModule = "react-dom/server"))
    private static native JSObject readableNative(JSObject element, JSObject settings);
    @JSBody(params = {"element", "state", "settings"}, script = "return ReactDOMServer.resumeToPipeableStream(element, state, settings);", imports = @JSBodyImport(alias = "ReactDOMServer", fromModule = "react-dom/server"))
    private static native JSObject resumePipeableNative(JSObject element, JSObject state, JSObject settings);
    @JSBody(params = {"element", "state", "settings"}, script = "return ReactDOMServer.resume(element, state, settings);", imports = @JSBodyImport(alias = "ReactDOMServer", fromModule = "react-dom/server"))
    private static native JSObject resumeNative(JSObject element, JSObject state, JSObject settings);
    @JSBody(params = {"stream", "writable"}, script = "stream.pipe(writable);") private static native void pipeNative(JSObject stream, JSObject writable);
    // Synchronous destinations never emit drain/error/close themselves. Callback failures are
    // rejected here and rethrown to React so its renderer also stops the affected request.
    @JSBody(params = {"stream", "write", "end"}, script = "return new Promise(function(resolve, reject) {"
            + " var decoder = new TextDecoder(), terminal = false;"
            + " var fail = function(error) { if (!terminal) { terminal = true; reject(error); } };"
            + " var target = { write: function(chunk) { if (terminal) return false;"
            + " try { write(typeof chunk === 'string' ? chunk : decoder.decode(chunk, {stream: true})); return true; }"
            + " catch (error) { fail(error); throw error; } },"
            + " end: function() { if (terminal) return; try { var tail = decoder.decode(); if (tail) write(tail);"
            + " end(); terminal = true; resolve(null); } catch (error) { fail(error); throw error; } },"
            + " on: function() { return target; }, once: function() { return target; }, removeListener: function() { return target; },"
            + " flush: function() {}, destroy: function(error) { fail(error || new Error('HTML stream destroyed before completion')); } };"
            + " try { stream.pipe(target); } catch (error) { fail(error); } });")
    private static native JSObject pipeSink(JSObject stream, Chunk write, Call end);
    @JSBody(params = "stream", script = "return stream.allReady;") private static native JSObject allReadyNative(JSObject stream);
    @JSBody(params = "stream", script = "var reader = stream.getReader(); var decoder = new TextDecoder(); var text = '';"
            + " var next = function() { return reader.read().then(function(part) { if (part.done) return text + decoder.decode(); text += decoder.decode(part.value, {stream: true}); return next(); }); };"
            + " return next();")
    static native JSObject readNative(JSObject stream);
    @JSBody(params = "value", script = "return String(value);") static native String string(JSObject value);

    @JSBody(params = {"element", "prefix"}, script = "return ReactDOMServer.renderToString(element, prefix === null ? undefined : {identifierPrefix: prefix});", imports = @JSBodyImport(alias = "ReactDOMServer", fromModule = "react-dom/server"))
    private static native String stringNative(JSObject element, String prefix);
    @JSBody(params = {"element", "prefix"}, script = "return ReactDOMServer.renderToStaticMarkup(element, prefix === null ? undefined : {identifierPrefix: prefix});", imports = @JSBodyImport(alias = "ReactDOMServer", fromModule = "react-dom/server"))
    private static native String staticNative(JSObject element, String prefix);
}
