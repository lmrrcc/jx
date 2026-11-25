package jx.conformance.cases;

import jx.ComponentType;
import jx.View;
import jx.Views;
import jx.react.React;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;
import org.teavm.jso.JSObject;

/** Test-only native-ref observation. It never mutates state or replaces React scheduling. */
final class ClassStateObserver {
    private ClassStateObserver() {}
    private static final ComponentType<View> OBSERVER = React.binding("ClassStateObserver", observer(),
            view -> props(React.element(view)));
    static View observe(View view) { return Views.component(OBSERVER, view); }
    @JSBody(params = "element", script = "return {element: element};")
    private static native JSObject props(JSObject element);
    @JSBody(script = """
        return function ClassStateObserver(props) {
            var element = props.element, original = element.props.ref, id = element.props.id;
            var ref = React.useCallback(function(instance) {
                var owners = globalThis.__nullableClassOwners || (globalThis.__nullableClassOwners = new Map());
                if (instance === null) owners.delete(id); else owners.set(id, instance);
                if (original) original(instance);
            }, [original, id]);
            return React.cloneElement(element, {ref: ref});
        };
        """, imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject observer();
}
