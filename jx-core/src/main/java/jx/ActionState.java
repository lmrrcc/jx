package jx;

import java.util.Objects;

/** The result of {@link Hooks#useActionState}: the state the last finished action returned, the
 * action to give to a form or button, and whether a call is still running. Calls run one after
 * the other, each with the state the previous one returned. */
public record ActionState<S>(S state, FormAction action, boolean pending) {
    public ActionState { Objects.requireNonNull(action); }
}
