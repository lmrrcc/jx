package jx;

/** Browser-thread store with stable immutable snapshots while unchanged.
 * subscribe returns cleanup and must not notify after that cleanup runs. */
public interface ExternalStore<T> {
    T snapshot();
    Runnable subscribe(Runnable listener);
}
