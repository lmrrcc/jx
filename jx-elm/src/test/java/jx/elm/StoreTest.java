package jx.elm;

import jx.View;
import jx.Views;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoreTest {
    private record Model(int value) {}
    private abstract static class TestProgram implements Program<Model, Integer> {
        @Override public Transition<Model, Integer> init() { return Transition.stay(new Model(0)); }
        @Override public View view(Model model, Dispatch<Integer> dispatch) { return Views.fragment(model.value()); }
    }
    @Test void commitsBeforeCommandsAndSerializesReentrantMessages() {
        List<String> order = new ArrayList<>();
        var program = new TestProgram() {
            @Override public Transition<Model, Integer> update(Model model, Integer message) {
                order.add("update " + message);
                return new Transition<>(new Model(message), message == 1 ? List.of(new Command<>("effect", reply -> {
                    order.add("command"); reply.send(2); return Cancellation.NONE;
                })) : List.of());
            }
        };
        try (var store = new Store<>(program)) {
            store.subscribe(() -> order.add("commit " + store.snapshot().value()));
            store.start(); store.dispatch(1);
            assertEquals(List.of("update 1", "commit 1", "command", "update 2", "commit 2"), order);
        }
    }
    @Test void replacementCancelsAndIgnoresStaleAndRepeatedReplies() {
        List<Dispatch<Integer>> replies = new ArrayList<>();
        AtomicInteger cancelled = new AtomicInteger();
        var program = new TestProgram() {
            @Override public Transition<Model, Integer> update(Model model, Integer message) {
                return new Transition<>(new Model(message), message < 3 ? List.of(new Command<>("fetch", reply -> {
                    replies.add(reply); return cancelled::incrementAndGet;
                })) : List.of());
            }
        };
        try (var store = new Store<>(program)) {
            store.start(); store.dispatch(1); store.dispatch(2);
            assertEquals(1, cancelled.get());
            replies.get(0).send(3);
            assertEquals(2, store.snapshot().value());
            replies.get(1).send(4); replies.get(1).send(5);
            assertEquals(4, store.snapshot().value());
            assertEquals(2, cancelled.get());
            store.stop(); replies.get(1).send(6);
            assertEquals(4, store.snapshot().value());
        }
    }
    @Test void snapshotIdentityAndManagedSubscriptionsSurviveDevelopmentRemount() {
        AtomicInteger started = new AtomicInteger();
        AtomicInteger stopped = new AtomicInteger();
        var program = new TestProgram() {
            @Override public Transition<Model, Integer> update(Model model, Integer message) { return Transition.stay(message == 0 ? model : new Model(message)); }
            @Override public List<Subscription<Integer>> subscriptions(Model model) {
                return model.value() < 2 ? List.of(new Subscription<>("feed", reply -> { started.incrementAndGet(); return stopped::incrementAndGet; })) : List.of();
            }
        };
        try (var store = new Store<>(program)) {
            assertEquals(0, started.get());
            store.start(); Model initial = store.snapshot(); store.dispatch(0);
            assertSame(initial, store.snapshot());
            store.dispatch(1); assertEquals(1, started.get());
            store.stop(); store.start();
            assertEquals(2, started.get()); assertEquals(1, stopped.get());
            store.dispatch(2); assertEquals(2, stopped.get());
        }
        assertEquals(2, stopped.get());
    }
    @Test void failuresStopOwnedWorkAndNeverBecomeSuccess() {
        AtomicInteger stopped = new AtomicInteger();
        var program = new TestProgram() {
            @Override public Transition<Model, Integer> update(Model model, Integer message) { throw new IllegalArgumentException("invalid message"); }
            @Override public List<Subscription<Integer>> subscriptions(Model model) { return List.of(new Subscription<>("feed", reply -> stopped::incrementAndGet)); }
        };
        try (var store = new Store<>(program)) {
            store.start();
            assertEquals("invalid message", assertThrows(IllegalArgumentException.class, () -> store.dispatch(1)).getMessage());
            assertEquals(1, stopped.get());
            assertThrows(IllegalStateException.class, () -> store.dispatch(1));
        }
    }
    @Test void synchronousMessageChainsAreBounded() {
        var program = new TestProgram() {
            @Override public Transition<Model, Integer> update(Model model, Integer message) {
                return new Transition<>(new Model(message), List.of(new Command<>("loop", reply -> { reply.send(message + 1); return Cancellation.NONE; })));
            }
        };
        try (var store = new Store<>(program, 4)) {
            store.start(); assertThrows(IllegalStateException.class, () -> store.dispatch(1));
            assertEquals(4, store.snapshot().value());
        }
    }
    @Test void identicalListenersHaveIndependentSubscriptionLifetimes() {
        AtomicInteger notifications = new AtomicInteger();
        var program = new TestProgram() {
            @Override public Transition<Model, Integer> update(Model model, Integer message) { return Transition.stay(new Model(message)); }
        };
        try (var store = new Store<>(program)) {
            Runnable listener = notifications::incrementAndGet;
            Runnable first = store.subscribe(listener);
            Runnable second = store.subscribe(listener);
            store.start(); first.run(); first.run(); store.dispatch(1);
            assertEquals(1, notifications.get());
            second.run(); store.dispatch(2);
            assertEquals(1, notifications.get());
        }
    }
    @Test void restoredStoreRecreatesSubscriptionsWithoutReplayingInitialCommands() {
        AtomicInteger commands = new AtomicInteger();
        AtomicInteger subscriptions = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        var program = new TestProgram() {
            @Override public Transition<Model, Integer> init() { return new Transition<>(new Model(0), List.of(new Command<>("load", reply -> { commands.incrementAndGet(); return Cancellation.NONE; }))); }
            @Override public Transition<Model, Integer> update(Model model, Integer message) { return Transition.stay(new Model(message)); }
            @Override public List<Subscription<Integer>> subscriptions(Model model) { return List.of(new Subscription<>("current:" + model.value(), reply -> { subscriptions.incrementAndGet(); return stops::incrementAndGet; })); }
        };
        try (var store = new Store<>(program)) {
            store.restore(new Model(42));
            store.start(); store.stop(); store.start();
            assertEquals(42, store.snapshot().value());
            assertEquals(0, commands.get());
            assertEquals(2, subscriptions.get());
            assertEquals(1, stops.get());
            assertThrows(IllegalStateException.class, () -> store.restore(new Model(0)));
        }
        assertEquals(2, stops.get());
    }
}
