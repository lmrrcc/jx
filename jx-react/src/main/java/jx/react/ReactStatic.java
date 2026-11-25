package jx.react;

import java.util.Objects;
import jx.Hooks;
import jx.Promise;
import jx.View;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;
import org.teavm.jso.JSObject;

/** React's static rendering ({@code react-dom/static}): HTML produced ahead of a request, after
 * all data is there. Unlike the streaming renderers it waits for every {@code React.suspense}
 * boundary before the HTML is complete, so the output has no fallbacks unless the work is aborted.
 * It runs in a Node host, like {@link ReactServer}. */
public final class ReactStatic {
    private ReactStatic() {}
    /** The version exported by the loaded {@code react-dom/static} entry point. */
    @JSBody(script = "return ReactDOMStatic.version;", imports = @JSBodyImport(alias = "ReactDOMStatic", fromModule = "react-dom/static"))
    public static native String version();

    /** A prerender that is still running. */
    public interface Prerendering {
        /** Settled when the HTML is complete, or when {@link #abort()} cut the work short. */
        Promise<Prerendered> result();
        /** Stops waiting for data. The result then holds the HTML so far, with fallbacks for the
         * boundaries that were not ready, and the {@linkplain Prerendered#postponed() state} to
         * continue from later. */
        void abort();
    }

    /** Finished static output. */
    public interface Prerendered {
        /** The stream of HTML bytes: a web {@code ReadableStream} from {@link ReactStatic#prerender},
         * a Node {@code Readable} from {@link ReactStatic#prerenderToNodeStream}. */
        JSObject prelude();
        /** Reads the prelude to its end as text. Use either this or {@link #prelude()}, not both. */
        Promise<String> text();
        /** What is left to render after an abort, or null when the HTML is complete. */
        Postponed postponed();
    }

    /** The opaque state of an aborted prerender, to be given back to React to finish the page.
     * {@link #json()} is the form to store between the two steps. */
    public static final class Postponed {
        final JSObject state;
        Postponed(JSObject state) { this.state = state; }
        /** The state as JSON text. */
        public String json() { return stringify(state); }
        /** The state from its JSON text. It must come from {@link #json()}: React trusts it. */
        public static Postponed fromJson(String json) { return new Postponed(parse(Objects.requireNonNull(json))); }
        @JSBody(params = "state", script = "return JSON.stringify(state);") private static native String stringify(JSObject state);
        @JSBody(params = "json", script = "return JSON.parse(json);") private static native JSObject parse(String json);
    }

    /** React's {@code prerender}: static HTML as a web stream. */
    public static Prerendering prerender(View view, ReactServer.StreamOptions options) {
        return start(view, options, false);
    }

    /** React's {@code prerenderToNodeStream}: static HTML as a Node stream. */
    public static Prerendering prerenderToNodeStream(View view, ReactServer.StreamOptions options) {
        return start(view, options, true);
    }

    /** React's {@code resumeAndPrerender}: finishes an aborted prerender as static HTML, a web
     * stream. {@code view} must be the view that was prerendered. */
    public static Prerendering resumeAndPrerender(View view, Postponed postponed, ReactServer.StreamOptions options) {
        return continued(view, postponed, options, false);
    }

    /** React's {@code resumeAndPrerenderToNodeStream}: as {@link #resumeAndPrerender}, a Node stream. */
    public static Prerendering resumeAndPrerenderToNodeStream(View view, Postponed postponed, ReactServer.StreamOptions options) {
        return continued(view, postponed, options, true);
    }

    private static Prerendering continued(View view, Postponed postponed, ReactServer.StreamOptions options, boolean node) {
        Hooks.installRuntime(ReactHooks.INSTANCE);
        JSObject state = Objects.requireNonNull(postponed).state;
        JSObject settings = ReactServer.settings(Objects.requireNonNull(options));
        JSObject controller = ReactServer.controller(settings);
        JSObject element = React.element(Objects.requireNonNull(view));
        return running(node ? resumeNodeNative(element, state, settings) : resumeNative(element, state, settings), controller, node);
    }

    private static Prerendering start(View view, ReactServer.StreamOptions options, boolean node) {
        Hooks.installRuntime(ReactHooks.INSTANCE);
        JSObject settings = ReactServer.settings(Objects.requireNonNull(options));
        JSObject controller = ReactServer.controller(settings);
        JSObject element = React.element(Objects.requireNonNull(view));
        return running(node ? prerenderNodeNative(element, settings) : prerenderNative(element, settings), controller, node);
    }

    /** Wraps React's promise of {@code {prelude, postponed}}. */
    static Prerendering running(JSObject pending, JSObject controller, boolean node) {
        Promise<Prerendered> result = React.promise(pending).map(output -> {
            JSObject prelude = prelude(output);
            JSObject state = postponed(output);
            return new Prerendered() {
                @Override public JSObject prelude() { return prelude; }
                @Override public Promise<String> text() {
                    return React.promise(node ? readNode(prelude) : ReactServer.readNative(prelude)).map(ReactServer::string);
                }
                @Override public Postponed postponed() { return state == null ? null : new Postponed(state); }
            };
        });
        return new Prerendering() {
            @Override public Promise<Prerendered> result() { return result; }
            @Override public void abort() { ReactServer.abortNative(controller); }
        };
    }

    @JSBody(params = "output", script = "return output.prelude;") private static native JSObject prelude(JSObject output);
    @JSBody(params = "output", script = "return output.postponed == null ? null : output.postponed;") private static native JSObject postponed(JSObject output);
    @JSBody(params = "readable", script = "return new Promise(function(resolve, reject) { var decoder = new TextDecoder(); var text = '';"
            + " readable.on('data', function(chunk) { text += typeof chunk === 'string' ? chunk : decoder.decode(chunk, {stream: true}); });"
            + " readable.on('end', function() { resolve(text + decoder.decode()); }); readable.on('error', reject); });")
    private static native JSObject readNode(JSObject readable);
    @JSBody(params = {"element", "state", "settings"}, script = "return ReactDOMStatic.resumeAndPrerender(element, state, settings);", imports = @JSBodyImport(alias = "ReactDOMStatic", fromModule = "react-dom/static"))
    private static native JSObject resumeNative(JSObject element, JSObject state, JSObject settings);
    @JSBody(params = {"element", "state", "settings"}, script = "return ReactDOMStatic.resumeAndPrerenderToNodeStream(element, state, settings);", imports = @JSBodyImport(alias = "ReactDOMStatic", fromModule = "react-dom/static"))
    private static native JSObject resumeNodeNative(JSObject element, JSObject state, JSObject settings);
    @JSBody(params = {"element", "settings"}, script = "return ReactDOMStatic.prerender(element, settings);", imports = @JSBodyImport(alias = "ReactDOMStatic", fromModule = "react-dom/static"))
    private static native JSObject prerenderNative(JSObject element, JSObject settings);
    @JSBody(params = {"element", "settings"}, script = "return ReactDOMStatic.prerenderToNodeStream(element, settings);", imports = @JSBodyImport(alias = "ReactDOMStatic", fromModule = "react-dom/static"))
    private static native JSObject prerenderNodeNative(JSObject element, JSObject settings);
}
