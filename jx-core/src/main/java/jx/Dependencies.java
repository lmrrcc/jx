package jx;

/** Explicit effect/memo dependency semantics. Scalars compare as JavaScript primitives, objects by identity. */
public final class Dependencies {
    private final Object[] values;
    private Dependencies(Object[] values) { this.values = values == null ? null : values.clone(); }
    /** Omit the dependency array, evaluating on each render. */
    public static Dependencies always() { return new Dependencies(null); }
    /** No reactive dependencies, retaining the result for this mounted instance. */
    public static Dependencies none() { return new Dependencies(new Object[0]); }
    public static Dependencies of(Object... values) { return new Dependencies(java.util.Objects.requireNonNull(values)); }
    public Object[] values() { return values == null ? null : values.clone(); }
}
