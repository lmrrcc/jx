package jx.elm;

/** Releases an owned operation. Implementations must be idempotent and must not throw. */
@FunctionalInterface
public interface Cancellation {
    Cancellation NONE = () -> {};
    void cancel();
}
