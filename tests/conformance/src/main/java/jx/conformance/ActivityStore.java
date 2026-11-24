package jx.conformance;

import java.util.ArrayList;
import java.util.List;
import jx.ExternalStore;

/** Page-owned fixture store; immutable snapshots and the actual live subscriber count. */
public final class ActivityStore implements ExternalStore<Integer> {
    public static final ActivityStore SHARED = new ActivityStore();
    private final List<Runnable> listeners = new ArrayList<>();
    private Integer value = 0;

    private ActivityStore() {}

    @Override public Integer snapshot() { return value; }
    @Override public Integer serverSnapshot() { return 0; }

    @Override public Runnable subscribe(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public int subscribers() { return listeners.size(); }

    public void increment() {
        value = value + 1;
        List.copyOf(listeners).forEach(Runnable::run);
    }
}
