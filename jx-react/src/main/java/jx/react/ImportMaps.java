package jx.react;

import java.util.Map;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/** Schema-specific conversion: property keys such as __proto__ remain ordinary own data. */
final class ImportMaps {
    private ImportMaps() {}
    static JSObject nativeMap(ImportMap source) {
        JSObject result = object();
        if (source.imports() != null) setObject(result, "imports", mappings(source.imports()));
        if (source.scopes() != null) {
            JSObject scopes = object();
            for (var entry : source.scopes().entrySet()) setObject(scopes, entry.getKey(), mappings(entry.getValue()));
            setObject(result, "scopes", scopes);
        }
        if (source.integrity() != null) setObject(result, "integrity", mappings(source.integrity()));
        return result;
    }
    private static JSObject mappings(Map<String, String> entries) {
        JSObject result = object();
        for (var entry : entries.entrySet()) setText(result, entry.getKey(), entry.getValue());
        return result;
    }
    @JSBody(script = "return Object.create(null);") private static native JSObject object();
    @JSBody(params = {"target", "key", "value"}, script = "target[key] = value;")
    private static native void setObject(JSObject target, String key, JSObject value);
    @JSBody(params = {"target", "key", "value"}, script = "target[key] = value;")
    private static native void setText(JSObject target, String key, String value);
}
