package jx.react;

import jx.*;
import org.teavm.jso.*;
import org.teavm.jso.core.JSBoolean;
import org.teavm.jso.core.JSNumber;
import org.teavm.jso.core.JSString;

/** Deliberate scalar/opaque-object boundary. Java object identity is never replaced by equals(). */
final class Interop {
    private Interop() {}
    // Keep scalars on the JSO path through the receiving native signature. Returning Object
    // boxes native numbers in TeaVM's JSWrapper cache, whose Map merges -0 and +0.
    static JSObject scalar(Object value) {
        if (value instanceof String text) return text(text);
        if (value instanceof Character character) return text(character.toString());
        if (value instanceof Boolean bool) return bool(bool);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Float || value instanceof Double)
            return number(((Number) value).doubleValue());
        if (value instanceof Long number) {
            if (number < -9_007_199_254_740_991L || number > 9_007_199_254_740_991L) throw new IllegalArgumentException("Long exceeds JavaScript exact integer range; use a String binding");
            return number(number.doubleValue());
        }
        return opaque(value);
    }
    static JSObject dependencies(Dependencies dependencies) {
        Object[] values = dependencies.values();
        if (values == null) return null;
        // A Java JSObject[] still boxes elements through TeaVM's Java-array adapter.
        // Construct the dependency array natively so Object.is sees the original scalar.
        JSObject nativeValues = array(values.length);
        for (int i = 0; i < values.length; i++) arraySet(nativeValues, i, scalar(values[i]));
        return nativeValues;
    }
    static boolean same(Object first, Object second) { return sameNative(scalar(first), scalar(second)); }
    static JSObject text(String value) { return JSString.valueOf(value); }
    static JSObject number(double value) { return JSNumber.valueOf(value); }
    static JSObject bool(boolean value) { return JSBoolean.valueOf(value); }
    @JSBody(params = "value", script = "return value;") static native JSObject opaque(Object value);
    @JSBody(params = "length", script = "return new Array(length);") private static native JSObject array(int length);
    @JSBody(params = {"array", "index", "value"}, script = "array[index] = value;") private static native void arraySet(JSObject array, int index, JSObject value);
    @JSBody(params = {"a", "b"}, script = "return Object.is(a, b);") private static native boolean sameNative(JSObject a, JSObject b);
    /** A React hook slot retains exactly the Java value supplied to that slot. This unchecked cast is
     * confined to the opaque JSO boundary, which cannot express Java's erased generic type. */
    @SuppressWarnings("unchecked") static <T> T value(Object value) { return (T) value; }
}
