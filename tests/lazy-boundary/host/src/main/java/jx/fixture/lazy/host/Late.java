package jx.fixture.lazy.host;

import java.util.function.Consumer;
import jx.LazyComponent;
import jx.fixture.lazy.Greeting;
import jx.react.LazyModules;
import jx.react.React;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** The host's side of the boundary: the lazily loaded component as a typed JX component. */
final class Late {
    private Late() {}

    /** What the host passes; encoded into a plain JavaScript object at the boundary. */
    record Props(Greeting greeting, Consumer<String> onPick, boolean explode) {}

    @JSFunctor interface Pick extends JSObject { void pick(String value); }

    /** Loaded from {@code late.js} when it first renders. */
    static final LazyComponent<Props> COMPONENT = LazyComponent.of("Late", () ->
            LazyModules.load("./late.js").map(module -> React.binding("Late", LazyModules.call(module, "greeting"), Late::encode)));

    private static JSObject encode(Props props) {
        Greeting greeting = props.greeting();
        Consumer<String> onPick = props.onPick();
        // A 64-bit integer does not fit a JavaScript number, so it crosses as text.
        return object(greeting.name(), greeting.count(), greeting.loud(), Long.toString(greeting.serial()), onPick::accept, props.explode());
    }

    @JSBody(params = {"name", "count", "loud", "serial", "onPick", "explode"},
            script = "return {name: name, count: count, loud: loud, serial: serial, onPick: onPick, explode: explode};")
    private static native JSObject object(String name, int count, boolean loud, String serial, Pick onPick, boolean explode);
}
