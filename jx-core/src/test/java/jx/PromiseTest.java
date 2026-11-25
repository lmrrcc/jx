package jx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import jx.spi.AsyncRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromiseTest {
    /** A queue in place of browser microtasks; await drains it until the promise is settled. */
    private static final class QueueRuntime implements AsyncRuntime {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        String context;
        @Override public void defer(Runnable task) { queue.add(task); }
        @Override public Runnable reaction(Runnable task) {
            String registered = context;
            return () -> defer(() -> {
                String previous = context;
                context = registered;
                try { task.run(); } finally { context = previous; }
            });
        }
        @Override public void start(Coroutine body) { body.run(); }
        @Override public void await(Promise<?> promise) {
            while (promise.pending()) {
                Runnable next = queue.poll();
                if (next == null) throw new IllegalStateException("nothing left to settle the promise");
                next.run();
            }
        }
        void drain() { for (Runnable next = queue.poll(); next != null; next = queue.poll()) next.run(); }
    }

    private static final QueueRuntime RUNTIME = new QueueRuntime();
    private final List<String> seen = new ArrayList<>();

    @BeforeAll static void install() { Async.installRuntime(RUNTIME); }
    @BeforeEach void reset() { RUNTIME.queue.clear(); RUNTIME.context = null; }

    @Test void observersKeepTheirRegistrationContextInsteadOfTheResolverContext() {
        List<Promise.Resolver<String>> resolver = new ArrayList<>();
        Promise<String> source = Promise.create(resolver::add);
        RUNTIME.context = "first";
        source.whenSettled(value -> seen.add(RUNTIME.context + ":" + value), error -> {});
        RUNTIME.context = "second";
        Promise<String> mapped = source.map(value -> RUNTIME.context + ":" + value);
        RUNTIME.context = "resolver";
        resolver.getFirst().resolve("value");
        assertEquals(List.of(), seen);
        RUNTIME.drain();
        assertEquals(List.of("first:value"), seen);
        assertEquals("second:value", Async.await(mapped));
        assertEquals("resolver", RUNTIME.context, "Each callback restores the surrounding job context");
        RUNTIME.context = "late";
        source.whenSettled(value -> seen.add(RUNTIME.context + ":" + value), error -> {});
        RUNTIME.context = "other";
        RUNTIME.drain();
        assertEquals(List.of("first:value", "late:value"), seen);
        assertEquals("other", RUNTIME.context);
    }

    @Test void callbacksRunLaterAndInRegistrationOrder() {
        List<Promise.Resolver<String>> resolver = new ArrayList<>();
        Promise<String> promise = Promise.create(resolver::add);
        promise.whenSettled(value -> seen.add("first " + value), error -> seen.add("error"));
        promise.whenSettled(value -> seen.add("second " + value), error -> seen.add("error"));
        resolver.get(0).resolve("a");
        assertFalse(promise.pending());
        assertEquals(List.of(), seen, "no callback inside resolve");
        RUNTIME.drain();
        assertEquals(List.of("first a", "second a"), seen);
    }

    @Test void throwingObserverStillReportsOnceAndDoesNotStarveTheThenableObserver() {
        List<Promise.Resolver<String>> sourceResolver = new ArrayList<>();
        Promise<String> source = Promise.create(sourceResolver::add);
        List<Promise.Resolver<String>> bridgeResolver = new ArrayList<>();
        Promise<String> bridgeResult = Promise.create(bridgeResolver::add);
        IllegalStateException observerFailure = new IllegalStateException("observer failed");
        List<String> calls = new ArrayList<>();
        List<Throwable> reported = new ArrayList<>();

        source.whenSettled(value -> {
            calls.add("throwing " + value);
            throw observerFailure;
        }, failure -> { throw new AssertionError("The source should resolve", failure); });
        // Same shape as AsyncBridge.thenable: an independent observer forwards settlement.
        source.whenSettled(value -> {
            calls.add("bridge " + value);
            bridgeResolver.getFirst().resolve(value);
        }, bridgeResolver.getFirst()::reject);
        bridgeResult.whenSettled(value -> calls.add("native consumer " + value),
                failure -> { throw new AssertionError("An observer cannot reject the source", failure); });

        sourceResolver.getFirst().resolve("kept");
        assertFalse(source.pending());
        assertTrue(bridgeResult.pending());
        assertEquals(List.of(), calls, "resolve must not invoke an observer synchronously");
        RUNTIME.defer(() -> calls.add("after settlement"));

        // Model the browser host reporting uncaught microtask errors and continuing other jobs.
        for (Runnable job = RUNTIME.queue.poll(); job != null; job = RUNTIME.queue.poll()) {
            try { job.run(); }
            catch (RuntimeException | Error failure) { reported.add(failure); }
        }

        assertEquals(1, reported.size(), "The observer error must remain visible exactly once");
        assertSame(observerFailure, reported.getFirst(), "Do not wrap, replace or swallow the error");
        assertFalse(bridgeResult.pending(), "An earlier observer must not leave React's thenable pending");
        assertEquals(List.of("throwing kept", "bridge kept", "after settlement", "native consumer kept"), calls,
                "Observers retain registration order and downstream delivery remains asynchronous");
        assertEquals("kept", Async.await(source));
        assertEquals("kept", Async.await(bridgeResult));
    }

    @Test void theFirstSettlementWins() {
        Promise<String> promise = Promise.create(resolver -> {
            resolver.resolve("kept");
            resolver.resolve("ignored");
            resolver.reject(new IllegalStateException("ignored"));
        });
        promise.whenSettled(seen::add, error -> seen.add("error"));
        assertEquals(List.of(), seen, "a settled promise still calls back later");
        RUNTIME.drain();
        assertEquals(List.of("kept"), seen);
    }

    @Test void anExecutorExceptionRejects() {
        IllegalArgumentException failure = new IllegalArgumentException("bad");
        Promise<String> promise = Promise.create(resolver -> { throw failure; });
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> Async.await(promise)));
    }

    @Test void mapFlatMapAndRecoverCarryValuesAndFailures() {
        Promise<Integer> length = Promise.resolved("four").map(String::length);
        assertEquals(4, Async.await(length));
        assertEquals("4!", Async.await(length.flatMap(value -> Promise.resolved(value + "!"))));
        Promise<Integer> failed = length.map(value -> { throw new IllegalStateException("mapper"); });
        assertEquals("mapper", assertThrows(IllegalStateException.class, () -> Async.await(failed)).getMessage());
        assertEquals("mapper", assertThrows(IllegalStateException.class, () -> Async.await(failed.map(value -> value + 1))).getMessage(),
                "a failure skips later mappers");
        assertEquals(-1, Async.await(failed.recover(error -> -1)));
        assertThrows(NullPointerException.class, () -> Async.await(length.flatMap(value -> null)));
    }

    @Test void runSettlesWithTheResultOrTheFailureOfTheBody() {
        assertEquals("ab", Async.await(Async.run(() -> Async.await(Promise.resolved("a")) + "b")));
        Promise<Void> failing = Async.run(() -> { throw new IOException("disk"); });
        AsyncException wrapped = assertThrows(AsyncException.class, () -> Async.await(failing));
        assertEquals("disk", wrapped.getCause().getMessage());
        Promise<String> caught = Async.run(() -> {
            try { return Async.await(Promise.<String>rejected(new IllegalStateException("inner"))); }
            catch (IllegalStateException error) { return "caught " + error.getMessage(); }
        });
        assertEquals("caught inner", Async.await(caught));
    }

    @Test void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> Promise.create(null));
        assertThrows(NullPointerException.class, () -> Promise.rejected(null));
        assertThrows(NullPointerException.class, () -> Async.await(null));
        assertTrue(Promise.create(resolver -> {}).pending());
    }

    @Test void aLazyComponentDoesNotLoadWhenItIsDeclared() {
        ComponentType<Void> lazy = ComponentType.lazy("Late", () -> { seen.add("loaded"); return Promise.create(resolver -> {}); });
        assertEquals("Late", lazy.name());
        assertEquals(List.of(), seen, "the loader waits for the first render");
        assertThrows(NullPointerException.class, () -> ComponentType.lazy("Late", null));
    }

    @Test void aLazyComponentKeepsItsDescriptorAndRejectsANullLoader() {
        LazyComponent<Void> lazy = LazyComponent.of("Late", () -> { seen.add("loaded"); return Promise.create(resolver -> {}); });
        assertSame(lazy.type(), lazy.type());
        lazy.retry();
        assertEquals(List.of(), seen, "retry does not load by itself");
        assertThrows(NullPointerException.class, () -> LazyComponent.of("Late", null));
    }
}
