package jx;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Stable identity and checked schemas of a Java-authored React class. Declare once. This
 * explicit descriptor API precedes compiler-generated class markup support. */
public final class ClassComponentType<P,S,C extends ClassComponent<P,S,?>> {
    public enum Lifecycle { MOUNT, UPDATE, UNMOUNT, SNAPSHOT, SHOULD_UPDATE }
    private final String name;
    private final ClassSchema<P> props;
    private final ClassSchema<S> state;
    private final Function<P,C> factory;
    private final boolean pure;
    private final Set<Lifecycle> lifecycles;
    private ClassComponentType(String name, ClassSchema<P> props, ClassSchema<S> state,
            Function<P,C> factory, boolean pure, Set<Lifecycle> lifecycles) {
        if (Objects.requireNonNull(name).isBlank()) throw new IllegalArgumentException("Empty class component name");
        this.name = name; this.props = Objects.requireNonNull(props); this.state = Objects.requireNonNull(state);
        this.factory = Objects.requireNonNull(factory); this.pure = pure; this.lifecycles = Set.copyOf(lifecycles);
        if (pure && this.lifecycles.contains(Lifecycle.SHOULD_UPDATE)) throw new IllegalArgumentException("PureComponent uses native shallow comparison");
        if (this.lifecycles.contains(Lifecycle.SNAPSHOT) && !this.lifecycles.contains(Lifecycle.UPDATE))
            throw new IllegalArgumentException("Snapshot lifecycle requires update lifecycle");
    }
    public static <P,S,C extends ClassComponent<P,S,?>> ClassComponentType<P,S,C> normal(String name,
            ClassSchema<P> props, ClassSchema<S> state, Function<P,C> factory, Set<Lifecycle> lifecycles) {
        return new ClassComponentType<>(name, props, state, factory, false, lifecycles);
    }
    public static <P,S,C extends PureComponent<P,S,?>> ClassComponentType<P,S,C> pure(String name,
            ClassSchema<P> props, ClassSchema<S> state, Function<P,C> factory, Set<Lifecycle> lifecycles) {
        return new ClassComponentType<>(name, props, state, factory, true, lifecycles);
    }
    public String name() { return name; }
    public ClassSchema<P> propsSchema() { return props; }
    public ClassSchema<S> stateSchema() { return state; }
    public boolean pure() { return pure; }
    public boolean lifecycle(Lifecycle lifecycle) { return lifecycles.contains(lifecycle); }
    /** Renderer construction. Initial props/state are checked before mounting. */
    public C create(P value) {
        props.validate(value);
        C peer = Objects.requireNonNull(factory.apply(value), "Class factory returned null");
        if ((peer instanceof PureComponent<?,?,?>) != pure) throw new IllegalArgumentException("Class peer kind disagrees with its descriptor");
        state.validate(peer.initialState());
        return peer;
    }
}
