package jx.react;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSExceptions;
import org.teavm.jso.JSObject;

/** Java projection of a foreign JavaScript value thrown through a native React class boundary.
 * Java exceptions thrown by compiled Java are delivered as their original Throwable instead.
 *
 * <p>The original native value is retained without cloning or conversion. This can retain its
 * object graph for as long as the exception is retained. Foreign wrapper identity is not
 * guaranteed between render-phase derivation and committed reporting; compare the borrowed
 * native value using JavaScript semantics when identity matters. No primitive/global cache is
 * maintained. The kind explicitly distinguishes null from undefined.
 *
 * <p>Message/stack diagnostics read only own string-valued data properties. Accessors are not
 * invoked and foreign objects are not stringified; inaccessible descriptors are ignored.
 * A proxy may intercept descriptor inspection. A missing native stack stays null, and no Java
 * source mapping is inferred. Use on the renderer's single JavaScript thread only. */
public final class ReactError extends RuntimeException {
    public enum Kind { NULL, UNDEFINED, STRING, NUMBER, BOOLEAN, BIGINT, SYMBOL, OBJECT, FUNCTION }
    private final JSObject nativeValue;
    private final Kind kind;
    private final String nativeStack;

    private ReactError(JSObject value, Kind kind) {
        super(message(value, kind.name()));
        this.nativeValue = value; this.kind = kind; this.nativeStack = ownString(value, "stack");
    }
    /** Borrowed original thrown value. Check {@link #kind} to distinguish null/undefined. */
    public JSObject nativeValue() { return nativeValue; }
    public Kind kind() { return kind; }
    /** Original own string stack, or null when unavailable. */
    public String nativeStack() { return nativeStack; }

    static Throwable fromNative(JSObject value) {
        Throwable java = JSExceptions.getJavaException(value);
        return java != null ? java : new ReactError(value, Kind.valueOf(kind(value)));
    }
    @JSBody(params = "value", script = "return value === null ? 'NULL' : typeof value === 'undefined' ? 'UNDEFINED' : typeof value === 'string' ? 'STRING' : typeof value === 'number' ? 'NUMBER' : typeof value === 'boolean' ? 'BOOLEAN' : typeof value === 'bigint' ? 'BIGINT' : typeof value === 'symbol' ? 'SYMBOL' : typeof value === 'function' ? 'FUNCTION' : 'OBJECT';")
    private static native String kind(JSObject value);
    @JSBody(params = {"value", "field"}, script = """
        if (value === null || (typeof value !== 'object' && typeof value !== 'function')) return null;
        try {
            var property = Object.getOwnPropertyDescriptor(value, field);
            return property && Object.prototype.hasOwnProperty.call(property, 'value') && typeof property.value === 'string'
                ? property.value : null;
        } catch (ignored) { return null; }
        """)
    private static native String ownString(JSObject value, String field);
    private static String message(JSObject value, String kind) {
        String message = ownString(value, "message");
        return message == null ? primitiveMessage(value, kind) : message;
    }
    @JSBody(params = {"value", "kind"}, script = "return value === null || (typeof value !== 'object' && typeof value !== 'function') ? String(value) : 'JavaScript ' + kind + ' thrown';")
    private static native String primitiveMessage(JSObject value, String kind);
}
