package jx.elm;

import jx.ExternalStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Serialized, browser/event-loop-thread MVU store. Construction only calls pure init; start owns effects.
 * Snapshots retain identity until a different model is committed. Effects run after commit/notification,
 * never inside update. A failure is propagated and stops active work. No exactly-once I/O guarantee.
 * stop preserves the model; restarting reinterprets init commands and current subscriptions. */
public final class Store<S, M> implements ExternalStore<S>, AutoCloseable {
    private final Program<S, M> program;
    private final List<Command<M>> initialCommands;
    private final int limit;
    private final ArrayDeque<M> queue = new ArrayDeque<>();
    private final Set<Runnable> listeners = new LinkedHashSet<>();
    private final Map<String, Operation> commands = new LinkedHashMap<>();
    private final Map<String, Operation> subscriptions = new LinkedHashMap<>();
    private S model;
    private boolean active;
    private boolean draining;
    private boolean closed;

    public Store(Program<S, M> program) { this(program, 1024); }
    public Store(Program<S, M> program, int capacity) {
        this.program = Objects.requireNonNull(program);
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive");
        limit = capacity;
        Transition<S, M> initial = Objects.requireNonNull(program.init());
        model = initial.model();
        initialCommands = initial.commands();
    }
    public void start() {
        if (closed) throw new IllegalStateException("Store is closed");
        if (active) return;
        active = true;
        draining = true;
        try { reconcile(); execute(initialCommands); }
        catch (RuntimeException | Error failure) { stop(); throw failure; }
        finally { draining = false; }
        drain();
    }
    /** Stop is reversible, making the same store safe for effect cleanup and React development remounts. */
    public void stop() {
        active = false;
        queue.clear();
        var owned = new ArrayList<Operation>(commands.values()); owned.addAll(subscriptions.values());
        commands.clear(); subscriptions.clear();
        for (Operation operation : owned) operation.cancel();
    }
    @Override public void close() { stop(); listeners.clear(); closed = true; }
    @Override public S snapshot() { return model; }
    @Override public Runnable subscribe(Runnable listener) {
        if (closed) throw new IllegalStateException("Store is closed");
        Objects.requireNonNull(listener);
        if (listeners.size() >= limit) throw new IllegalStateException("Store listener capacity exceeded");
        Runnable registration = listener::run;
        listeners.add(registration);
        return () -> listeners.remove(registration);
    }
    public void dispatch(M message) {
        if (!active) throw new IllegalStateException("Start the store before dispatching");
        if (queue.size() >= limit) throw new IllegalStateException("Store message capacity exceeded");
        queue.add(Objects.requireNonNull(message));
        drain();
    }
    private void drain() {
        if (draining) return;
        draining = true;
        int processed = 0;
        try {
            while (active && !queue.isEmpty()) {
                if (++processed > limit) throw new IllegalStateException("Synchronous message chain exceeded capacity");
                Transition<S, M> transition = Objects.requireNonNull(program.update(model, queue.remove()));
                S previous = model;
                model = transition.model();
                if (model != previous) for (Runnable listener : List.copyOf(listeners)) if (listeners.contains(listener)) listener.run();
                if (!active) break;
                reconcile();
                execute(transition.commands());
            }
        } catch (RuntimeException | Error failure) { stop(); throw failure; }
        finally { draining = false; }
    }
    private void execute(List<Command<M>> descriptions) {
        if (descriptions.size() > limit) throw new IllegalStateException("Command capacity exceeded");
        for (Command<M> command : descriptions) {
            if (!active) return;
            Operation previous = commands.remove(command.key());
            if (previous != null) previous.cancel();
            if (commands.size() >= limit) throw new IllegalStateException("Active command capacity exceeded");
            Operation operation = new Operation();
            commands.put(command.key(), operation);
            Cancellation cancellation = Objects.requireNonNull(command.work().start(message -> {
                if (!active || !operation.valid || commands.get(command.key()) != operation) return;
                commands.remove(command.key());
                operation.cancel();
                dispatch(message);
            }), "Command must return cancellation");
            operation.attach(cancellation);
        }
    }
    private void reconcile() {
        List<Subscription<M>> desired = List.copyOf(program.subscriptions(model));
        if (desired.size() > limit) throw new IllegalStateException("Subscription capacity exceeded");
        Set<String> keys = new LinkedHashSet<>();
        for (Subscription<M> description : desired) if (!keys.add(description.key())) throw new IllegalArgumentException("Duplicate subscription key: " + description.key());
        for (String old : List.copyOf(subscriptions.keySet())) if (!keys.contains(old)) subscriptions.remove(old).cancel();
        for (Subscription<M> description : desired) {
            if (!active || subscriptions.containsKey(description.key())) continue;
            Operation operation = new Operation();
            subscriptions.put(description.key(), operation);
            operation.attach(Objects.requireNonNull(description.source().start(message -> {
                if (active && operation.valid && subscriptions.get(description.key()) == operation) dispatch(message);
            }), "Subscription must return cancellation"));
        }
    }
    private static final class Operation {
        boolean valid = true;
        Cancellation cancellation;
        void attach(Cancellation value) { if (valid) cancellation = value; else value.cancel(); }
        void cancel() {
            if (!valid) return;
            valid = false;
            if (cancellation != null) { Cancellation owned = cancellation; cancellation = null; owned.cancel(); }
        }
    }
}
