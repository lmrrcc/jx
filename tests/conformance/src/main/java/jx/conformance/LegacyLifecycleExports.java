package jx.conformance;

import jx.ComponentType;
import jx.Views;
import jx.react.React;
import jx.react.ReactServer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Synchronous fixture boundary that returns actual server HTML and its lifecycle trace. */
final class LegacyLifecycleExports {
    private LegacyLifecycleExports() {}
    @JSFunctor interface Render extends JSObject { JSObject render(String id); }
    static void install() { register(id -> render(Main.component(id))); }
    private static <P> JSObject render(ComponentType<P> component) {
        clear();
        return result(ReactServer.renderToString(React.strictMode(Views.component(component, null))));
    }
    @JSBody(script = "globalThis.__benchLog = [];") private static native void clear();
    @JSBody(params = "html", script = "return {html: html, log: globalThis.__benchLog.slice()};")
    private static native JSObject result(String html);
    @JSBody(params = "render", script = "(globalThis.__jxBenchServer = globalThis.__jxBenchServer || {}).renderLegacy = render;")
    private static native void register(Render render);
}
