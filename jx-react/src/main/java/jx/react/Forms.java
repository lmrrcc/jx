package jx.react;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jx.FormAction;
import jx.FormData;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Form actions and form data at the React boundary. */
final class Forms {
    private static final JSObject FUNCTIONS = weakMap();
    private static final JSObject ACTIONS = weakMap();
    private Forms() {}

    @JSFunctor interface Submit extends JSObject { JSObject submit(JSObject data); }

    /** The browser's FormData; file entries read as absent. */
    record BrowserFormData(JSObject data) implements FormData {
        @Override public String get(String name) { return first(data, Objects.requireNonNull(name)); }
        @Override public List<String> getAll(String name) {
            String[] values = all(data, Objects.requireNonNull(name));
            List<String> result = new ArrayList<>(values.length);
            for (String value : values) result.add(value);
            return List.copyOf(result);
        }
        @Override public boolean has(String name) { return hasNative(data, Objects.requireNonNull(name)); }
    }

    /** The dispatcher React returned from {@code useActionState}. React must receive this very
     * function as the form action, so it is passed through instead of being wrapped. */
    record NativeAction(JSObject function) implements FormAction {
        @Override public void run(FormData data) {
            if (data != null && !(data instanceof BrowserFormData)) throw new IllegalArgumentException("This action needs the form data of a browser submission");
            call(function, data == null ? null : ((BrowserFormData) data).data());
        }
    }

    /** The JavaScript value for an {@code action} or {@code formAction} property. */
    static JSObject function(FormAction action) {
        if (action instanceof NativeAction nativeAction) return nativeAction.function();
        JSObject existing = lookupFunction(FUNCTIONS, action);
        if (existing != null) return existing;
        JSObject result = function(data -> {
            FormData fields = new BrowserFormData(data);
            if (action instanceof FormAction.OfPromise async) return AsyncBridge.thenable(async.start(fields));
            action.run(fields);
            return null;
        });
        remember(FUNCTIONS, ACTIONS, action, result);
        return result;
    }

    /** One Java handle per native action. These weak associations belong to one
     * TeaVM generation and store functions, not submission payloads. A retained
     * application function still owns whatever data its closure captures.
     */
    static FormAction action(JSObject function) {
        if (function == null) return null;
        Object existing = lookupAction(ACTIONS, function);
        if (existing instanceof FormAction action) return action;
        if (existing != null) throw new IllegalStateException("Invalid form action mapping");
        FormAction action = new NativeAction(function);
        remember(FUNCTIONS, ACTIONS, action, function);
        return action;
    }

    @JSBody(script = "return new WeakMap();") private static native JSObject weakMap();
    @JSBody(params = {"functions", "action"}, script = "return functions.get(action) || null;")
    private static native JSObject lookupFunction(JSObject functions, Object action);
    @JSBody(params = {"actions", "nativeFunction"}, script = "return actions.get(nativeFunction) || null;")
    private static native Object lookupAction(JSObject actions, JSObject function);
    @JSBody(params = {"functions", "actions", "action", "nativeFunction"}, script = "functions.set(action, nativeFunction); actions.set(nativeFunction, action);")
    private static native void remember(JSObject functions, JSObject actions, Object action, JSObject function);

    @JSBody(params = "submit", script = "return function(data) { var result = submit(data); return result === null ? undefined : result; };") private static native JSObject function(Submit submit);
    @JSBody(params = {"action", "data"}, script = "action(data === null ? undefined : data);") private static native void call(JSObject action, JSObject data);
    @JSBody(params = {"data", "name"}, script = "var value = data.get(name); return typeof value === 'string' ? value : null;") private static native String first(JSObject data, String name);
    @JSBody(params = {"data", "name"}, script = "return data.getAll(name).filter(function(value) { return typeof value === 'string'; });") private static native String[] all(JSObject data, String name);
    @JSBody(params = {"data", "name"}, script = "return data.has(name);") private static native boolean hasNative(JSObject data, String name);
}
