package jx.conformance;

import java.util.ArrayList;
import java.util.List;
import jx.ExternalStore;

/** A store owned outside React, for the external-store case. Browser main thread only. */
public final class CounterStore implements ExternalStore<Integer> {
    /** The single store instance of the page. */
    public static final CounterStore SHARED = new CounterStore();
    private final List<Runnable> listeners = new ArrayList<>();
    private Integer value = 0;

    private CounterStore() {}

    @Override public Integer snapshot() { return value; }

    @Override public Runnable subscribe(Runnable listener) {
        listeners.add(listener);
        Bench.log("subscribe");
        return () -> {
            listeners.remove(listener);
            Bench.log("unsubscribe");
        };
    }

    /** Changes the value and notifies a copy of the current listeners. */
    public void increment() {
        value = value + 1;
        List.copyOf(listeners).forEach(Runnable::run);
    }
}
