package jx;

/** Releases an operation owned by an effect, command, subscription or request.
 * Implementations must be idempotent and must not throw. Invoke on the operation's
 * owning thread; this interface does not add synchronization or move work between
 * threads. Cancellation releases local resources but does not roll back work that
 * has already reached a server. Owners must also ignore late results from work
 * that cannot be stopped. Browser HTTP suppresses completion after cancellation.
 */
@FunctionalInterface
public interface Cancellation {
    /** A completed operation or a source with no resources to release. */
    Cancellation NONE = () -> {};

    /** Releases the operation at most once, including after normal completion. */
    void cancel();
}
