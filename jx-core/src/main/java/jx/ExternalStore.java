package jx;

/** Browser-thread store with stable immutable snapshots while unchanged.
 * subscribe returns cleanup and must not notify after that cleanup runs. */
public interface ExternalStore<T> {
    T snapshot();
    Runnable subscribe(Runnable listener);
    /** The snapshot used while rendering on the server and while hydrating that HTML; it must be
     * the same in both places, or hydration reports a mismatch. After hydration the component
     * switches to {@link #snapshot()}. The default is {@link #snapshot()}. */
    default T serverSnapshot() { return snapshot(); }
}
