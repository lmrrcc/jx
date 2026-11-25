package jx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassNullPatchTest {
    record Model(int count) {}
    record Queued(BiFunction<Model,Model,StatePatch<Model>> update, Runnable committed) {}

    static final class Counter extends ClassComponent<Model,Model,Void> {
        Counter(Model props, Model state) { super(props, state); }
        @Override public View render() { throw new AssertionError("No render expected"); }
    }

    private static Counter bind(List<Queued> queue) {
        Model initial = new Model(1);
        Counter counter = new Counter(initial, initial);
        counter.bind(new ClassComponent.Host<>() {
            @Override public Model props() { return initial; }
            @Override public Model state() { return initial; }
            @Override public void update(BiFunction<Model,Model,StatePatch<Model>> update, Runnable committed) {
                queue.add(new Queued(update, committed));
            }
            @Override public void force(Runnable committed) { fail("No force expected"); }
        });
        return counter;
    }

    @Test void directNullPatchesReachTheNativeQueueAndDoNotRunCallbacksEagerly() {
        List<Queued> queue = new ArrayList<>();
        Counter counter = bind(queue);
        List<String> delivered = new ArrayList<>();
        Runnable callback = () -> delivered.add("committed");
        StatePatch<Model> noChange = null;
        counter.setState(noChange);
        counter.setState((StatePatch<Model>) null, callback);
        assertEquals(2, queue.size());
        assertNull(queue.getFirst().committed());
        assertSame(callback, queue.getLast().committed());
        for (Queued queued : queue) assertNull(queued.update().apply(new Model(2), new Model(3)));
        assertTrue(delivered.isEmpty());
        queue.getLast().committed().run();
        assertEquals(List.of("committed"), delivered);
    }

    @Test void nullableResultDoesNotPermitANullUpdaterOrEagerlyEvaluateAFunction() {
        List<Queued> queue = new ArrayList<>();
        Counter counter = bind(queue);
        BiFunction<Model,Model,StatePatch<Model>> absent = null;
        assertThrows(NullPointerException.class, () -> counter.setState(absent));
        assertThrows(NullPointerException.class, () -> counter.setState(absent, () -> fail("Invalid update callback")));
        assertTrue(queue.isEmpty());
        List<Model> observed = new ArrayList<>();
        counter.setState((pending, props) -> { observed.add(pending); observed.add(props); return null; });
        assertTrue(observed.isEmpty());
        Model pending = new Model(5), props = new Model(8);
        assertNull(queue.getFirst().update().apply(pending, props));
        assertEquals(List.of(pending, props), observed);
        RuntimeException failure = new IllegalStateException("Updater failed");
        counter.setState((state, nextProps) -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> queue.getLast().update().apply(pending, props)));
    }

    @Test void nullPatchDoesNotRelaxInitializationAndConstructionGuards() {
        Model initial = new Model(0);
        assertThrows(NullPointerException.class, () -> new Counter(null, initial));
        assertThrows(NullPointerException.class, () -> new Counter(initial, null));
        Counter counter = new Counter(initial, initial);
        StatePatch<Model> noChange = null;
        assertThrows(IllegalStateException.class, () -> counter.setState(noChange));
        assertThrows(IllegalStateException.class, () -> counter.setState(noChange, () -> fail("Construction callback")));
    }
}
