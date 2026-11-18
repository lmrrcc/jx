package jx;

/** A state owner that can be restored before activation and stopped before hot replacement.
 * restore must not run external effects or replay pending commands. */
public interface StateTransfer<T> {
    T snapshot();
    void restore(T snapshot);
    void stop();
}
