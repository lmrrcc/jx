package jx;

import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** Stable identity and checked schemas of a Java-authored React class. Declare once. This
 * explicit descriptor API also underlies compiler-generated native class markup. */
public final class ClassComponentType<P,S,C extends ClassComponent<P,S,?>> {
    public enum Lifecycle { MOUNT, UPDATE, UNMOUNT, SNAPSHOT, SHOULD_UPDATE, CATCH }
    /** Renderer-owned access to the one native context declared by a descriptor. Applications
     * receive a typed reader through the context-aware factory or a value through the update
     * policy, not this adapter. */
    public interface ContextAccess {
        /** Reads the renderer's context source. Factory access supplies a live instance reader
         * usable during construction; should-update access supplies its fixed pending value. */
        <T> Supplier<T> current(Context<T> context);
    }
    /** Pure native should-update policy with the descriptor's concrete context value type. */
    @FunctionalInterface
    public interface ContextUpdatePolicy<P,S,C extends ClassComponent<P,S,?>,T> {
        /** Current values remain on peer; the arguments describe React's pending update.
         * The context value may be null. React may repeat/discard calls or render despite false. */
        boolean shouldUpdate(C peer, P nextProps, S nextState, T nextContext);
    }
    @FunctionalInterface
    private interface ContextPolicy<P,S,C> {
        boolean shouldUpdate(C peer, P nextProps, S nextState, ContextAccess nextContext);
    }
    private final String name;
    private final ClassSchema<P> props;
    private final ClassSchema<S> state;
    private final BiFunction<P,ContextAccess,C> factory;
    private final Context<?> context;
    private final boolean pure;
    private final Set<Lifecycle> lifecycles;
    private final BiFunction<P,S,StatePatch<S>> derivedState;
    private final Function<Throwable,StatePatch<S>> derivedError;
    private final ContextPolicy<P,S,C> contextUpdate;
    private final boolean nullableState;
    private final ClassProps<P> defaultProps;
    private ClassComponentType(String name, ClassSchema<P> props, ClassSchema<S> state,
            BiFunction<P,ContextAccess,C> factory, Context<?> context, boolean pure, Set<Lifecycle> lifecycles) {
        this(name, props, state, factory, context, pure, lifecycles, null, null, null, false, null);
    }
    private ClassComponentType(String name, ClassSchema<P> props, ClassSchema<S> state,
            BiFunction<P,ContextAccess,C> factory, Context<?> context, boolean pure, Set<Lifecycle> lifecycles,
            BiFunction<P,S,StatePatch<S>> derivedState, Function<Throwable,StatePatch<S>> derivedError,
            ContextPolicy<P,S,C> contextUpdate, boolean nullableState, ClassProps<P> defaultProps) {
        if (Objects.requireNonNull(name).isBlank()) throw new IllegalArgumentException("Empty class component name");
        this.name = name; this.props = Objects.requireNonNull(props); this.state = Objects.requireNonNull(state);
        this.factory = Objects.requireNonNull(factory); this.context = context;
        this.pure = pure; this.lifecycles = Set.copyOf(lifecycles);
        this.derivedState = derivedState;
        this.derivedError = derivedError;
        this.contextUpdate = contextUpdate;
        this.nullableState = nullableState;
        if (defaultProps != null && defaultProps.schema() != props)
            throw new IllegalArgumentException("Default props belong to another class schema");
        this.defaultProps = defaultProps;
        if (this.lifecycles.contains(Lifecycle.SNAPSHOT) && !this.lifecycles.contains(Lifecycle.UPDATE))
            throw new IllegalArgumentException("Snapshot lifecycle requires update lifecycle");
    }
    public static <P,S,C extends ClassComponent<P,S,?>> ClassComponentType<P,S,C> normal(String name,
            ClassSchema<P> props, ClassSchema<S> state, Function<P,C> factory, Set<Lifecycle> lifecycles) {
        Objects.requireNonNull(factory);
        return new ClassComponentType<>(name, props, state, (value, ignored) -> factory.apply(value), null, false, lifecycles);
    }
    public static <P,S,C extends PureComponent<P,S,?>> ClassComponentType<P,S,C> pure(String name,
            ClassSchema<P> props, ClassSchema<S> state, Function<P,C> factory, Set<Lifecycle> lifecycles) {
        Objects.requireNonNull(factory);
        return new ClassComponentType<>(name, props, state, (value, ignored) -> factory.apply(value), null, true, lifecycles);
    }
    /** Declares React's native {@code contextType}. The factory receives a live reader of the
     * closest provider value, or the context default when no provider is present. Null values
     * remain null. The reader works during construction, render, lifecycle callbacks and events;
     * it follows the native instance's current context rather than a captured initial value.
     *
     * <p>Keep the reader with its owning Java peer and use it only on the rendering thread.
     * Retaining the peer or reader retains the same native instance as the peer's props/state
     * host, including its last context after unmount. It is not a subscription or disposable
     * resource of its own. React owns context updates, including updates through a class whose
     * should-update method returns false. Context reads do not invoke hooks. */
    public static <P,S,C extends ClassComponent<P,S,?>,T> ClassComponentType<P,S,C> normalWithContext(String name,
            ClassSchema<P> props, ClassSchema<S> state, Context<T> context,
            BiFunction<P,Supplier<T>,C> factory, Set<Lifecycle> lifecycles) {
        Objects.requireNonNull(context); Objects.requireNonNull(factory);
        return new ClassComponentType<>(name, props, state,
                (value, access) -> factory.apply(value, Objects.requireNonNull(access.current(context), "Missing context reader")),
                context, false, lifecycles);
    }
    /** The context-aware variant of {@link #pure}; native context changes bypass PureComponent's
     * props/state bailout. Reader ownership and null behavior match {@link #normalWithContext}. */
    public static <P,S,C extends PureComponent<P,S,?>,T> ClassComponentType<P,S,C> pureWithContext(String name,
            ClassSchema<P> props, ClassSchema<S> state, Context<T> context,
            BiFunction<P,Supplier<T>,C> factory, Set<Lifecycle> lifecycles) {
        Objects.requireNonNull(context); Objects.requireNonNull(factory);
        return new ClassComponentType<>(name, props, state,
                (value, access) -> factory.apply(value, Objects.requireNonNull(access.current(context), "Missing context reader")),
                context, true, lifecycles);
    }
    public String name() { return name; }
    /** Returns a distinct descriptor with native React class default properties. Declare
     * it once: descriptor identity controls reconciliation. The immutable input must belong
     * to this descriptor's exact props schema. Null removes defaults in the returned copy.
     *
     * <p>React supplies defaults only for absent or undefined input properties; explicit null,
     * false, zero and empty strings remain values. Reference defaults retain their identity.
     * Inputs and defaults are never merged into Java records by this API: the renderer sets
     * the native constructor's defaultProps before element creation, and the strict props
     * schema validates the resolved record before Java construction/lifecycle dispatch.
     * Undefined defaults may still leave required fields unresolved and therefore invalid.
     *
     * <p>Use {@link Views#classPropsView(ClassComponentType, ClassProps)} for input that
     * intentionally omits required fields. The original descriptor remains unchanged;
     * factory, context, lifecycle, derivation settings are retained. This
     * immutable contract does not expose native defaultProps mutation or reassignment.
     */
    public ClassComponentType<P,S,C> withDefaultProps(ClassProps<P> defaults) {
        return new ClassComponentType<>(name, props, state, factory, context, pure, lifecycles,
                derivedState, derivedError, contextUpdate, nullableState, defaults);
    }
    /** Native default-property input, or null when no defaults are declared. */
    public ClassProps<P> defaultProps() { return defaultProps; }
    /** Returns a distinct descriptor permitting null aggregate state. Declare the result
     * once to keep native component identity stable. Props and the state schema itself
     * remain non-null contracts; non-null state objects still validate every declared field.
     * Use optional fields with explicit Presence values when React's first partial merge
     * may leave fields absent. A required field is never filled with a synthetic default.
     *
     * <p>Null initial state reaches constructors, rendering, updaters, derivation and lifecycle
     * callbacks as Java null. A null patch preserves state; an empty patch merges to an
     * object, including from null. This does not offer resetting aggregate state to null.
     * No checkpoint/serialization support or compiler-generated sparse record is implied.
     * Existing factory, context, lifecycle and derivation settings are preserved. */
    public ClassComponentType<P,S,C> withNullableState() {
        return new ClassComponentType<>(name, props, state, factory, context, pure, lifecycles,
                derivedState, derivedError, contextUpdate, true, defaultProps);
    }
    /** Whether null aggregate state is accepted; properties have separate presence rules. */
    public boolean nullableState() { return nullableState; }
    /** Renderer validation of aggregate state and all present fields. Null is accepted only
     * by opt-in; required fields remain strict in every non-null state snapshot. */
    public void validateState(S value) {
        if (value != null || !nullableState) state.validate(value);
    }
    /** Returns a new descriptor with React's static {@code getDerivedStateFromProps}.
     * Declare the result once: descriptors identify native component types, so creating one
     * during render changes component identity. The original descriptor is unchanged.
     *
     * <p>The pure callback receives next props and pending state after queued state updates.
     * React invokes it after construction and when React processes updates, before the native
     * render policy. It may repeat/discard calls. It has no component instance and must not
     * perform effects, schedule updates or read hooks. Return a patch owned by this descriptor's
     * state schema; omitted fields survive, explicit nullable fields may become null, and a
     * null result leaves pending state unchanged. An empty patch remains an object merge.
     * Native Pure comparison and should-update run after derivation; rejecting a render does
     * not reject the derived state. Exceptions propagate to React's error handling.
     *
     * <p>Context/factory/lifecycle settings are retained. Calling this on an already configured
     * descriptor replaces its derivation in the new descriptor. Use the renderer's single
     * JavaScript thread only.
     * @throws NullPointerException when callback is null
     */
    public ClassComponentType<P,S,C> withDerivedStateFromProps(BiFunction<P,S,StatePatch<S>> callback) {
        return new ClassComponentType<>(name, props, state, factory, context, pure, lifecycles,
                Objects.requireNonNull(callback, "callback"), derivedError, contextUpdate, nullableState, defaultProps);
    }
    /** Renderer capability check; no callback is executed. */
    public boolean hasDerivedStateFromProps() { return derivedState != null; }
    /** Renderer dispatch of the static derivation. Inputs and patch ownership are checked.
     * @return a schema-owned partial patch, or null for no derived change
     * @throws IllegalStateException when no derivation was configured
     * @throws IllegalArgumentException when the callback returns another schema's patch
     */
    public StatePatch<S> deriveStateFromProps(P nextProps, S pendingState) {
        if (derivedState == null) throw new IllegalStateException("No derived state callback configured");
        props.validate(nextProps); validateState(pendingState);
        StatePatch<S> patch = derivedState.apply(nextProps, pendingState);
        if (patch != null && patch.schema() != state) throw new IllegalArgumentException("Derived state patch belongs to another class schema");
        return patch;
    }
    /** Returns a new descriptor with native static getDerivedStateFromError. Declare the result
     * once to retain component identity. The original descriptor and all other settings remain
     * unchanged. Repeating this operation replaces the callback in the returned descriptor.
     *
     * <p>The callback is pure render-phase work, without a peer instance, and can be repeated or
     * discarded by React. Return a patch owned by this descriptor's state schema to render the
     * fallback. Null means no derived change; omitted fields survive the native merge. Java
     * failures preserve Throwable identity; the renderer documents foreign JavaScript failure
     * projection. Reporting effects belong in componentDidCatch with Lifecycle.CATCH instead.
     * React determines which descendant failures reach the boundary and propagates failures
     * thrown by this callback to a parent boundary. Server-render errors are not caught here.
     */
    public ClassComponentType<P,S,C> withDerivedStateFromError(Function<Throwable,StatePatch<S>> callback) {
        return new ClassComponentType<>(name, props, state, factory, context, pure, lifecycles,
                derivedState, Objects.requireNonNull(callback, "callback"), contextUpdate, nullableState, defaultProps);
    }
    /** Renderer capability check; no callback is executed. */
    public boolean hasDerivedStateFromError() { return derivedError != null; }
    /** Renderer dispatch of the pure error derivation, with checked patch ownership.
     * @throws IllegalStateException when no error derivation was configured
     * @throws IllegalArgumentException when the callback returns another schema's patch */
    public StatePatch<S> deriveStateFromError(Throwable error) {
        if (derivedError == null) throw new IllegalStateException("No derived error callback configured");
        StatePatch<S> patch = derivedError.apply(Objects.requireNonNull(error, "error"));
        if (patch != null && patch.schema() != state) throw new IllegalArgumentException("Derived error patch belongs to another class schema");
        return patch;
    }
    /** Returns a new descriptor with a typed native shouldComponentUpdate policy. Declare it
     * once: descriptor identity is native component identity. The requested context must be
     * this descriptor's contextType, by identity. A PureComponent can supply a custom policy;
     * React then uses it instead of shallow comparison and emits its development warning.
     * Existing factory, context, derivations and lifecycle settings are preserved.
     *
     * <p>This policy takes precedence over the peer's two-argument shouldComponentUpdate,
     * including when SHOULD_UPDATE was registered. The original descriptor still uses its
     * original policy. Repeating this modifier replaces the policy on the returned descriptor.
     * The policy automatically enables native should-update dispatch; no capability flag is
     * required. React does not invoke it on initial render or forceUpdate, and a changed native
     * provider value can force rendering even when the policy returns false.
     *
     * <p>The callback receives the peer with its current props/state/context and separate next
     * values, including null context. It is pure render-phase work, may repeat or be discarded,
     * must not schedule updates or effects, and runs only on the rendering thread. No instance
     * value is eagerly changed by this adapter. Exceptions propagate through React. The next
     * context is a borrowed value, not a live reader; retaining it retains that value normally.
     * @throws NullPointerException if requested or policy is null
     * @throws IllegalArgumentException if context identity differs
     */
    public <T> ClassComponentType<P,S,C> withContextShouldUpdate(Context<T> requested,
            ContextUpdatePolicy<P,S,C,T> policy) {
        Objects.requireNonNull(requested, "requested"); Objects.requireNonNull(policy, "policy");
        if (requested != context) throw new IllegalArgumentException("Update policy context does not belong to this descriptor");
        return new ClassComponentType<>(name, props, state, factory, context, pure, lifecycles,
                derivedState, derivedError, (peer, nextProps, nextState, nextContext) ->
                    policy.shouldUpdate(peer, nextProps, nextState,
                            Objects.requireNonNull(nextContext.current(requested), "Missing next context reader").get()), nullableState, defaultProps);
    }
    /** Renderer capability check; no callback is executed. */
    public boolean hasContextShouldUpdate() { return contextUpdate != null; }
    /** Renderer dispatch with checked pending props/state. Context access belongs to this
     * callback's pending native value, not the peer's current reader. Without a configured
     * context policy, dispatches the original two-argument peer override and ignores access.
     * @throws NullPointerException for a null peer or missing required context access
     */
    public boolean shouldUpdate(C peer, P nextProps, S nextState, ContextAccess nextContext) {
        Objects.requireNonNull(peer, "peer"); props.validate(nextProps); validateState(nextState);
        return contextUpdate == null ? peer.shouldUpdate(nextProps, nextState)
                : contextUpdate.shouldUpdate(peer, nextProps, nextState, Objects.requireNonNull(nextContext, "Missing next context access"));
    }
    public ClassSchema<P> propsSchema() { return props; }
    public ClassSchema<S> stateSchema() { return state; }
    public boolean pure() { return pure; }
    /** The declared native context, or null for a descriptor without class context. */
    public Context<?> contextType() { return context; }
    public boolean lifecycle(Lifecycle lifecycle) {
        return lifecycles.contains(lifecycle) || lifecycle == Lifecycle.SHOULD_UPDATE && contextUpdate != null;
    }
    /** Renderer construction. Initial props/state are checked before mounting. */
    public C create(P value) {
        if (context != null) throw new IllegalStateException("A context-aware class needs its renderer context access");
        return create(value, null);
    }
    /** Renderer construction with current native context already available. The renderer must
     * supply context access for context-aware descriptors; ordinary descriptors ignore it. */
    public C create(P value, ContextAccess access) {
        props.validate(value);
        if (context != null) Objects.requireNonNull(access, "Missing renderer context access");
        C peer = Objects.requireNonNull(factory.apply(value, access), "Class factory returned null");
        if ((peer instanceof PureComponent<?,?,?>) != pure) throw new IllegalArgumentException("Class peer kind disagrees with its descriptor");
        validateState(peer.initialState());
        return peer;
    }
}
