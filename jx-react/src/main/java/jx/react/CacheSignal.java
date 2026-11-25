package jx.react;

import java.util.Objects;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Read-only lifetime of a React server cache. React owns cancellation; consumers
 * can observe it and cancel their own work, but cannot abort another render.
 * All methods and listeners run on the server runtime's JavaScript event loop.
 */
public final class CacheSignal {
    private final JSObject signal;
    CacheSignal(JSObject signal) { this.signal = Objects.requireNonNull(signal); }

    /** True once React has finished, failed or aborted the scope. */
    public boolean aborted() { return aborted(signal); }

    /** Registers a one-shot notification. An already-ended scope invokes the
     * listener synchronously before returning. The returned idempotent cleanup
     * removes a pending listener and releases the Java callback; call it when the
     * observing operation ends before the scope. Exceptions are not swallowed.
     */
    public Runnable onAbort(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        Cleanup cleanup = subscribe(signal, listener::run);
        return cleanup::run;
    }

    /** The original standard AbortSignal for explicit framework/JS interoperability,
     * for example a host fetch operation. No clone or abort controller is created.
     */
    public JSObject nativeSignal() { return signal; }

    @JSFunctor interface Cleanup extends JSObject { void run(); }
    @JSBody(params = "signal", script = "return signal.aborted;") private static native boolean aborted(JSObject signal);
    @JSBody(params = {"signal", "listener"}, script = """
            function notify() {
                var current = listener;
                listener = null;
                if (current) current();
            }
            if (signal.aborted) notify();
            else signal.addEventListener('abort', notify, {once: true});
            return function() {
                listener = null;
                signal.removeEventListener('abort', notify);
            };
            """)
    private static native Cleanup subscribe(JSObject signal, Cleanup listener);
}
