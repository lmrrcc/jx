package jx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** A value that arrives later, or a failure: the JX counterpart of a JavaScript promise.
 *
 * <p>A promise is settled once, with a value or with an error; later attempts are ignored.
 * Callbacks never run inside the call that registers them or that settles the promise: they run
 * after the current browser job, in registration order. A promise has identity: create it outside
 * rendering, or keep it in state or a memo, before passing it to {@link Hooks#use(Promise)}.
 *
 * <p>Read the value with {@link Async#await(Promise)} inside {@link Async#run(Async.Body)}, with
 * {@link Hooks#use(Promise)} while rendering, or with the callbacks here. Promises run on the
 * single browser thread and are not safe for use from JVM threads. */
public final class Promise<T> {
    /** Settles the promise it was created for. The first call wins. */
    public interface Resolver<T> {
        void resolve(T value);
        /** {@code error} must not be null. */
        void reject(Throwable error);
    }

    private record Reaction<T>(Consumer<? super T> onValue, Consumer<? super Throwable> onError) {}

    private boolean settled;
    private T value;
    private Throwable error;
    private List<Runnable> reactions = new ArrayList<>();

    private Promise() {}

    /** Creates a promise and runs {@code executor} now with its resolver. An exception thrown by
     * the executor rejects the promise. */
    public static <T> Promise<T> create(Consumer<Resolver<T>> executor) {
        Objects.requireNonNull(executor);
        Promise<T> promise = new Promise<>();
        Resolver<T> resolver = promise.resolver();
        try { executor.accept(resolver); } catch (RuntimeException | Error failure) { resolver.reject(failure); }
        return promise;
    }

    /** A promise that already holds {@code value}, which may be null. */
    public static <T> Promise<T> resolved(T value) {
        Promise<T> promise = new Promise<>();
        promise.settle(value, null);
        return promise;
    }

    /** A promise that already failed with {@code error}. */
    public static <T> Promise<T> rejected(Throwable error) {
        Promise<T> promise = new Promise<>();
        promise.settle(null, Objects.requireNonNull(error));
        return promise;
    }

    /** True until the promise has a value or an error. */
    public boolean pending() { return !settled; }

    /** Calls exactly one of the callbacks once the promise is settled, also when it already is.
     * The call happens after the current browser job. Exceptions thrown by a callback are not
     * caught here; they do not prevent other observers from receiving settlement. Use
     * {@link #map} or {@link #recover} to carry a failure forward. The configured
     * renderer captures host async context when the observer is registered, not
     * whichever request happens to settle this promise later. */
    public void whenSettled(Consumer<? super T> onValue, Consumer<? super Throwable> onError) {
        Reaction<T> reaction = new Reaction<>(Objects.requireNonNull(onValue), Objects.requireNonNull(onError));
        Runnable schedule = Async.reaction(() -> deliver(reaction));
        if (settled) schedule.run();
        else reactions.add(schedule);
    }

    /** The promise of {@code mapper}'s result. A failure of this promise, or an exception thrown
     * by {@code mapper}, rejects the returned promise. */
    public <R> Promise<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        return create(resolver -> whenSettled(result -> {
            R mapped;
            try { mapped = mapper.apply(result); } catch (RuntimeException | Error failure) { resolver.reject(failure); return; }
            resolver.resolve(mapped);
        }, resolver::reject));
    }

    /** Chains a second asynchronous step: the returned promise settles like the promise that
     * {@code mapper} returns. A null result or an exception from {@code mapper} rejects it. */
    public <R> Promise<R> flatMap(Function<? super T, Promise<R>> mapper) {
        Objects.requireNonNull(mapper);
        return create(resolver -> whenSettled(result -> {
            Promise<R> next;
            try { next = Objects.requireNonNull(mapper.apply(result), "flatMap returned null"); }
            catch (RuntimeException | Error failure) { resolver.reject(failure); return; }
            next.whenSettled(resolver::resolve, resolver::reject);
        }, resolver::reject));
    }

    /** Replaces a failure with the value {@code fallback} computes from it. */
    public Promise<T> recover(Function<? super Throwable, ? extends T> fallback) {
        Objects.requireNonNull(fallback);
        return create(resolver -> whenSettled(resolver::resolve, failure -> {
            T recovered;
            try { recovered = fallback.apply(failure); } catch (RuntimeException | Error next) { resolver.reject(next); return; }
            resolver.resolve(recovered);
        }));
    }

    /** The value, for {@link Async#await} after the runtime reported the promise settled. */
    T settledValue() {
        if (!settled) throw new IllegalStateException("The promise is still pending");
        if (error instanceof RuntimeException failure) throw failure;
        if (error instanceof Error failure) throw failure;
        if (error != null) throw new AsyncException(error);
        return value;
    }

    private Resolver<T> resolver() {
        return new Resolver<>() {
            @Override public void resolve(T result) { settle(result, null); }
            @Override public void reject(Throwable failure) { settle(null, Objects.requireNonNull(failure)); }
        };
    }

    private void settle(T result, Throwable failure) {
        if (settled) return;
        settled = true;
        value = result;
        error = failure;
        List<Runnable> waiting = reactions;
        reactions = null;
        for (Runnable schedule : waiting) schedule.run();
    }

    private void deliver(Reaction<T> reaction) {
        if (error != null) reaction.onError().accept(error);
        else reaction.onValue().accept(value);
    }
}
