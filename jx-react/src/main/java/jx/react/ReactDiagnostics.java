package jx.react;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;

/** Optional diagnostics from the loaded React runtime. These methods do not enable debugging
 * or retain component owners. Call them on the rendering thread, not from JVM code. */
public final class ReactDiagnostics {
    private ReactDiagnostics() {}

    /** Returns React's current development owner stack, or null when no owner stack is available.
     * React omits this export from production builds; JX returns null there. The stack describes
     * component ownership, not the Java call stack. Its text is diagnostic and is not a stable
     * format for application logic. An empty string is distinct from null: React can have an
     * active owner with no printable frames. Its bounded creation stack can truncate deep TeaVM
     * call chains; this adapter preserves the raw result and does not synthesize missing owners. */
    @JSBody(script = "return typeof React.captureOwnerStack === 'function' ? React.captureOwnerStack() : null;",
            imports = @JSBodyImport(alias = "React", fromModule = "react"))
    public static native String captureOwnerStack();
}
