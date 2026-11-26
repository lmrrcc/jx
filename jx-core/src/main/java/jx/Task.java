package jx;

import java.util.Objects;
import java.util.function.Consumer;

/** Description of one asynchronous result, with a typed failure channel.
 * Constructing a task performs no work. Each {@link #start} owns a separate
 * operation and returns its cancellation handle, including for synchronous
 * completion. Call and complete on the owner's thread; no scheduler is implied.
 * A task must report at most one result and release any resources it acquires if
 * start throws before returning a handle. Unexpected programming exceptions are
 * not converted to declared failures. Cancellation does not undo remote writes.
 */
@FunctionalInterface
public interface Task<T, E> {
    /** Starts work outside rendering. The nonnull completion may run before this
     * method returns. The returned handle must be nonnull and idempotent. */
    Cancellation start(Consumer<Result<T, E>> completion);

    /** A successful value or a declared, typed failure. */
    sealed interface Result<T, E> permits Success, Failure {}

    /** Successful completion; the value may be null when the task's schema permits it.
     * Payloads are not copied: publish immutable values or transfer ownership and
     * stop mutating them after completion. */
    record Success<T, E>(T value) implements Result<T, E> {}

    /** A declared domain/transport failure. Keep timeout and transport categories
     * in E when the source distinguishes them; an error must not be null. */
    record Failure<T, E>(E error) implements Result<T, E> {
        public Failure { Objects.requireNonNull(error, "Task failure must have an error"); }
    }
}
