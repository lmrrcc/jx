package jx.conformance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jx.Attributes;
import jx.Promise;
import jx.Views;
import jx.react.ImportMap;
import jx.react.React;
import jx.react.ReactServer;
import jx.react.ReactStatic;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Compiling consumer of trusted module maps, scopes, blocked imports and integrity metadata. */
final class ImportMapExports {
    private ImportMapExports() {}
    @JSFunctor interface Render extends JSObject { JSObject render(String kind, String token, String integrity); }
    static void install() { register((kind, token, integrity) -> React.thenable(render(kind, token, integrity).map(ImportMapExports::text))); }

    private static Promise<String> render(String kind, String token, String integrity) {
        var imports = new LinkedHashMap<String, String>();
        imports.put("shared", "/maps/" + token + "/global.mjs");
        imports.put("pkg/", "/maps/pkg/");
        imports.put("__proto__", "/maps/proto.mjs");
        imports.put("constructor", "/maps/constructor.mjs");
        imports.put("escaped</script><script>globalThis.__injected=1</script>", "/maps/unused.mjs");
        imports.put("blocked", null);
        var map = new ImportMap(imports, Map.of("/scope/", Map.of("shared", "/maps/" + token + "/scoped.mjs")),
                Map.of("/maps/" + token + "/global.mjs", integrity));
        var options = ReactServer.StreamOptions.defaults().withImportMap(map).withBootstrapModules(List.of("/map-entry.mjs"));
        if (!kind.startsWith("prerender")) options = options.withNonce(token);
        var page = Views.element("html", Attributes.create(), Views.element("head", Attributes.create()),
                Views.element("body", Attributes.create(), Views.element("h1", Attributes.create(), "import maps")));
        return switch (kind) {
            case "readable" -> ReactServer.renderToReadableStream(page, options)
                    .flatMap(stream -> stream.allReady().flatMap(ready -> stream.text()));
            case "pipeable" -> {
                var stream = ReactServer.renderToPipeableStream(page, options);
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

    @JSBody(params = "render", script = "(globalThis.__jxBenchServer = globalThis.__jxBenchServer || {}).renderImportMap = render;")
    private static native void register(Render render);
    @JSBody(params = "value", script = "return value;") private static native JSObject text(String value);
}
