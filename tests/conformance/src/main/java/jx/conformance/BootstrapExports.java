package jx.conformance;

import java.util.List;
import jx.Attributes;
import jx.Promise;
import jx.View;
import jx.Views;
import jx.react.React;
import jx.react.ReactServer;
import jx.react.ReactStatic;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Compiling Java consumer of trusted bootstrap source and per-response script/style nonces. */
final class BootstrapExports {
    private BootstrapExports() {}
    @JSFunctor interface Render extends JSObject { JSObject render(String kind, String mode, String token); }

    static void install() { register((kind, mode, token) -> React.thenable(render(kind, mode, token).map(BootstrapExports::text))); }

    private static Promise<String> render(String kind, String mode, String token) {
        var options = ReactServer.StreamOptions.defaults()
                .withBootstrapScriptContent("globalThis.__bootstrapValue = \"</script><script>globalThis.__injected=1</script>\";")
                .withBootstrapScripts(List.of("/bootstrap-classic.js"))
                .withBootstrapModules(List.of("/bootstrap-module.js"));
        options = switch (mode) {
            case "none" -> options;
            case "script" -> options.withNonce(token + "-script");
            case "style" -> options.withNonces(new ReactServer.Nonces(null, token + "-style"));
            case "split" -> options.withNonces(new ReactServer.Nonces(token + "-script", token + "-style"));
            default -> throw new IllegalArgumentException("Unknown nonce mode: " + mode);
        };
        View page = page(mode.equals("style") || mode.equals("split") ? token + "-style" : null);
        return switch (kind) {
            case "readable" -> ReactServer.renderToReadableStream(page, options)
                    .flatMap(stream -> stream.allReady().flatMap(ready -> stream.text()));
            case "pipeable" -> {
                ReactServer.PipeableStream stream = ReactServer.renderToPipeableStream(page, options);
                yield stream.allReady().flatMap(ready -> {
                    var html = new StringBuilder();
                    return stream.pipe(new ReactServer.HtmlSink() {
                        @Override public void write(String chunk) { html.append(chunk); }
                        @Override public void end() {}
                    }).map(ignored -> html.toString());
                });
            }
            case "prerender" -> ReactStatic.prerender(page, options).result().flatMap(ReactStatic.Prerendered::text);
            case "prerender-node" -> ReactStatic.prerenderToNodeStream(page, options).result().flatMap(ReactStatic.Prerendered::text);
            default -> throw new IllegalArgumentException("Unknown renderer: " + kind);
        };
    }

    private static View page(String styleNonce) {
        Attributes style = Attributes.create().attr("href", "bootstrap-style").attr("precedence", "app");
        if (styleNonce != null) style = style.attr("nonce", styleNonce);
        return Views.element("html", Attributes.create(),
                Views.element("head", Attributes.create(), Views.element("style", style, ".bootstrap-probe{color:rgb(12, 34, 56)}")),
                Views.element("body", Attributes.create(),
                        Views.element("h1", Attributes.create().attr("class", "bootstrap-probe"), "bootstrap")));
    }

    @JSBody(params = "render", script = "(globalThis.__jxBenchServer = globalThis.__jxBenchServer || {}).renderBootstrap = render;")
    private static native void register(Render render);
    @JSBody(params = "value", script = "return value;") private static native JSObject text(String value);
}
