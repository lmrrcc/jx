package jx.fixture.rsc.client;

import jx.fixture.rsc.CounterProps;
import jx.react.React;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/** Framework-owned typed decoder for the registered client component. */
public final class ClientEntry {
    private ClientEntry() {}
    public static void main(String[] arguments) {
        register(React.exportComponent(Counter.JX, props -> new Counter.Props(new CounterProps(initial(props), label(props)))));
    }
    @JSBody(params = "counter", script = "globalThis.__jxRscClient = {Counter: counter};")
    private static native void register(JSObject counter);
    @JSBody(params = "props", script = "if (!Number.isInteger(props.initial) || props.initial < 0 || props.initial > 100) throw new Error('Invalid counter initial value'); return props.initial;")
    private static native int initial(JSObject props);
    @JSBody(params = "props", script = "if (typeof props.label !== 'string') throw new Error('Invalid counter label'); return props.label;")
    private static native String label(JSObject props);
}
