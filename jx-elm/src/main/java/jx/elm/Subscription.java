package jx.elm;

import java.util.Objects;

/** Description of an ongoing message source. The key includes configuration: retaining a key retains
 * its existing source. Removing/changing a key releases the source before starting its replacement. */
public record Subscription<M>(String key, Command.Work<M> source) {
    public Subscription { if (Objects.requireNonNull(key).isBlank()) throw new IllegalArgumentException("Empty subscription key"); Objects.requireNonNull(source); }
}
