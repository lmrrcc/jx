package jx;

import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** Stable identity and checked schemas of a Java-authored React class. Declare once. This
 * explicit descriptor API also underlies compiler-generated native class markup. */
public final class ClassComponentType<P,S,C extends ClassComponent<P,S,?>> {
    public enum Lifecycle { MOUNT, UPDATE, UNMOUNT, SNAPSHOT, SHOULD_UPDATE }
    /** Renderer-owned access to the one native context declared by a descriptor. Applications
     * receive the resulting typed reader through the context-aware factory, not this adapter. */
    public interface ContextAccess {
        /** A live reader of the declared context, usable during Java peer construction. */
        <T> Supplier<T> current(Context<T> context);
    }
    private final String name;
    private final ClassSchema<P> props;
    private final ClassSchema<S> state;
    private final BiFunction<P,ContextAccess,C> factory;
    private final Context<?> context;
    private final boolean pure;
    private final Set<Lifecycle> lifecycles;
    private final BiFunction<P,S,StatePatch<S>> derivedState;
    private ClassComponentType(String name, ClassSchema<P> props, ClassSchema<S> state,
            BiFunction<P,ContextAccess,C> factory, Context<?> context, boolean pure, Set<Lifecycle> lifecycles) {
        this(name, props, state, factory, context, pure, lifecycles, null);
    }
    private ClassComponentType(String name, ClassSchema<P> props, ClassSchema<S> state,
            BiFunction<P,ContextAccess,C> factory, Context<?> context, boolean pure, Set<Lifecycle> lifecycles,
            BiFunction<P,S,StatePatch<S>> derivedState) {
        if (Objects.requireNonNull(name).isBlank()) throw new IllegalArgumentException("Empty class component name");
        this.name = name; this.props = Objects.requireNonNull(props); this.state = Objects.requireNonNull(state);
        this.factory = Objects.requireNonNull(factory); this.context = context;
        this.pure = pure; this.lifecycles = Set.copyOf(lifecycles);
        this.derivedState = derivedState;
        if (pure && this.lifecycles.contains(Lifecycle.SHOULD_UPDATE)) throw new IllegalArgumentException("PureComponent uses native shallow comparison");
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
                Objects.requireNonNull(callback, "callback"));
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
        props.validate(nextProps); state.validate(pendingState);
        StatePatch<S> patch = derivedState.apply(nextProps, pendingState);
        if (patch != null && patch.schema() != state) throw new IllegalArgumentException("Derived state patch belongs to another class schema");
        return patch;
    }
    public ClassSchema<P> propsSchema() { return props; }
    public ClassSchema<S> stateSchema() { return state; }
    public boolean pure() { return pure; }
    /** The declared native context, or null for a descriptor without class context. */
    public Context<?> contextType() { return context; }
    public boolean lifecycle(Lifecycle lifecycle) { return lifecycles.contains(lifecycle); }
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
        state.validate(peer.initialState());
        return peer;
    }
}
