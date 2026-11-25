package jx.conformance;

import java.util.ArrayList;
import jx.Attributes;
import jx.ComponentType;
import jx.Promise;
import jx.View;
import jx.Views;
import jx.react.React;
import jx.react.ReactServer;
import jx.react.ReactStatic;
import jx.react.Resources;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Request-owned header snapshots from the actual Java-authored React server tree. */
final class ResourceHeadersExports {
    private ResourceHeadersExports() {}
    private record Props(String origin, boolean hints) {}
    private static final ComponentType<Props> PAGE = new ComponentType<>("ResourceHeaderPage", props -> {
        if (props.hints()) {
            Resources.preconnect(props.origin());
            Resources.preconnect(props.origin());
            Resources.preload(props.origin() + "/font.woff2", new Resources.Options(
                    "font", "", null, null, null, "font/woff2", null));
            Resources.preload(props.origin() + "/hero.png", new Resources.Options(
                    "image", null, null, null, "high", null, null));
        }
        return Views.element("html", Attributes.create(), Views.element("head", Attributes.create()),
                Views.element("body", Attributes.create(), Views.element("h1", Attributes.create(), "headers")));
    });

    @JSFunctor interface Render extends JSObject {
        JSObject render(String kind, String origin, int limit, boolean hints, boolean fail);
    }

    static void install() {
        register((kind, origin, limit, hints, fail) -> React.thenable(render(kind, origin, limit, hints, fail)));
    }

    private static Promise<JSObject> render(String kind, String origin, int limit, boolean hints, boolean fail) {
        var snapshots = new ArrayList<ReactServer.ResourceHeaders>();
        var events = new ArrayList<String>();
        var callbackFailure = new IllegalStateException("header destination refused");
        var options = ReactServer.StreamOptions.defaults().withHeaders(headers -> {
            snapshots.add(headers);
            events.add("headers");
            if (fail) throw callbackFailure;
        }).withError(error -> events.add(error == callbackFailure ? "original-error" : "unexpected-error"));
        if (limit != -1) options = options.withMaxHeadersLength(limit);
        View page = Views.component(PAGE, new Props(origin, hints));
        Promise<String> output = switch (kind) {
            case "readable" -> ReactServer.renderToReadableStream(page, options).flatMap(stream -> {
                events.add("ready");
                return stream.allReady().flatMap(ready -> stream.text());
            });
            case "pipeable" -> {
                var stream = ReactServer.renderToPipeableStream(page, options);
                yield stream.shellReady().flatMap(ready -> {
                    events.add("ready");
                    var html = new StringBuilder();
                    return stream.pipe(new ReactServer.HtmlSink() {
                        @Override public void write(String chunk) { html.append(chunk); }
                        @Override public void end() {}
                    }).map(ignored -> html.toString());
                });
            }
            case "prerender", "prerender-node" -> {
                var pending = kind.equals("prerender") ? ReactStatic.prerender(page, options)
                        : ReactStatic.prerenderToNodeStream(page, options);
                yield pending.result().flatMap(result -> {
                    events.add("ready");
                    return result.text();
                });
            }
            default -> throw new IllegalArgumentException("Unknown renderer: " + kind);
        };
        return output.map(html -> {
            events.add("end");
            String[] links = snapshots.stream().map(ReactServer.ResourceHeaders::link).toArray(String[]::new);
            return result(html, links, events.toArray(String[]::new));
        });
    }

    @JSBody(params = "render", script = "(globalThis.__jxBenchServer = globalThis.__jxBenchServer || {}).resourceHeaders = render;")
    private static native void register(Render render);
    @JSBody(params = {"html", "links", "events"}, script = "return {html: html, links: Array.from(links), events: Array.from(events)};")
    private static native JSObject result(String html, String[] links, String[] events);
}
