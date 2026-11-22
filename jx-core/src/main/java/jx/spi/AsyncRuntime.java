package jx.spi;

import jx.Promise;

/** Browser scheduling contract behind {@link jx.Promise} and {@link jx.Async}, installed once per
 * Java runtime by the renderer. */
public interface AsyncRuntime {
    /** The body of an asynchronous function. A dedicated type, so that only code that really
     * reaches a suspension point is compiled as suspendable. */
    @FunctionalInterface interface Coroutine { void run(); }
    /** Runs {@code task} after the current JavaScript job, in order, like a promise reaction. */
    void defer(Runnable task);
    /** Runs {@code body} where {@link #await} may suspend it. It starts at once when no other
     * suspendable code is running, otherwise after the current JavaScript job. */
    void start(Coroutine body);
    /** Suspends the calling asynchronous function until {@code promise} is settled.
     * @throws IllegalStateException outside an asynchronous function */
    void await(Promise<?> promise);
}
