package jx;

import java.util.Objects;
import java.util.function.Function;

/** A function used as the {@code action} of a form or the {@code formAction} of a button, in
 * place of a URL. React calls it with the submitted fields inside a transition and resets the
 * form's uncontrolled fields when it has finished. */
@FunctionalInterface
public interface FormAction {
    void run(FormData data);

    /** An asynchronous action: the form stays pending, as {@link Hooks#useFormStatus()} reports,
     * until the returned promise is settled. Usually {@code FormAction.async(data -> Async.run(() -> ...))}. */
    static FormAction async(Function<FormData, Promise<?>> action) { return new OfPromise(action); }

    /** The result of {@link #async}. The renderer hands its promise to React. */
    record OfPromise(Function<FormData, Promise<?>> action) implements FormAction {
        public OfPromise { Objects.requireNonNull(action); }
        /** Runs the action and returns its promise, which must not be null. */
        public Promise<?> start(FormData data) { return Objects.requireNonNull(action.apply(data), "The form action returned null"); }
        @Override public void run(FormData data) { start(data); }
    }
}
