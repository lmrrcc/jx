package jx;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable DOM attribute collection: scalar attributes, Style, and separately typed event handlers.
 * class/for are translated to className/htmlFor. Raw HTML and string event handlers are rejected. */
public final class Attributes {
    public record Entry(String name, Object value) {}
    private final Map<String, Object> values;
    private Attributes(Map<String, Object> values) { this.values = values; }
    public static Attributes create() { return new Attributes(Map.of()); }
    public Attributes attr(String name, Object value) {
        validate(name);
        if (name.equals("ref")) {
            if (!(value instanceof DomRef)) throw new IllegalArgumentException("ref requires DomRef");
            return with(name, value);
        }
        if (name.startsWith("on") || name.equals("dangerouslySetInnerHTML") || name.equals("__proto__"))
            throw new IllegalArgumentException("Use the typed API for " + name);
        if (value instanceof Style && !name.equals("style")) throw new IllegalArgumentException("Style is only valid for the style property");
        if (value != null && !(value instanceof String || value instanceof Boolean || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double || value instanceof Style))
            throw new IllegalArgumentException("Attribute must be scalar or Style: " + name);
        if ((name.equals("href") || name.equals("src") || name.equals("action") || name.equals("formAction")) && value instanceof String url
                && url.replaceAll("[\\s\\p{Cntrl}]", "").toLowerCase(java.util.Locale.ROOT).startsWith("javascript:"))
            throw new IllegalArgumentException("Executable URL is not an attribute value");
        return with(name.equals("class") ? "className" : name.equals("for") ? "htmlFor" : name, value);
    }
    public Attributes on(String event, EventHandler handler) {
        validate(event);
        if (!event.matches("on[A-Z][A-Za-z]+")) throw new IllegalArgumentException("Use a React event name such as onClick");
        return with(event, Objects.requireNonNull(handler));
    }
    public List<Entry> entries() { return values.entrySet().stream().map(entry -> new Entry(entry.getKey(), entry.getValue())).toList(); }
    private Attributes with(String name, Object value) {
        var copy = new LinkedHashMap<>(values);
        copy.put(name, value);
        return new Attributes(java.util.Collections.unmodifiableMap(copy));
    }
    private static void validate(String name) {
        if (!Objects.requireNonNull(name).matches("[A-Za-z][A-Za-z0-9:_-]*")) throw new IllegalArgumentException("Invalid attribute name: " + name);
    }
}
