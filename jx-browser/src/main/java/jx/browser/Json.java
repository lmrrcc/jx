package jx.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.teavm.jso.*;

/** Immutable native JSON value for explicit schema codecs, without Java reflection or arbitrary maps.
 * Browser-only. Accessors reject missing/wrong-type properties; intValue checks exact 32-bit integers. */
public final class Json {
    final JSObject value;
    Json(JSObject value) { this.value = value; }
    public static Json nil() { return new Json(null); }
    /** Parse an explicit JSON checkpoint. Invalid JSON fails as a Java IllegalArgumentException;
     * the eight-million-character bound also applies before allocating the parsed value. */
    public static Json parse(String text) {
        Objects.requireNonNull(text);
        if (text.length() > 8_388_608) throw new IllegalArgumentException("JSON text exceeds limit");
        JSObject parsed = parseNative(text);
        if (!parseValid(parsed)) throw new IllegalArgumentException("Invalid JSON text");
        return new Json(getNative(parsed, "value"));
    }
    public static Json string(String value) { return new Json(stringNative(Objects.requireNonNull(value))); }
    public static Json number(int value) { return new Json(numberNative(value)); }
    public static Json bool(boolean value) { return new Json(boolNative(value)); }
    public static Json object() { return new Json(objectNative()); }
    public static Json array(List<Json> values) {
        JSObject[] array = new JSObject[values.size()];
        for (int i = 0; i < array.length; i++) array[i] = Objects.requireNonNull(values.get(i)).value;
        return new Json(arrayNative(array));
    }
    public Json put(String name, Json field) {
        require("object");
        if (Objects.requireNonNull(name).equals("__proto__")) throw new IllegalArgumentException("Reserved JSON property");
        return new Json(putNative(value, name, Objects.requireNonNull(field).value));
    }
    public Json get(String name) {
        require("object"); Objects.requireNonNull(name);
        if (!hasNative(value, name)) throw new IllegalArgumentException("Missing JSON property: " + name);
        return new Json(getNative(value, name));
    }
    public boolean has(String name) { require("object"); return hasNative(value, Objects.requireNonNull(name)); }
    public String stringValue() { require("string"); return stringValue(value); }
    public int intValue() {
        require("number");
        if (!isInt(value)) throw new IllegalArgumentException("JSON number is not an exact 32-bit integer");
        return intValue(value);
    }
    public boolean booleanValue() { require("boolean"); return booleanValue(value); }
    public boolean isNull() { return value == null; }
    public List<Json> items() {
        require("array"); int length = length(value); var items = new ArrayList<Json>(length);
        for (int i = 0; i < length; i++) items.add(new Json(item(value, i)));
        return List.copyOf(items);
    }
    public String stringify() { return stringify(value); }
    private void require(String expected) { if (!type(value).equals(expected)) throw new IllegalArgumentException("Expected JSON " + expected + "; got " + type(value)); }
    @JSBody(params = "value", script = "return value === null ? 'null' : Array.isArray(value) ? 'array' : typeof value;") private static native String type(JSObject value);
    @JSBody(params = "value", script = "return value;") private static native JSObject stringNative(String value);
    @JSBody(params = "value", script = "return value;") private static native JSObject numberNative(int value);
    @JSBody(params = "value", script = "return value;") private static native JSObject boolNative(boolean value);
    @JSBody(script = "return Object.create(null);") private static native JSObject objectNative();
    @JSBody(params = "values", script = "return values;") private static native JSObject arrayNative(JSObject[] values);
    @JSBody(params = {"object", "name", "field"}, script = "var result = Object.assign(Object.create(null), object); result[name] = field; return result;") private static native JSObject putNative(JSObject object, String name, JSObject field);
    @JSBody(params = {"object", "name"}, script = "return Object.prototype.hasOwnProperty.call(object, name);") private static native boolean hasNative(JSObject object, String name);
    @JSBody(params = {"object", "name"}, script = "return object[name];") private static native JSObject getNative(JSObject object, String name);
    @JSBody(params = "value", script = "return value;") private static native String stringValue(JSObject value);
    @JSBody(params = "value", script = "return Number.isInteger(value) && value >= -2147483648 && value <= 2147483647;") private static native boolean isInt(JSObject value);
    @JSBody(params = "value", script = "return value;") private static native int intValue(JSObject value);
    @JSBody(params = "value", script = "return value;") private static native boolean booleanValue(JSObject value);
    @JSBody(params = "value", script = "return value.length;") private static native int length(JSObject value);
    @JSBody(params = {"value", "index"}, script = "return value[index];") private static native JSObject item(JSObject value, int index);
    @JSBody(params = "value", script = "return JSON.stringify(value);") private static native String stringify(JSObject value);
    @JSBody(params = "text", script = "try { return {valid: true, value: JSON.parse(text)}; } catch (error) { return {valid: false}; }") private static native JSObject parseNative(String text);
    @JSBody(params = "parsed", script = "return parsed.valid;") private static native boolean parseValid(JSObject parsed);
}
