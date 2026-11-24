package jx.fixture.lazy.module;

import jx.fixture.lazy.Greeting;
import jx.react.React;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** The application that is loaded on demand. It mounts nothing: it publishes one component. */
public final class LateModule {
    private LateModule() {}

    public static void main(String[] arguments) {}

    @JSFunctor interface Pick extends JSObject { void pick(String value); }

    /** The exported component. Its props arrive as a plain JavaScript object and are decoded
     * into this application's own {@link Greeting}; the callback is a JavaScript function. */
    @JSExport
    public static JSObject greeting() {
        return React.exportComponent(LateGreeting.JX, props -> new LateGreeting.Props(
                new Greeting(text(props, "name"), integer(props, "count"), flag(props, "loud"), Long.parseLong(text(props, "serial"))),
                callback(props, "onPick")::pick, flag(props, "explode")));
    }

    @JSBody(params = {"object", "name"}, script = "var value = object[name]; if (typeof value !== 'string') throw new TypeError('Expected a string: ' + name); return value;")
    private static native String text(JSObject object, String name);
    @JSBody(params = {"object", "name"}, script = "var value = object[name]; if (typeof value !== 'number' || (value | 0) !== value) throw new TypeError('Expected an integer: ' + name); return value;")
    private static native int integer(JSObject object, String name);
    @JSBody(params = {"object", "name"}, script = "var value = object[name]; if (typeof value !== 'boolean') throw new TypeError('Expected a boolean: ' + name); return value;")
    private static native boolean flag(JSObject object, String name);
    @JSBody(params = {"object", "name"}, script = "var value = object[name]; if (typeof value !== 'function') throw new TypeError('Expected a function: ' + name); return value;")
    private static native Pick callback(JSObject object, String name);
}
