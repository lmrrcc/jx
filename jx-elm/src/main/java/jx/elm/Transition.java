package jx.elm;

import java.util.List;
import java.util.Objects;

/** Immutable transition description. The model itself must be immutable. */
public record Transition<S, M>(S model, List<Command<M>> commands) {
    public Transition { Objects.requireNonNull(model); commands = List.copyOf(commands); }
    public static <S, M> Transition<S, M> stay(S model) { return new Transition<>(model, List.of()); }
}
