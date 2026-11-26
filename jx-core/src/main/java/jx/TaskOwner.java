package jx;

import java.util.Objects;
import java.util.function.Consumer;

/** One event-loop-owned operation. Invalidate before releasing resources, so
 * reentrant/late/duplicate completion cannot publish into a released owner. */
final class TaskOwner<T, E> implements Cancellation {
    private Consumer<Task.Result<T, E>> completion;
    private Cancellation handle;
    private boolean started;
    private boolean active = true;

    TaskOwner(Consumer<Task.Result<T, E>> completion) {
        this.completion = Objects.requireNonNull(completion);
    }

    void start(Task<T, E> task) {
        Objects.requireNonNull(task);
        if (started) throw new IllegalStateException("Task owner already started");
        started = true;
        if (!active) return;
        try {
            Cancellation returned = Objects.requireNonNull(task.start(this::complete), "Task must return cancellation");
            if (active) handle = returned;
            else returned.cancel();
        } catch (RuntimeException | Error failure) {
            cancel();
            throw failure;
        }
    }

    private void complete(Task.Result<T, E> result) {
        if (!active) return;
        Consumer<Task.Result<T, E>> recipient = completion;
        cancel();
        recipient.accept(Objects.requireNonNull(result, "Task must complete with a result"));
    }

    @Override public void cancel() {
        if (!active) return;
        active = false;
        completion = null;
        Cancellation owned = handle;
        handle = null;
        if (owned != null) owned.cancel();
    }
}
