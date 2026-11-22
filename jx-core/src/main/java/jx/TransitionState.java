package jx;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The result of {@link Hooks#useTransition()}: whether a transition of this component is still
 * pending, and the way to start one. Updates made inside a transition are not urgent: React keeps
 * the current screen responsive and may interrupt them. */
public record TransitionState(boolean pending, Starter starter) {
    public TransitionState { Objects.requireNonNull(starter); }

    /** Starts transitions. The same starter is returned on every render of a mounted component. */
    public interface Starter {
        void start(Runnable action);
        void startAsync(Consumer<Runnable> action);
        void startPromise(Supplier<? extends Promise<?>> action);
    }

    /** Runs {@code action} now and marks the state updates it makes as a transition. */
    public void start(Runnable action) { starter.start(Objects.requireNonNull(action)); }

    /** Starts an asynchronous action, React's async transition. {@code action} runs now and
     * receives a completion callback; {@link #pending()} stays true until that callback is called.
     * State updates made after the action returned belong to the transition only when they are
     * wrapped in a transition again, as after an {@code await} in React. Always call the
     * callback, also on failure, or the transition stays pending. */
    public void startAsync(Consumer<Runnable> action) { starter.startAsync(Objects.requireNonNull(action)); }

    /** Starts an asynchronous action that reports its end with a promise, like an async function
     * passed to React's {@code startTransition}. {@code action} runs now; {@link #pending()} stays
     * true until the promise it returns is settled. With {@link Async#run(Async.Action)} the
     * updates made before the first {@code await} belong to the transition; later ones only when
     * they are wrapped in a transition again. A failed promise is reported like an error thrown
     * while rendering. */
    public void startAsync(Supplier<? extends Promise<?>> action) { starter.startPromise(Objects.requireNonNull(action)); }
}
