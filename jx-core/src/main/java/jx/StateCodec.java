package jx;

/** Explicit portable state schema for development hot replacement. A new Java runtime decodes strings
 * produced by the old runtime; no Java object crosses that boundary. Implementations must be pure,
 * deterministic, validate input, and change schema() whenever their representation becomes incompatible.
 * Resources, callbacks, pending operations, and identity-sensitive objects must not be serialized. */
public interface StateCodec<T> {
    String schema();
    String encode(T value);
    T decode(String payload);
}
