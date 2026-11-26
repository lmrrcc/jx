package jx;

import java.util.Objects;
import java.util.function.BiFunction;

/** Java peer of an actual React class instance. Browser-main-thread only. Props/state are
 * immutable values; React owns update evaluation and commit timing. Constructors, render and
 * updater functions must be pure and may be repeated/discarded. Hooks are not allowed here.
 * Class state resets on hot replacement by default. The limited portable opt-in is
 * {@link ClassComponentType#withStateCheckpoint}; peers never cross TeaVM generations. */
public abstract class ClassComponent<P,S,X> {
    /** Renderer-owned instance access; applications do not implement or bind this adapter. */
    public interface Host<P,S> {
        P props(); S state();
        void update(BiFunction<S,P,StatePatch<S>> updater, Runnable committed);
        void force(Runnable committed);
    }
    private P initialProps;
    private S initialState;
    private Host<P,S> host;
    /** Constructs a peer. Null initial state requires the descriptor's withNullableState
     * opt-in; the renderer checks this at construction before mounting. Props remain non-null. */
    protected ClassComponent(P props, S state) { initialProps = Objects.requireNonNull(props); initialState = state; }
    /** Current native instance values, including updates whose render was skipped. Repeated
     * reads of the same current native snapshot reuse the decoded record. Historical snapshot
     * identity after disposal/concurrent discard is not guaranteed. Declared reference fields
     * retain their original identity. */
    public final P props() { return host == null ? initialProps : host.props(); }
    public final S state() { return host == null ? initialState : host.state(); }
    /** Renderer initialization handoff, available only before binding. */
    public final S initialState() {
        if (host != null) throw new IllegalStateException("Initial class state has already been handed to the renderer");
        return initialState;
    }
    /** Renderer entry point. A peer can belong to only one native instance. */
    public final void bind(Host<P,S> host) {
        if (this.host != null) throw new IllegalStateException("Class peer already bound");
        this.host = Objects.requireNonNull(host);
        initialProps = null;
        initialState = null;
    }
    private Host<P,S> host() {
        if (host == null) throw new IllegalStateException("Class updates are not allowed during construction");
        return host;
    }
    /** Queues a partial state patch. A null patch preserves pending state; an empty patch
     * still produces a merged state object. Java's overload resolution requires a typed
     * null (for example a {@code StatePatch<S>} variable), because an updater overload also
     * exists. A null patch does not enable nullable state or bypass field validation. */
    public final void setState(StatePatch<S> patch) { setState(patch, null); }
    /** Queues a nullable partial patch and an optional callback. React commits the callback
     * even when the patch is null and no render is needed; it never runs synchronously here.
     * Other pending updates, derived state or changed props/context may still cause a render. */
    public final void setState(StatePatch<S> patch, Runnable committed) {
        host().update((state, props) -> patch, committed);
    }
    public final void setState(BiFunction<S,P,StatePatch<S>> updater) { setState(updater, null); }
    /** Queues a non-null pure updater over pending state and next props. A null result means
     * no state change; the updater itself must be non-null. Optional callbacks run after
     * commit, including for a null result, never while evaluating the updater. */
    public final void setState(BiFunction<S,P,StatePatch<S>> updater, Runnable committed) {
        host().update(Objects.requireNonNull(updater), committed);
    }
    /** Schedules native forceUpdate, bypassing shouldComponentUpdate. */
    public final void forceUpdate(Runnable committed) { host().force(committed); }
    public final void forceUpdate() { forceUpdate(null); }
    /** Pure rendered description, or null to render nothing while retaining this instance. */
    public abstract View render();
    protected void componentDidMount() {}
    protected void componentDidUpdate(P previousProps, S previousState, X snapshot) {}
    protected void componentWillUnmount() {}
    /** Deprecated native render-phase callbacks. Declare each matching descriptor capability;
     * React controls ordering, warnings, retries and suppression by modern lifecycles.
     * They do not prove a commit and may run for discarded work. Prefer committed callbacks
     * for effects. Queued will-mount/receive-props updates still use native setState. */
    protected void componentWillMount() {}
    protected void UNSAFE_componentWillMount() {}
    protected void componentWillReceiveProps(P nextProps) {}
    protected void UNSAFE_componentWillReceiveProps(P nextProps) {}
    protected void componentWillUpdate(P nextProps, S nextState) {}
    protected void UNSAFE_componentWillUpdate(P nextProps, S nextState) {}
    /** Committed report of a descendant failure caught by this native boundary. The descriptor
     * must declare CATCH. Java failures retain their original Throwable identity; renderers
     * explicitly project foreign failures. This callback may perform reporting effects.
     * React does not route this component's own failures or event-handler errors here.
     * Recovery state belongs in the descriptor's pure derived-error callback. */
    protected void componentDidCatch(Throwable error, ComponentErrorInfo info) {}
    protected X getSnapshotBeforeUpdate(P previousProps, S previousState) { return null; }
    protected boolean shouldComponentUpdate(P nextProps, S nextState) { return true; }
    /** Renderer lifecycle dispatch. Register the corresponding descriptor capability. */
    public final void mounted() { componentDidMount(); }
    public final void unmounting() { componentWillUnmount(); }
    /** Renderer dispatch of one distinct native legacy spelling, never a commit signal. */
    public final void beforeMount(ClassComponentType.LegacyVariant variant) {
        switch (variant) {
            case DEPRECATED -> componentWillMount();
            case UNSAFE -> UNSAFE_componentWillMount();
        }
    }
    /** Renderer dispatch; current accessors remain current while arguments are pending. */
    public final void beforeReceiveProps(ClassComponentType.LegacyVariant variant, P nextProps) {
        switch (variant) {
            case DEPRECATED -> componentWillReceiveProps(nextProps);
            case UNSAFE -> UNSAFE_componentWillReceiveProps(nextProps);
        }
    }
    /** Renderer dispatch; React may suppress this callback or discard its work. */
    public final void beforeUpdate(ClassComponentType.LegacyVariant variant, P nextProps, S nextState) {
        switch (variant) {
            case DEPRECATED -> componentWillUpdate(nextProps, nextState);
            case UNSAFE -> UNSAFE_componentWillUpdate(nextProps, nextState);
        }
    }
    /** Renderer dispatch of committed error reporting. */
    public final void caught(Throwable error, ComponentErrorInfo info) {
        componentDidCatch(Objects.requireNonNull(error), Objects.requireNonNull(info));
    }
    public final boolean shouldUpdate(P nextProps, S nextState) { return shouldComponentUpdate(nextProps, nextState); }
    public final void updated(P previousProps, S previousState) { componentDidUpdate(previousProps, previousState, null); }
    /** Captures the typed snapshot now; the returned callback consumes it after DOM commit. */
    public final Runnable snapshot(P previousProps, S previousState) {
        X snapshot = getSnapshotBeforeUpdate(previousProps, previousState);
        return () -> componentDidUpdate(previousProps, previousState, snapshot);
    }
}
