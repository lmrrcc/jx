package jx.react;

import java.util.Objects;
import jx.Hooks;
import jx.View;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;
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

    @JSBody(params = {"element", "prefix"}, script = "return ReactDOMServer.renderToString(element, prefix === null ? undefined : {identifierPrefix: prefix});", imports = @JSBodyImport(alias = "ReactDOMServer", fromModule = "react-dom/server"))
    private static native String stringNative(JSObject element, String prefix);
    @JSBody(params = {"element", "prefix"}, script = "return ReactDOMServer.renderToStaticMarkup(element, prefix === null ? undefined : {identifierPrefix: prefix});", imports = @JSBodyImport(alias = "ReactDOMServer", fromModule = "react-dom/server"))
    private static native String staticNative(JSObject element, String prefix);
}
