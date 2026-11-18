package jx.react;

import jx.*;
import org.teavm.jso.*;

/** Deliberate scalar/opaque-object boundary. Java object identity is never replaced by equals(). */
final class Interop {
    private Interop() {}
    static Object scalar(Object value) {
        if (value instanceof String text) return text(text);
        if (value instanceof Character character) return text(character.toString());
        if (value instanceof Boolean bool) return bool(bool);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Float || value instanceof Double)
            return number(((Number) value).doubleValue());
        if (value instanceof Long number) {
            if (number < -9_007_199_254_740_991L || number > 9_007_199_254_740_991L) throw new IllegalArgumentException("Long exceeds JavaScript exact integer range; use a String binding");
            return number(number.doubleValue());
        }
        return value;
    }
    static Object[] dependencies(Dependencies dependencies) {
        Object[] values = dependencies.values();
        if (values != null) for (int i = 0; i < values.length; i++) values[i] = scalar(values[i]);
        return values;
    }
    static boolean same(Object first, Object second) { return sameNative(scalar(first), scalar(second)); }
    @JSBody(params = "value", script = "return value;") static native Object text(String value);
    @JSBody(params = "value", script = "return value;") static native Object number(double value);
    @JSBody(params = "value", script = "return value;") static native Object bool(boolean value);
    @JSBody(params = {"a", "b"}, script = "return Object.is(a, b);") private static native boolean sameNative(Object a, Object b);
    /** A React hook slot retains exactly the Java value supplied to that slot. This unchecked cast is
     * confined to the opaque JSO boundary, which cannot express Java's erased generic type. */
    @SuppressWarnings("unchecked") static <T> T value(Object value) { return (T) value; }
}
