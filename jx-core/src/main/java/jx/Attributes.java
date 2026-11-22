package jx;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable DOM attribute collection: scalar attributes, Style, and separately typed event handlers.
 * class/for are translated to className/htmlFor. String event handlers are rejected; raw HTML needs {@link RawHtml}. */
public final class Attributes {
    public record Entry(String name, Object value) {}
    private final Map<String, Object> values;
    private Attributes(Map<String, Object> values) { this.values = values; }
    public static Attributes create() { return new Attributes(Map.of()); }
    public Attributes attr(String name, Object value) {
        validate(name);
        if (name.equals("ref")) {
            if (!(value instanceof DomRef || value instanceof RefCallback)) throw new IllegalArgumentException("ref requires DomRef or RefCallback");
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
    /** These attributes followed by all of {@code other}, whose values win for names in both: the
     * typed form of a JSX spread. A null argument adds nothing. */
    public Attributes merge(Attributes other) {
        if (other == null || other.values.isEmpty()) return this;
        var copy = new LinkedHashMap<>(values);
        copy.putAll(other.values);
        return new Attributes(java.util.Collections.unmodifiableMap(copy));
    }
    /** Attaches the element to a ref object. A null ref attaches nothing. */
    public Attributes ref(DomRef ref) { return ref == null ? this : with("ref", ref); }
    /** Attaches the element through a callback ref. A null callback attaches nothing. */
    public Attributes ref(RefCallback callback) { return callback == null ? this : with("ref", callback); }
    /** The URL a form submits to. */
    public Attributes action(String url) { return attr("action", url); }
    /** A function as form action; null adds nothing. */
    public Attributes action(FormAction action) { return action == null ? this : with("action", action); }
    /** The URL a submit button submits its form to. */
    public Attributes formAction(String url) { return attr("formAction", url); }
    /** A function as the action of a submit button; null adds nothing. */
    public Attributes formAction(FormAction action) { return action == null ? this : with("formAction", action); }
    /** Replaces the element's content with parsed HTML, React's {@code dangerouslySetInnerHTML}.
     * The element must have no children. Null adds nothing. See {@link RawHtml} for the risk. */
    public Attributes dangerouslySetInnerHTML(RawHtml html) { return html == null ? this : with("dangerouslySetInnerHTML", html); }
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
