package jx.conformance;

import jx.Views;
import jx.conformance.cases.ServerPage;
import jx.react.ReactServer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** The bench application as a Node host: the server cases call these functions without a page.
 * The reference implementation in {@code reference/server.jsx} exports the same names. */
final class Server {
    private Server() {}

    @JSFunctor interface Render extends JSObject { String render(); }

    /** True when there is no document, that is, outside a browser. */
    @JSBody(script = "return typeof document === 'undefined';")
    static native boolean active();

    static void install() {
        register("renderToString", () -> ReactServer.renderToString(page()));
        register("renderToStaticMarkup", () -> ReactServer.renderToStaticMarkup(page()));
    }

    private static jx.View page() { return Views.component(ServerPage.JX, new ServerPage.Props("Ada")); }

    @JSBody(params = {"name", "render"}, script = "(globalThis.__jxBenchServer = globalThis.__jxBenchServer || {})[name] = render;")
    private static native void register(String name, Render render);
}
