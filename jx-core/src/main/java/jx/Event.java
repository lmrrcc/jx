package jx;

/** Typed access to common React synthetic event properties. Unsupported target fields are empty/false. */
public interface Event {
    String targetValue();
    boolean targetChecked();
    String key();
    void preventDefault();
    void stopPropagation();
}
