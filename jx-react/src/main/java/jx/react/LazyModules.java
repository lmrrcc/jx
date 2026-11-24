package jx.react;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import jx.Promise;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/** Loads separately built JavaScript modules on demand, for code that should not be in the first
 * download.
 *
 * <p>A lazily loaded JX module is a second compiled application with its own Java runtime. Java
 * objects must not cross between the two: a record, a context or a lambda of one runtime means
 * nothing to the other. What crosses is JavaScript: strings, numbers, booleans, plain objects,
 * functions and React components. The loading side wraps an exported component with
 * {@link React#binding} and encodes its props; the loaded side exports it with
 * {@link React#exportComponent} and decodes them. */
public final class LazyModules {
    private static final Map<String, Promise<JSObject>> LOADS = new HashMap<>();
    private LazyModules() {}

    /** The module at {@code url}, loaded at most once. A relative URL is resolved against the
     * loading script. A failed load is forgotten, so a later call tries again; a load that is
     * running or has succeeded is shared. */
    public static Promise<JSObject> load(String url) {
        Objects.requireNonNull(url);
        Promise<JSObject> running = LOADS.get(url);
        if (running != null) return running;
        Promise<JSObject> started = React.promise(importNative(url));
        LOADS.put(url, started);
        started.whenSettled(module -> {}, error -> { if (LOADS.get(url) == started) LOADS.remove(url); });
        return started;
    }

    /** Calls the exported function {@code name} of a loaded module without arguments and returns
     * its result, for example the component a JX module publishes.
     * @throws IllegalArgumentException when the module has no such function or it returns nothing */
    public static JSObject call(JSObject module, String name) {
        JSObject result = callNative(Objects.requireNonNull(module), Objects.requireNonNull(name));
        if (result == null) throw new IllegalArgumentException("The module has no export function '" + name + "' that returns a value");
        return result;
    }

    @JSBody(params = "url", script = "return import(url);") private static native JSObject importNative(String url);
    @JSBody(params = {"module", "name"}, script = "var value = typeof module[name] === 'function' ? module[name]() : null; return value == null ? null : value;")
    private static native JSObject callNative(JSObject module, String name);
}
