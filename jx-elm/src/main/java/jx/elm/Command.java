package jx.elm;

import java.util.Objects;

/** Description of a one-result effect. Work starts only after the model is committed.
 * A newer command with the same key cancels and invalidates its predecessor. Report failures as typed
 * messages; do not throw an asynchronous failure away. Replies after completion/cancellation are ignored. */
public record Command<M>(String key, Work<M> work) {
    public Command { if (Objects.requireNonNull(key).isBlank()) throw new IllegalArgumentException("Empty command key"); Objects.requireNonNull(work); }
    @FunctionalInterface public interface Work<M> { Cancellation start(Dispatch<M> reply); }
}
