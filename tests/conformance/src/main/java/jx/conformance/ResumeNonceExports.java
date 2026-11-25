package jx.conformance;

import jx.Attributes;
import jx.ComponentType;
import jx.Hooks;
import jx.Promise;
import jx.View;
import jx.Views;
import jx.react.React;
import jx.react.ReactServer;
import jx.react.ReactStatic;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Per-request resume consumer; only trusted React postponed JSON crosses the two render steps. */
final class ResumeNonceExports {
    private ResumeNonceExports() {}
    @JSFunctor interface Render extends JSObject { JSObject render(String kind, String token); }
    @JSFunctor interface Task extends JSObject { void run(); }
    private record Props(Promise<String> data, String styleNonce, Runnable entered) {}
    private static final ComponentType<Props> CHILD = new ComponentType<>("NonceDeferred", props -> {
        // Test observation only: abort after React has actually attempted this boundary.
        props.entered().run();
        String data = Hooks.use(props.data());
        Attributes style = Attributes.create().attr("href", "resume-style").attr("precedence", "app");
        if (props.styleNonce() != null) style = style.attr("nonce", props.styleNonce());
        return Views.fragment(Views.element("style", style, ".resume{color:rgb(12, 34, 56)}"),
                Views.element("p", Attributes.create().attr("class", "resume"), data));
    });
    private static final class Signal {
        Promise.Resolver<Void> resolver;
        final Promise<Void> promise = Promise.create(created -> resolver = created);
    }

    static void install() { register((kind, token) -> React.thenable(render(kind, token))); }

    private static Promise<JSObject> render(String kind, String token) {
        Signal entered = new Signal();
        Promise<String> waiting = Promise.create(resolver -> {});
        var initial = ReactStatic.prerender(page(waiting, null, () -> later(() -> entered.resolver.resolve(null))),
                ReactServer.StreamOptions.defaults().withError(error -> {})
                        .withBootstrapScriptContent("globalThis.__resumeBoot = (globalThis.__resumeBoot || 0) + 1;"));
        return entered.promise.flatMap(ignored -> {
            initial.abort();
            return initial.result();
        }).flatMap(first -> first.text().flatMap(shell -> {
            if (first.postponed() == null) return Promise.rejected(new IllegalStateException("Missing postponed boundary"));
            var state = ReactStatic.Postponed.fromJson(first.postponed().json());
            var options = ReactServer.StreamOptions.defaults()
                    .withNonces(new ReactServer.Nonces(token + "-script", token + "-style"))
                    .withBootstrapScriptContent("throw new Error('resume must not replace the original bootstrap');");
            View next = page(Promise.resolved("arrived"), token + "-style", () -> {});
            Promise<String> output = switch (kind) {
                case "readable" -> ReactServer.resume(next, state, options)
                        .flatMap(stream -> stream.allReady().flatMap(ready -> stream.text()));
                case "pipeable" -> {
                    var stream = ReactServer.resumeToPipeableStream(next, state, options);
                    yield stream.allReady().flatMap(ready -> {
                        StringBuilder html = new StringBuilder();
                        return stream.pipe(new ReactServer.HtmlSink() {
                            @Override public void write(String chunk) { html.append(chunk); }
                            @Override public void end() {}
                        }).map(ignored -> html.toString());
                    });
                }
                default -> throw new IllegalArgumentException("Unknown resume renderer: " + kind);
            };
            return output.map(rest -> result(shell, rest));
        }));
    }

    private static View page(Promise<String> data, String nonce, Runnable entered) {
        return Views.element("html", Attributes.create(), Views.element("head", Attributes.create()),
                Views.element("body", Attributes.create(), Views.element("h1", Attributes.create(), "shell"),
                        React.suspense(Views.element("p", Attributes.create(), "waiting"),
                                Views.component(CHILD, new Props(data, nonce, entered)))));
    }
    @JSBody(params = "task", script = "queueMicrotask(function() { task(); });") private static native void later(Task task);
    @JSBody(params = {"shell", "rest"}, script = "return {shell: shell, rest: rest};")
    private static native JSObject result(String shell, String rest);
    @JSBody(params = "render", script = "(globalThis.__jxBenchServer = globalThis.__jxBenchServer || {}).resumeBootstrap = render;")
    private static native void register(Render render);
}
