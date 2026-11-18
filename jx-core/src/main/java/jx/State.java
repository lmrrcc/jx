package jx;

import java.util.Objects;
import java.util.function.UnaryOperator;

/** A render snapshot plus stable dispatch. Calling set/update does not mutate this snapshot.
 * Updaters must be pure: React may evaluate them repeatedly in development. Null values are supported. */
public record State<T>(T value, Setter<T> setter) {
    public State { Objects.requireNonNull(setter); }
    public void set(T next) { setter.update(ignored -> next); }
    public void update(UnaryOperator<T> update) { setter.update(Objects.requireNonNull(update)); }
    @FunctionalInterface public interface Setter<T> { void update(UnaryOperator<T> update); }
}
