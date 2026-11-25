package jx.fixture.rsc.server;

import jx.Views;
import jx.ComponentType;
import jx.Promise;
import jx.fixture.rsc.CounterProps;
import jx.react.React;
import jx.react.ReactServerComponents;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Framework-owned host boundary; the component itself is ordinary Java/JX. */
public final class ServerEntry {
    private ServerEntry() {}
    @JSFunctor interface Render extends JSObject { JSObject render(String request, JSObject counter, JSObject data); }
    @JSFunctor interface CacheRender extends JSObject { JSObject render(JSObject data, Action ready); }
    @JSFunctor interface Snapshot extends JSObject { JSObject get(); }
    @JSFunctor interface Action extends JSObject { void run(); }
    @JSFunctor interface Outside extends JSObject { boolean check(); }
    public static void main(String[] arguments) {
        register((request, counter, data) -> {
            ComponentType<CounterProps> component = counter == null ? null : React.binding("FlightCounter", counter,
                    props -> encode(props.initial(), props.label()));
            Promise<String> result = data == null ? null : React.promise(data).map(ServerEntry::text);
            return ReactServerComponents.element(Views.component(ServerPage.JX, new ServerPage.Props(request, component, result)));
        }, (data, ready) -> {
            var audit = new CacheProbe.Audit(ready::run);
            var props = new CacheProbe.Props(audit, React.promise(data).map(ServerEntry::text));
            return cacheHandle(ReactServerComponents.element(Views.component(CacheProbe.JX, props)),
                    audit::snapshot, audit::lateListener, audit::close);
        }, CacheProbe::outside);
    }
    @JSBody(params = {"render", "cache", "outside"}, script = "globalThis.__jxRscProof = {render, cache, outside};")
    private static native void register(Render render, CacheRender cache, Outside outside);
    @JSBody(params = {"element", "snapshot", "late", "close"}, script = "return {element, snapshot, late, close};")
    private static native JSObject cacheHandle(JSObject element, Snapshot snapshot, Action late, Action close);
    @JSBody(params = {"initial", "label"}, script = "return {initial: initial, label: label};")
    private static native JSObject encode(int initial, String label);
    @JSBody(params = "value", script = "if (typeof value !== 'string' || !/^[a-z]{1,32}$/.test(value)) throw new Error('Invalid proof data'); return value;")
    private static native String text(JSObject value);
}
