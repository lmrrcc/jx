package jx;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable React style properties. Values are CSS strings or numeric React values; names use camelCase.
 * Properties keep the order in which they were added, which CSS depends on when a shorthand and
 * one of its longhands are both set. */
public final class Style {
    private final Map<String, Object> properties;
    /** {@code properties} is a fresh ordered map that the caller gives up. */
    private Style(Map<String, Object> properties) { this.properties = java.util.Collections.unmodifiableMap(properties); }
    public static Style create() { return new Style(new LinkedHashMap<>()); }
    public Style property(String name, String value) { return with(name, value); }
    public Style property(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Style number must be finite");
        return with(name, value);
    }
    private Style with(String name, Object value) {
        if (!Objects.requireNonNull(name).matches("(?:[a-zA-Z][a-zA-Z0-9]*|--[a-zA-Z0-9-]+)") || name.equals("__proto__")) throw new IllegalArgumentException("Invalid style name");
        var copy = new LinkedHashMap<>(properties); copy.put(name, Objects.requireNonNull(value)); return new Style(copy);
    }
    /** The properties in the order they were added; unmodifiable. */
    public Map<String, Object> properties() { return properties; }
}
