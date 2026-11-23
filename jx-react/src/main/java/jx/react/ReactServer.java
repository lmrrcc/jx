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
    public record StreamOptions(String identifierPrefix, List<String> bootstrapScripts, List<String> bootstrapModules, Consumer<Throwable> onError) {
        public StreamOptions {
            bootstrapScripts = bootstrapScripts == null ? null : List.copyOf(bootstrapScripts);
            bootstrapModules = bootstrapModules == null ? null : List.copyOf(bootstrapModules);
        }
        public static StreamOptions defaults() { return new StreamOptions(null, null, null, null); }
        public StreamOptions withIdentifierPrefix(String prefix) { return new StreamOptions(prefix, bootstrapScripts, bootstrapModules, onError); }
        public StreamOptions withBootstrapScripts(List<String> urls) { return new StreamOptions(identifierPrefix, urls, bootstrapModules, onError); }
        public StreamOptions withBootstrapModules(List<String> urls) { return new StreamOptions(identifierPrefix, bootstrapScripts, urls, onError); }
        public StreamOptions withError(Consumer<Throwable> callback) { return new StreamOptions(identifierPrefix, bootstrapScripts, bootstrapModules, callback); }
    }

    /** Receives streamed HTML in order. {@code end} is called once, after the last chunk. */
    public interface HtmlSink {
        void write(String html);
        void end();
    }

    /** The result of {@link #renderToPipeableStream}: a render that is still running. */
    public interface PipeableStream {
        /** Settled when everything outside {@code React.suspense} boundaries is rendered; it fails
         * when that part threw, and then nothing can be sent. */
        Promise<Void> shellReady();
        /** Settled when every boundary has its content, for crawlers and static output. */
        Promise<Void> allReady();
        /** Starts sending: what is ready now, then each boundary's content as it arrives. Call it
         * once, after {@link #shellReady()} or {@link #allReady()}. */
        void pipe(HtmlSink sink);
        /** As {@link #pipe(HtmlSink)}, to a Node {@code Writable} such as an HTTP response. */
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
        Hooks.installRuntime(ReactHooks.INSTANCE);
        JSObject settings = settings(Objects.requireNonNull(options));
        Signal shell = new Signal();
        Signal all = new Signal();
        setCall(settings, "onShellReady", () -> shell.resolver.resolve(null));
        setError(settings, "onShellError", error -> shell.resolver.reject(React.throwable(error)));
        setCall(settings, "onAllReady", () -> all.resolver.resolve(null));
        JSObject stream = pipeableNative(React.element(Objects.requireNonNull(view)), settings);
        return new PipeableStream() {
            @Override public Promise<Void> shellReady() { return shell.promise; }
            @Override public Promise<Void> allReady() { return all.promise; }
            @Override public void pipe(HtmlSink sink) { Objects.requireNonNull(sink); pipeSink(stream, sink::write, sink::end); }
            @Override public void pipe(JSObject writable) { pipeNative(stream, Objects.requireNonNull(writable)); }
            @Override public void abort() { abortNative(stream); }
        };
    }

    /** React's {@code renderToReadableStream}: streaming rendering to a web stream. The promise is
     * settled when the shell is rendered and fails when the shell threw. */
    public static Promise<ReadableStream> renderToReadableStream(View view, StreamOptions options) {
        Hooks.installRuntime(ReactHooks.INSTANCE);
        JSObject settings = settings(Objects.requireNonNull(options));
        JSObject controller = controller(settings);
        return React.promise(readableNative(React.element(Objects.requireNonNull(view)), settings)).map(stream -> new ReadableStream() {
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
        Consumer<Throwable> onError = options.onError();
        if (onError != null) setError(settings, "onError", error -> onError.accept(React.throwable(error)));
        return settings;
    }

    @JSBody(script = "return {};") private static native JSObject object();
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
    @JSBody(params = {"stream", "writable"}, script = "stream.pipe(writable);") private static native void pipeNative(JSObject stream, JSObject writable);
    // React writes to anything shaped like a Node Writable; this one decodes the bytes for a Java sink.
    @JSBody(params = {"stream", "write", "end"}, script = "var decoder = new TextDecoder();"
            + " var target = { write: function(chunk) { write(typeof chunk === 'string' ? chunk : decoder.decode(chunk, {stream: true})); return true; },"
            + " end: function() { var tail = decoder.decode(); if (tail) write(tail); end(); },"
            + " on: function() { return target; }, once: function() { return target; }, removeListener: function() { return target; },"
            + " flush: function() {}, destroy: function() {} };"
            + " stream.pipe(target);")
    private static native void pipeSink(JSObject stream, Chunk write, Call end);
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
