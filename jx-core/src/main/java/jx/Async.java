package jx;

import java.util.Objects;
import jx.spi.AsyncRuntime;

/** Asynchronous functions for Java: {@link #run} is {@code async}, {@link #await} is {@code await}.
 *
 * <pre>{@code
 * Promise<String> title = Async.run(() -> {
 *     User user = await(loadUser(id));        // suspends here; the browser stays responsive
 *     Team team = await(loadTeam(user.team()));
 *     return user.name() + " of " + team.name();
 * });
 * }</pre>
 *
 * <p>The body starts at once and runs until its first {@code await}, like a JavaScript async
 * function; when it is started from inside another asynchronous function it starts after the
 * current browser job instead. Everything runs on the single browser thread, so no locking is
 * needed, but state read before an {@code await} may be stale after it.
 *
 * <p>{@code await} works only in code that an asynchronous function calls directly in Java. It
 * must not be called while rendering, in a hook, or inside a callback that React or the browser
 * invokes (for example the action of {@code React.flushSync}); start a new asynchronous function
 * there. In a component, read a promise with {@link Hooks#use(Promise)}. */
public final class Async {
    /** An asynchronous function with a result. */
    @FunctionalInterface public interface Body<T> { T run() throws Exception; }
    /** An asynchronous function without a result. */
    @FunctionalInterface public interface Action { void run() throws Exception; }

    private static AsyncRuntime runtime;
    private Async() {}

    /** Starts {@code body} and returns the promise of its result. An exception thrown by the body,
     * also after an {@code await}, rejects the promise. */
    public static <T> Promise<T> run(Body<T> body) {
        Objects.requireNonNull(body);
        AsyncRuntime current = runtime();
        return Promise.create(resolver -> current.start(() -> {
            T result;
            try { result = body.run(); } catch (Exception | Error failure) { resolver.reject(failure); return; }
            resolver.resolve(result);
        }));
    }

    /** As {@link #run(Body)}, for a body without a result. */
    public static Promise<Void> run(Action action) {
        Objects.requireNonNull(action);
        return run(() -> { action.run(); return null; });
    }

    /** Suspends the calling asynchronous function until {@code promise} is settled and returns its
     * value. A failed promise throws its error here: unchecked errors as they are, checked
     * exceptions wrapped in {@link AsyncException}.
     * @throws IllegalStateException outside an asynchronous function */
    public static <T> T await(Promise<T> promise) {
        Objects.requireNonNull(promise);
        if (promise.pending()) runtime().await(promise);
        return promise.settledValue();
    }

    /** Renderer bootstrap only. */
    public static void installRuntime(AsyncRuntime candidate) {
        Objects.requireNonNull(candidate);
        if (runtime != null && runtime != candidate) throw new IllegalStateException("An async runtime is already installed");
        runtime = candidate;
    }

    static AsyncRuntime runtime() {
        if (runtime == null) throw new IllegalStateException("Promises require the browser runtime; mount a React root first");
        return runtime;
    }
}
