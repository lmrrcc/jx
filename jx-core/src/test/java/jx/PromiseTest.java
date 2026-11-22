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
        @Override public void defer(Runnable task) { queue.add(task); }
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
    @BeforeEach void reset() { RUNTIME.queue.clear(); }

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
}
