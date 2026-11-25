package jx.conformance;

import static jx.Async.await;

import java.util.Objects;
import jx.Async;
import jx.FormData;
import jx.Promise;

/** Component-generation-owned gates for real React form actions. Browser thread only;
 * disposal settles the current gate and prevents disposed actions from producing results. */
public final class ActionQueueControl {
    private static final class Mount {
        private boolean disposed;
        private Promise.Resolver<Void> pending;
    }

    private final int generation;
    private int started;
    private Mount mount;

    public ActionQueueControl(int generation) { this.generation = generation; }

    /** Starts a committed mount period, including a fresh period after Strict Mode effect replay. */
    public void activate() {
        if (mount != null && !mount.disposed) throw new IllegalStateException("Action owner is already mounted");
        mount = new Mount();
    }

    /** Idempotent cleanup. Invalidate ownership before resolving the local waiter. */
    public void dispose() {
        if (mount == null || mount.disposed) return;
        mount.disposed = true;
        if (mount.pending != null) takePending().resolve(null);
    }

    /** Reads the submission when React starts its action, then awaits explicit completion. */
    public Promise<Integer> submit(int previous, FormData data) {
        Mount owner = mount;
        // React may drain already queued callbacks after the form has unmounted.
        if (owner == null || owner.disposed) {
            Bench.log("action ignored " + generation);
            return Promise.resolved(previous);
        }
        if (owner.pending != null) throw new IllegalStateException("React started concurrent queued actions");
        String text = Objects.requireNonNull(data.get("step"), "Missing step");
        int step = Integer.parseInt(text);
        int call = ++started;
        Bench.log("queue start " + generation + ":" + call + " previous=" + previous + " step=" + text);
        Promise<Void> gate = Promise.create(resolver -> owner.pending = resolver);
        return Async.run(() -> {
            await(gate);
            if (owner.disposed) {
                Bench.log("action cancelled " + generation + ":" + call);
                return previous;
            }
            int next = Math.addExact(previous, step);
            Bench.log("queue finish " + generation + ":" + call + " total=" + next);
            return next;
        });
    }

    public void release() { takePending().resolve(null); }

    public void fail() { takePending().reject(new IllegalStateException("queued action failed")); }

    private Promise.Resolver<Void> takePending() {
        Mount owner = Objects.requireNonNull(mount, "No action owner is mounted");
        Promise.Resolver<Void> resolver = Objects.requireNonNull(owner.pending, "No queued action is waiting");
        owner.pending = null;
        return resolver;
    }
}
