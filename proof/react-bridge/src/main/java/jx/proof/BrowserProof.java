package jx.proof;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Feasibility gate: real Java callbacks execute synchronously inside React. */
public final class BrowserProof {
    @JSFunctor public interface Render extends JSObject { JSObject render(JSObject props); }
    @JSFunctor public interface Click extends JSObject { void click(JSObject event); }
    @JSFunctor public interface Update extends JSObject { Object update(Object previous); }
    @JSFunctor public interface Cleanup extends JSObject { void clean(); }
    @JSFunctor public interface Effect extends JSObject { Cleanup setup(); }

    public record CounterProps(int initial, String label) {}
    private static final Render COUNTER = BrowserProof::counter;
    private static final Render APP = ignored -> {
        JSObject state = state(false);
        boolean hidden = (Boolean) value(state);
        return element("main", null, new Object[] {
                element("h1", null, new Object[] { text("Java / React proof") }),
                hidden ? null : component(COUNTER, new CounterProps(0, "First")),
                component(COUNTER, new CounterProps(10, "Second")),
                button("toggle", "Toggle first", e -> dispatch(setter(state), old -> !(Boolean) old))
        });
    };

    public static void main(String[] args) {
        mount(APP);
    }

    private static JSObject counter(JSObject props) {
        CounterProps input = (CounterProps) read(props);
        JSObject pair = state(input.initial());
        int snapshot = (Integer) value(pair);
        effect(() -> {
            report("setup", input.label(), snapshot);
            return () -> report("cleanup", input.label(), snapshot);
        }, new Object[] { number(snapshot) });
        return button(input.label(), input.label() + ": " + snapshot, event -> {
            dispatch(setter(pair), previous -> (Integer) previous + 1);
            dispatch(setter(pair), previous -> (Integer) previous + 1);
            dispatch(setter(pair), previous -> (Integer) previous + 1);
            report("snapshot", input.label(), snapshot);
        });
    }

    @JSBody(params = "initial", script = "return React.useState(function() { return initial; });", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject state(Object initial);

    @JSBody(params = "pair", script = "return pair[0];")
    private static native Object value(JSObject pair);

    @JSBody(params = "pair", script = "return pair[1];")
    private static native JSObject setter(JSObject pair);

    @JSBody(params = "value", script = "return value;")
    private static native Object text(String value);

    @JSBody(params = "value", script = "return value;")
    private static native Object number(int value);

    @JSBody(params = { "setter", "updater" }, script = "setter(updater);")
    private static native void dispatch(JSObject setter, Update updater);

    @JSBody(params = { "effect", "deps" }, script = "React.useEffect(effect, deps);", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native void effect(Effect effect, Object[] deps);

    @JSBody(params = { "tag", "props", "children" }, script = "return React.createElement.apply(null, [tag, props].concat(children));", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject element(String tag, JSObject props, Object[] children);

    @JSBody(params = { "render", "props" }, script = "return React.createElement(render, {value: props});", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject component(Render render, Object props);

    @JSBody(params = "props", script = "return props.value;")
    private static native Object read(JSObject props);

    @JSBody(params = { "id", "text", "click" }, script = "return React.createElement('button', {id: id, onClick: click}, text);", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject button(String id, String text, Click click);

    @JSBody(params = { "kind", "label", "value" }, script = "if (!globalThis.proofEvents) globalThis.proofEvents = []; globalThis.proofEvents.push({kind: kind, label: label, value: value});")
    private static native void report(String kind, String label, int value);

    private static void mount(Render app) { render(root(), app); }

    @JSBody(script = "return ReactDOM.createRoot(document.getElementById('root'));",
            imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom/client"))
    private static native JSObject root();

    @JSBody(params = { "root", "app" }, script = "root.render(React.createElement(React.StrictMode, null, React.createElement(app)));",
            imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native void render(JSObject root, Render app);
}
