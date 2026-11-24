package jx.conformance;

import jx.ComponentType;
import jx.Dependencies;
import jx.Hooks;
import jx.Views;
import jx.react.React;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSObject;

/** Delayed TeaVM application. The cases and their contexts live entirely in this runtime. */
public final class LazyRoute {
    private LazyRoute() {}

    public static void main(String[] arguments) {}

    /** Exports the selected case without mounting another root or sharing Java objects. */
    @JSExport
    public static JSObject route() {
        ComponentType<?> selected = Main.component(Bench.caseId());
        if (selected == null) throw new IllegalArgumentException("Unknown route case: " + Bench.caseId());
        return export(selected);
    }

    private static <P> JSObject export(ComponentType<P> selected) {
        ComponentType<Void> committed = new ComponentType<>("CommittedBenchRoute", ignored -> {
            Hooks.useLayoutEffect(() -> { ready(true); return () -> ready(false); }, Dependencies.none());
            return Views.component(selected, null);
        });
        return React.exportComponent(committed, ignored -> null);
    }

    @JSBody(params = "value", script = "globalThis.__benchLazyReady = value;")
    private static native void ready(boolean value);
}
