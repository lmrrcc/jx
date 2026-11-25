package jx.conformance;

import jx.Promise;
import jx.View;
import jx.Views;
import jx.conformance.cases.HydratedPage;
import jx.conformance.cases.ServerPage;
import jx.conformance.cases.ServerPostponing;
import jx.conformance.cases.ServerStreaming;
import jx.react.React;
import jx.react.ReactServer;
import jx.react.ReactStatic;
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
    @JSFunctor interface RenderCase extends JSObject { String render(String id); }

    /** True when there is no document, that is, outside a browser. */
    @JSBody(script = "return typeof document === 'undefined';")
    static native boolean active();

    static void install() {
        CombinedServerExports.install();
        SinkCompletionExports.install();
        BootstrapExports.install();
        register("reactVersion", React::version);
        register("domVersion", React::domVersion);
        register("clientVersion", React::clientVersion);
        register("serverVersion", ReactServer::version);
        register("staticVersion", ReactStatic::version);
        registerCase("renderStatic", id -> ReactServer.renderToStaticMarkup(React.strictMode(caseView(Main.component(id)))));
        registerCase("renderCase", id -> ReactServer.renderToString(React.strictMode(caseView(Main.component(id)))));
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
        registerLater("prerender", () -> later(
                ReactStatic.prerender(page(), ReactServer.StreamOptions.defaults()).result().flatMap(ReactStatic.Prerendered::text)));
        registerLater("prerenderToNodeStream", () -> later(
                ReactStatic.prerenderToNodeStream(page(), ReactServer.StreamOptions.defaults()).result().flatMap(ReactStatic.Prerendered::text)));
        registerLater("resume", () -> React.thenable(postponed().flatMap(first -> first.text().flatMap(shell ->
                ReactServer.resume(postponing(), first.postponed(), ReactServer.StreamOptions.defaults())
                        .flatMap(stream -> stream.allReady().flatMap(ready -> stream.text()))
                        .map(rest -> result(shell, first.postponed() != null, rest, false))))));
        registerLater("resumeToPipeableStream", () -> React.thenable(postponed().flatMap(first -> first.text().flatMap(shell -> {
            ReactServer.PipeableStream stream = ReactServer.resumeToPipeableStream(postponing(), first.postponed(), ReactServer.StreamOptions.defaults());
            return stream.allReady().flatMap(ready -> collect(stream)).map(rest -> result(shell, first.postponed() != null, rest, false));
        }))));
        registerLater("resumeAndPrerender", () -> React.thenable(postponed().flatMap(first -> first.text().flatMap(shell ->
                ReactStatic.resumeAndPrerender(postponing(), first.postponed(), ReactServer.StreamOptions.defaults()).result()
                        .flatMap(second -> second.text().map(rest -> result(shell, first.postponed() != null, rest, second.postponed() == null)))))));
        registerLater("resumeAndPrerenderToNodeStream", () -> React.thenable(postponed().flatMap(first -> first.text().flatMap(shell ->
                ReactStatic.resumeAndPrerenderToNodeStream(postponing(), first.postponed(), ReactServer.StreamOptions.defaults()).result()
                        .flatMap(second -> second.text().map(rest -> result(shell, first.postponed() != null, rest, second.postponed() == null)))))));
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

    private static View postponing() { return Views.component(ServerPostponing.JX, null); }

    /** Prerenders the postponing page, aborts while its data is missing, then supplies the data. */
    private static Promise<ReactStatic.Prerendered> postponed() {
        ServerData.current = Promise.create(resolver -> {});
        ReactStatic.Prerendering running = ReactStatic.prerender(postponing(), ReactServer.StreamOptions.defaults().withError(error -> {}));
        delay(running::abort, 20);
        return running.result().map(first -> {
            ServerData.current = Promise.resolved("arrived");
            return first;
        });
    }

    @JSBody(params = {"shell", "postponed", "rest", "finished"}, script = "return {shell: shell, postponed: postponed, rest: rest, finished: finished};")
    private static native JSObject result(String shell, boolean postponed, String rest, boolean finished);

    /** Pipes the stream into one string, settled when the stream ends. */
    private static Promise<String> collect(ReactServer.PipeableStream stream) {
        StringBuilder html = new StringBuilder();
        return stream.pipe(new ReactServer.HtmlSink() {
            @Override public void write(String chunk) { html.append(chunk); }
            @Override public void end() {}
        }).map(ignored -> html.toString());
    }

    /** Any client case as a view, for the hydrate mode of the bench. */
    private static <P> View caseView(jx.ComponentType<P> component) { return Views.component(component, null); }

    private static View page() { return Views.component(ServerPage.JX, new ServerPage.Props("Ada")); }

    /** A JavaScript promise of the JavaScript string, for the test that awaits it. */
    private static JSObject later(Promise<String> html) { return React.thenable(html.map(Server::text)); }

    @JSBody(params = "value", script = "return value;") private static native JSObject text(String value);
    @JSBody(params = {"task", "milliseconds"}, script = "setTimeout(function() { task(); }, milliseconds);") private static native void delay(Task task, int milliseconds);
    @JSBody(params = {"name", "render"}, script = "(globalThis.__jxBenchServer = globalThis.__jxBenchServer || {})[name] = render;")
    private static native void registerCase(String name, RenderCase render);
    @JSBody(params = {"name", "render"}, script = "(globalThis.__jxBenchServer = globalThis.__jxBenchServer || {})[name] = render;")
    private static native void register(String name, Render render);
    @JSBody(params = {"name", "render"}, script = "(globalThis.__jxBenchServer = globalThis.__jxBenchServer || {})[name] = render;")
    private static native void registerLater(String name, RenderLater render);
}
