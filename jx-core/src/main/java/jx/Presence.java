package jx;

/** An own property's presence in a typed JavaScript-shaped value. These are three distinct
 * states: no own property, an own property whose value is undefined, and an own value.
 * Present(null) is permitted here; the declaring field's value type decides whether null
 * is valid. This immutable wrapper does not copy a contained reference or change its
 * identity. It is not a serialization or hot-replacement codec. */
public sealed interface Presence<V> permits Presence.Absent, Presence.Undefined, Presence.Present {
    /** No own property exists. */
    record Absent<V>() implements Presence<V> {}
    /** An own property exists and its value is JavaScript undefined. */
    record Undefined<V>() implements Presence<V> {}
    /** An own property exists. Its value may be null when the field type permits null. */
    record Present<V>(V value) implements Presence<V> {}

    static <V> Presence<V> absent() { return new Absent<>(); }
    static <V> Presence<V> undefined() { return new Undefined<>(); }
    static <V> Presence<V> present(V value) { return new Present<>(value); }
}
