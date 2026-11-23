package jx.conformance;

import jx.Promise;
import jx.View;
import jx.Views;
import jx.conformance.cases.HydratedPage;
import jx.conformance.cases.ServerPage;
import jx.conformance.cases.ServerStreaming;
import jx.react.React;
import jx.react.ReactServer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** The bench application as a Node host: the server cases call these functions without a page.
 * The reference implementation in {@code reference/server.jsx} exports the same names. */
final class Server {
    private Server() {}

    @JSFunctor interface Render extends JSObject { String render(); }
    @JSFunctor interface RenderLater extends JSObject { JSObject render(); }
    @JSFunctor interface Task extends JSObject { void run(); }

    /** True when there is no document, that is, outside a browser. */
    @JSBody(script = "return typeof document === 'undefined';")
    static native boolean active();

    static void install() {
        register("renderToString", () -> ReactServer.renderToString(page()));
        register("renderHydratable", () -> ReactServer.renderToString(React.strictMode(Views.component(HydratedPage.JX, null))));
        register("renderToStaticMarkup", () -> ReactServer.renderToStaticMarkup(page()));
        registerLater("renderToReadableStream", () -> later(
                ReactServer.renderToReadableStream(page(), ReactServer.StreamOptions.defaults())
                        .flatMap(stream -> stream.allReady().flatMap(ready -> stream.text()))));
        registerLater("renderToPipeableStream", () -> {
            ReactServer.PipeableStream stream = ReactServer.renderToPipeableStream(page(), ReactServer.StreamOptions.defaults());
            return later(stream.allReady().flatMap(ready -> collect(stream)));
        });
        registerLater("streamSuspense", () -> {
            Promise.Resolver<String>[] release = newResolvers();
            Promise<String> late = Promise.create(resolver -> release[0] = resolver);
            ReactServer.PipeableStream stream = ReactServer.renderToPipeableStream(
                    Views.component(ServerStreaming.JX, new ServerStreaming.Props(late)), ReactServer.StreamOptions.defaults());
            return later(stream.shellReady().flatMap(ready -> {
                Promise<String> html = collect(stream);
                delay(() -> release[0].resolve("arrived"), 20);
                return html;
            }));
        });
    }

    @SuppressWarnings("unchecked")
    private static Promise.Resolver<String>[] newResolvers() { return (Promise.Resolver<String>[]) new Promise.Resolver<?>[1]; }

    /** Pipes the stream into one string, settled when the stream ends. */
    private static Promise<String> collect(ReactServer.PipeableStream stream) {
        StringBuilder html = new StringBuilder();
        return Promise.create(resolver -> stream.pipe(new ReactServer.HtmlSink() {
            @Override public void write(String chunk) { html.append(chunk); }
            @Override public void end() { resolver.resolve(html.toString()); }
        }));
    }

    private static View page() { return Views.component(ServerPage.JX, new ServerPage.Props("Ada")); }

    /** A JavaScript promise of the JavaScript string, for the test that awaits it. */
    private static JSObject later(Promise<String> html) { return React.thenable(html.map(Server::text)); }

    @JSBody(params = "value", script = "return value;") private static native JSObject text(String value);
    @JSBody(params = {"task", "milliseconds"}, script = "setTimeout(function() { task(); }, milliseconds);") private static native void delay(Task task, int milliseconds);
    @JSBody(params = {"name", "render"}, script = "(globalThis.__jxBenchServer = globalThis.__jxBenchServer || {})[name] = render;")
    private static native void register(String name, Render render);
    @JSBody(params = {"name", "render"}, script = "(globalThis.__jxBenchServer = globalThis.__jxBenchServer || {})[name] = render;")
    private static native void registerLater(String name, RenderLater render);
}
