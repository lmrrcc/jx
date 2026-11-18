package jx;

import java.util.function.Consumer;

/** This render's reducer snapshot and stable action dispatcher. */
public record ReducerState<S, A>(S value, Consumer<A> dispatch) {
    public ReducerState { java.util.Objects.requireNonNull(dispatch); }
}
