package jx;

import java.util.Objects;
import java.util.function.Consumer;

/** The result of {@link Hooks#useTransition()}: whether a transition of this component is still
 * pending, and the way to start one. Updates made inside a transition are not urgent: React keeps
 * the current screen responsive and may interrupt them. */
public record TransitionState(boolean pending, Starter starter) {
    public TransitionState { Objects.requireNonNull(starter); }

    /** Starts transitions. The same starter is returned on every render of a mounted component. */
    public interface Starter {
        void start(Runnable action);
        void startAsync(Consumer<Runnable> action);
    }

    /** Runs {@code action} now and marks the state updates it makes as a transition. */
    public void start(Runnable action) { starter.start(Objects.requireNonNull(action)); }

    /** Starts an asynchronous action, React's async transition. {@code action} runs now and
     * receives a completion callback; {@link #pending()} stays true until that callback is called.
     * State updates made after the action returned belong to the transition only when they are
     * wrapped in a transition again, as after an {@code await} in React. Always call the
     * callback, also on failure, or the transition stays pending. */
    public void startAsync(Consumer<Runnable> action) { starter.startAsync(Objects.requireNonNull(action)); }
}
