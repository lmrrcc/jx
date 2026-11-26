package jx;

import java.util.Objects;

/** Immutable result snapshot returned by {@link Hooks#useResource}.
 * Loading carries no previous key's value. Ready values follow the task's
 * nullability; failed snapshots preserve the original typed error. */
public sealed interface Resource<T, E> permits Resource.Loading, Resource.Ready, Resource.Failed {
    record Loading<T, E>() implements Resource<T, E> {}
    record Ready<T, E>(T value) implements Resource<T, E> {}
    record Failed<T, E>(E error) implements Resource<T, E> {
        public Failed { Objects.requireNonNull(error, "Resource failure must have an error"); }
    }
}
