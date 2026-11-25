package jx.react;

import org.teavm.jso.JSClass;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSMethod;

/** Development-protocol attestation exported from the compiled application runtime.
 * The framework reads the exported {@code protocolN} method name without invoking its JavaScript
 * wrapper: TeaVM wrappers may initialize the application entry class. The Java version call itself
 * neither enables hot replacement nor initializes React or application state. */
@JSClass(name = "JxHotRuntime")
public final class HotRuntime {
    private static final int VERSION = 2;
    private HotRuntime() {}

    /** Protocol implemented by this compiled runtime, checked before replacing the active application. */
    @JSExport
    @JSMethod("protocol" + VERSION)
    public static int version() { return VERSION; }
}
