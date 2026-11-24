package jx;

import java.util.Objects;
import java.util.function.Supplier;

/** A component that is loaded on first use, with a way to try again after a failed load.
 *
 * <p>{@link #type()} is the descriptor to render. Its loader runs once, when the component first
 * renders; the nearest {@code React.suspense} shows its fallback until the promise is settled, and
 * a failed load reaches the nearest error boundary. The failure is kept, so every later render
 * fails the same way, until {@link #retry()} is called. Declare a lazy component once, outside
 * rendering. */
public final class LazyComponent<P> {
    private final Supplier<Promise<ComponentType<P>>> load;
    private final ComponentType<P> type;
    private Promise<ComponentType<P>> loading;
    private boolean failed;

    private LazyComponent(String name, Supplier<Promise<ComponentType<P>>> load) {
        this.load = Objects.requireNonNull(load);
        this.type = new ComponentType<>(name, props ->
                new View.ComponentView<>(Objects.requireNonNull(Hooks.use(started()), "The lazy component is null"), props, null));
    }

    public static <P> LazyComponent<P> of(String name, Supplier<Promise<ComponentType<P>>> load) { return new LazyComponent<>(name, load); }

    /** The descriptor to render; the same object on every call. */
    public ComponentType<P> type() { return type; }

    /** Forgets a failed load, so that the next render starts the loader again. It does nothing
     * while a load is running or after it succeeded. Call it from an event handler, then render
     * the component again, for example by resetting the error boundary that showed the failure. */
    public void retry() {
        if (failed) {
            failed = false;
            loading = null;
        }
    }

    private Promise<ComponentType<P>> started() {
        if (loading == null) {
            Promise<ComponentType<P>> current = Objects.requireNonNull(load.get(), "The lazy loader returned null");
            loading = current;
            current.whenSettled(loaded -> {}, error -> { if (loading == current) failed = true; });
        }
        return loading;
    }
}
