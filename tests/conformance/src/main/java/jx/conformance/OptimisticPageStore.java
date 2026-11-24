package jx.conformance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jx.ExternalStore;
import jx.Promise;

/** One mounted page's immutable data and manually settled requests; no static state, timers or I/O.
 * Explicit close settles both owned gates; this fixture does not provide framework-level request cancellation. */
public final class OptimisticPageStore implements ExternalStore<List<OptimisticPageStore.Item>>, AutoCloseable {
    public record Item(String id, String label, boolean optimistic) {
        public Item { Objects.requireNonNull(id); Objects.requireNonNull(label); }
    }

    /** The expected request rejection, caught inside the transition's asynchronous action. */
    public static final class RequestFailure extends RuntimeException {
        public RequestFailure(String message) { super(message); }
    }

    /** Two event gates: server response, then completion after the committed snapshot is visible. */
    public static final class Attempt {
        private Promise.Resolver<Item> responseResolver;
        private Promise.Resolver<Void> completionResolver;
        private final Promise<Item> response = Promise.create(resolver -> responseResolver = resolver);
        private final Promise<Void> completion = Promise.create(resolver -> completionResolver = resolver);

        public Promise<Item> response() { return response; }
        public Promise<Void> completion() { return completion; }
    }

    private final List<Item> initial = List.of(new Item("seed", "Seed", false));
    private List<Item> value = initial;
    private final List<Runnable> listeners = new ArrayList<>();
    private Attempt active;
    private boolean closed;

    @Override public List<Item> snapshot() { return value; }
    @Override public List<Item> serverSnapshot() { return initial; }

    @Override public Runnable subscribe(Runnable listener) {
        requireOpen();
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }

    /** A pure optimistic reducer: replay against the latest base, retaining a committed item with the same id. */
    public static List<Item> appendMissing(List<Item> current, Item item) {
        if (current.stream().anyMatch(existing -> existing.id().equals(item.id()))) return current;
        var next = new ArrayList<>(current);
        next.add(item);
        return List.copyOf(next);
    }

    public void publishRemote() { publish(new Item("remote", "Remote", false)); }

    public void commit(Item confirmed) {
        if (confirmed.optimistic()) throw new IllegalArgumentException("Cannot commit an optimistic item");
        publish(confirmed);
    }

    private void publish(Item item) {
        requireOpen();
        List<Item> next = appendMissing(value, item);
        if (next == value) return;
        value = next;
        for (Runnable listener : List.copyOf(listeners)) if (listeners.contains(listener)) listener.run();
    }

    public Attempt begin() {
        requireOpen();
        if (active != null) throw new IllegalStateException("A request is already pending");
        active = new Attempt();
        return active;
    }

    public void accept() { request().responseResolver.resolve(new Item("local", "Local", false)); }
    public void reject() { request().responseResolver.reject(new RequestFailure("rejected")); }
    public void finishAction() { request().completionResolver.resolve(null); }

    /** Releases this request even after a rejection or page close. */
    public void end(Attempt attempt) {
        if (active != attempt) throw new IllegalStateException("Request ownership changed");
        // Rejection skips the second await, so settle that unused gate as well.
        attempt.completionResolver.resolve(null);
        active = null;
    }

    private Attempt request() {
        requireOpen();
        if (active == null) throw new IllegalStateException("No request is pending");
        return active;
    }

    public int subscribers() { return listeners.size(); }
    public int requests() { return active == null ? 0 : 1; }
    public boolean closed() { return closed; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (active != null) {
            active.responseResolver.reject(new RequestFailure("closed"));
            active.completionResolver.resolve(null);
        }
        // useSyncExternalStore owns listener cleanup; do not clear it here and hide a leaked subscription.
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("The page fixture is closed");
    }
}
