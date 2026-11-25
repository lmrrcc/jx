package jx.react;

import jx.Hooks;
import jx.View;
import java.util.Objects;
import org.teavm.jso.JSObject;

/** Entry boundary for Java server components compiled for a React server host.
 *
 * <p>The host must resolve React with its {@code react-server} condition, own the
 * official Flight transport and client-reference manifest, and pass the returned
 * element to that transport. This API does not start a server, serialize arbitrary
 * Java objects, or make client hooks or JVM APIs available on the server.
 * Calls belong to the server runtime's single JavaScript event loop. React owns
 * component invocation, suspension and request-scoped dispatcher state.
 */
public final class ReactServerComponents {
    private ReactServerComponents() {}

    /** Creates a native React element from a non-null Java view without rendering it.
     * Server-compatible hooks, including {@code Hooks.use(Promise)}, run when React
     * invokes the component. Rendering failures propagate through the host's Flight
     * error handling; this method does not catch or turn them into successful views.
     */
    public static JSObject element(View view) {
        Objects.requireNonNull(view, "view");
        Hooks.installRuntime(ReactHooks.INSTANCE);
        return React.element(view);
    }
}
