package jx;

import java.util.Objects;

/** Thrown by {@link Async#await(Promise)} when the promise failed with a checked exception,
 * which is the {@linkplain #getCause() cause}. Unchecked failures are rethrown as they are. */
public final class AsyncException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public AsyncException(Throwable cause) { super(Objects.requireNonNull(cause)); }
}
