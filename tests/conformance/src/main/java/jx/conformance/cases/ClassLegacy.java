package jx.conformance.cases;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import jx.*;
import jx.conformance.Bench;
import jx.react.React;
import static jx.ClassComponentType.LegacyVariant.*;
import static jx.ClassComponentType.Lifecycle.*;

/** Explicit typed descriptors; native legacy declaration sugar is a separate compiler slice. */
public final class ClassLegacy {
    private ClassLegacy() {}
    public record Props(String id, int phase, boolean allow) {}
    public record Model(int count) {}
    private static final ClassSchema.Field<Props,String> ID = ClassSchema.field("id", ClassSchema.STRING, Props::id);
    private static final ClassSchema.Field<Props,Integer> PHASE = ClassSchema.field("phase", ClassSchema.INTEGER, Props::phase);
    private static final ClassSchema.Field<Props,Boolean> ALLOW = ClassSchema.field("allow", ClassSchema.BOOLEAN, Props::allow);
    private static final ClassSchema<Props> PROPS = new ClassSchema<>(List.of(ID, PHASE, ALLOW), reader -> new Props(reader.get(ID), reader.get(PHASE), reader.get(ALLOW)));
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(COUNT), reader -> new Model(reader.get(COUNT)));
    private static final Context<String> THEME = new Context<>("day");
    private static final Set<ClassComponentType.Lifecycle> LEGACY = Set.of(WILL_MOUNT, UNSAFE_WILL_MOUNT,
            WILL_RECEIVE_PROPS, UNSAFE_WILL_RECEIVE_PROPS, WILL_UPDATE, UNSAFE_WILL_UPDATE, MOUNT, UPDATE);
    private static final ClassRef<Ordinary> NORMAL_REF = new ClassRef<>(value -> {
        if (value instanceof Ordinary peer) return peer;
        throw new IllegalArgumentException("Expected ordinary legacy peer");
    });
    public static final class Ordinary extends ClassComponent<Props,Model,Void> {
        final Supplier<String> theme;
        Ordinary(Props props, Supplier<String> theme) { super(props, new Model(0)); this.theme = theme; }
        protected void componentWillMount() { mounting(this, theme, "old", 1); }
        protected void UNSAFE_componentWillMount() { mounting(this, theme, "unsafe", 10); }
        protected void componentWillReceiveProps(Props next) { Bench.log(props().id() + ":fallback-receive"); }
        protected void UNSAFE_componentWillReceiveProps(Props next) { Bench.log(props().id() + ":fallback-unsafe-receive"); }
        protected void componentWillUpdate(Props next, Model state) { Bench.log(props().id() + ":fallback-update"); }
        protected void UNSAFE_componentWillUpdate(Props next, Model state) { Bench.log(props().id() + ":fallback-unsafe-update"); }
        protected boolean shouldComponentUpdate(Props next, Model state) { return next.allow(); }
        protected void componentDidMount() { Bench.log(props().id() + ":mounted"); }
        protected void componentDidUpdate(Props previous, Model state, Void snapshot) { Bench.log(props().id() + ":committed"); }
        protected Void getSnapshotBeforeUpdate(Props previous, Model state) { Bench.log(props().id() + ":snapshot"); return null; }
        public View render() { return output(this, theme); }
    }
    public static final class Pure extends PureComponent<Props,Model,Void> {
        final Supplier<String> theme;
        Pure(Props props, Supplier<String> theme) { super(props, new Model(0)); this.theme = theme; }
        protected void componentWillMount() { mounting(this, theme, "old", 1); }
        protected void UNSAFE_componentWillMount() { mounting(this, theme, "unsafe", 10); }
        protected void componentDidMount() { Bench.log(props().id() + ":mounted"); }
        protected void componentDidUpdate(Props previous, Model state, Void snapshot) { Bench.log(props().id() + ":committed"); }
        public View render() { return output(this, theme); }
    }
    private static final ClassComponentType<Props,Model,Ordinary> NORMAL = ordinary("LegacyNormal", true);
    private static ClassComponentType<Props,Model,Ordinary> ordinary(String name, boolean should) {
        return ordinary(name, should, false);
    }
    private static ClassComponentType<Props,Model,Ordinary> ordinary(String name, boolean should, boolean snapshot) {
        var lifecycles = new java.util.HashSet<>(LEGACY);
        if (should) lifecycles.add(SHOULD_UPDATE);
        if (snapshot) lifecycles.add(SNAPSHOT);
        return ClassComponentType.normalWithContext(name, PROPS, MODEL, THEME, Ordinary::new, lifecycles)
                .withContextWillReceiveProps(THEME, DEPRECATED, (peer, next, context) -> receiving(peer, peer.theme, next, context, "old", 100))
                .withContextWillReceiveProps(THEME, UNSAFE, (peer, next, context) -> receiving(peer, peer.theme, next, context, "unsafe", 1000))
                .withContextWillUpdate(THEME, DEPRECATED, (peer, next, state, context) -> updating(peer, peer.theme, next, state, context, "old"))
                .withContextWillUpdate(THEME, UNSAFE, (peer, next, state, context) -> updating(peer, peer.theme, next, state, context, "unsafe"));
    }
    private static final ClassComponentType<Props,Model,Pure> PURE = ClassComponentType.pureWithContext(
            "LegacyPure", PROPS, MODEL, THEME, Pure::new, LEGACY)
            .withContextWillReceiveProps(THEME, DEPRECATED, (peer, next, context) -> receiving(peer, peer.theme, next, context, "old", 100))
            .withContextWillReceiveProps(THEME, UNSAFE, (peer, next, context) -> receiving(peer, peer.theme, next, context, "unsafe", 1000))
            .withContextWillUpdate(THEME, DEPRECATED, (peer, next, state, context) -> updating(peer, peer.theme, next, state, context, "old"))
            .withContextWillUpdate(THEME, UNSAFE, (peer, next, state, context) -> updating(peer, peer.theme, next, state, context, "unsafe"));

    private static void mounting(ClassComponent<Props,Model,?> peer, Supplier<String> theme, String variant, int amount) {
        Bench.log(peer.props().id() + ":mount:" + variant + ":current=" + value(peer, theme));
        peer.setState((pending, props) -> MODEL.patch().set(COUNT, pending.count() + amount),
                () -> Bench.log(peer.props().id() + ":mount-callback:" + variant + ":" + peer.state().count()));
    }
    private static void receiving(ClassComponent<Props,Model,?> peer, Supplier<String> theme,
            Props next, String context, String variant, int amount) {
        Bench.log(peer.props().id() + ":receive:" + variant + ":current=" + value(peer, theme)
                + ":next=" + next.phase() + "/" + context);
        if (next.phase() != peer.props().phase()) peer.setState((pending, props) -> MODEL.patch().set(COUNT, pending.count() + amount),
                () -> Bench.log(peer.props().id() + ":receive-callback:" + variant + ":" + peer.state().count()));
    }
    private static void updating(ClassComponent<Props,Model,?> peer, Supplier<String> theme,
            Props next, Model state, String context, String variant) {
        Bench.log(peer.props().id() + ":update:" + variant + ":current=" + value(peer, theme)
                + ":next=" + next.phase() + "/" + state.count() + "/" + context);
    }
    private static String value(ClassComponent<Props,Model,?> peer, Supplier<String> theme) {
        return peer.props().phase() + "/" + peer.state().count() + "/" + theme.get();
    }
    private static View output(ClassComponent<Props,Model,?> peer, Supplier<String> theme) {
        Bench.log(peer.props().id() + ":render:" + value(peer, theme));
        return Views.element("output", Attributes.create().attr("data-testid", peer.props().id()), value(peer, theme));
    }
    private static View button(String label, Runnable action) {
        return Views.element("button", Attributes.create().on("onClick", event -> action.run()), label);
    }
    public static final ComponentType<Void> JX = new ComponentType<>("ClassLegacy", ignored -> {
        State<Integer> phase = Hooks.useState(0), pulse = Hooks.useState(0);
        State<Boolean> allow = Hooks.useState(true);
        State<String> theme = Hooks.useState("day"), inspection = Hooks.useState("uninspected");
        return Views.element("main", Attributes.create(),
                button("Equal legacy props", () -> pulse.update(value -> value + 1)),
                button("Advance legacy props", () -> phase.update(value -> value + 1)),
                button("Block legacy normal", () -> allow.set(false)),
                button("Force legacy normal", () -> NORMAL_REF.current().forceUpdate()),
                button("Change legacy context", () -> theme.set("night")),
                button("Inspect legacy normal", () -> inspection.set(value(NORMAL_REF.current(), NORMAL_REF.current().theme))),
                Views.element("output", Attributes.create().attr("data-testid", "legacy-pulse"), pulse.value()),
                Views.element("output", Attributes.create().attr("data-testid", "legacy-inspection"), inspection.value()),
                THEME.provide(theme.value(), Views.fragment(
                        Views.component(NORMAL, new Props("normal", phase.value(), allow.value()), "normal", NORMAL_REF),
                        Views.component(PURE, new Props("pure", phase.value(), true), "pure", null))));
    });
    private static final ClassComponentType<Props,Model,Ordinary> DERIVED = ordinary("LegacyDerived", false)
            .withDerivedStateFromProps((props, state) -> null);
    private static final ClassComponentType<Props,Model,Ordinary> SNAPSHOT_TYPE = ordinary("LegacySnapshot", false, true);
    private static final ClassComponentType<Props,Model,Ordinary> ERROR_ONLY = ordinary("LegacyErrorOnly", false)
            .withDerivedStateFromError(error -> null);
    public static final ComponentType<Void> SUPPRESSION = new ComponentType<>("ClassLegacySuppression", ignored -> {
        State<Integer> phase = Hooks.useState(0);
        return Views.element("main", Attributes.create(),
                button("Advance suppressed props", () -> phase.update(value -> value + 1)),
                THEME.provide("day", Views.fragment(
                        Views.component(DERIVED, new Props("derived", phase.value(), true), "derived", null),
                        Views.component(SNAPSHOT_TYPE, new Props("snapshot", phase.value(), true), "snapshot", null),
                        Views.component(ERROR_ONLY, new Props("error", phase.value(), true), "error", null))));
    });

    /** Two bounded, manually settled requests owned only by this conformance fixture. */
    private static final class Attempt {
        private Promise.Resolver<String> resolver;
        final Promise<String> result = Promise.create(created -> resolver = created);
        void release() { resolver.resolve("ready"); }
    }
    private record PendingProps(String id, Attempt attempt) {}
    private record GateProps(String id, int count, Promise<String> result) {}
    private static final ClassSchema.Field<PendingProps,String> PENDING_ID = ClassSchema.field("id", ClassSchema.STRING, PendingProps::id);
    private static final ClassSchema.Field<PendingProps,Attempt> ATTEMPT = ClassSchema.field("attempt", ClassSchema.reference(value -> {
        if (value instanceof Attempt attempt) return attempt;
        throw new IllegalArgumentException("Expected fixture attempt");
    }), PendingProps::attempt);
    private static final ClassSchema<PendingProps> PENDING_PROPS = new ClassSchema<>(List.of(PENDING_ID, ATTEMPT),
            reader -> new PendingProps(reader.get(PENDING_ID), reader.get(ATTEMPT)));
    private static final ComponentType<GateProps> GATE = new ComponentType<>("LegacyGate", props ->
            Views.element("output", Attributes.create().attr("data-testid", "attempt-" + props.id()),
                    props.id() + ":" + props.count() + ":" + Hooks.use(props.result())));
    public static final class Interrupted extends ClassComponent<PendingProps,Model,Void> {
        Interrupted(PendingProps props) { super(props, new Model(0)); }
        private void queue(String variant, int amount) {
            Bench.log(props().id() + ":attempt-mount:" + variant + ":" + state().count());
            setState((pending, props) -> MODEL.patch().set(COUNT, pending.count() + amount),
                    () -> Bench.log(props().id() + ":attempt-callback:" + variant + ":" + state().count()));
        }
        protected void componentWillMount() { queue("old", 1); }
        protected void UNSAFE_componentWillMount() { queue("unsafe", 10); }
        protected void componentDidMount() { Bench.log(props().id() + ":attempt-mounted"); }
        protected void componentWillUnmount() { Bench.log(props().id() + ":attempt-unmounted"); }
        public View render() {
            Bench.log(props().id() + ":attempt-render:" + state().count());
            return Views.component(GATE, new GateProps(props().id(), state().count(), props().attempt().result));
        }
    }
    private static final ClassComponentType<PendingProps,Model,Interrupted> INTERRUPTED_TYPE = ClassComponentType.normal(
            "LegacyInterrupted", PENDING_PROPS, MODEL, Interrupted::new,
            Set.of(WILL_MOUNT, UNSAFE_WILL_MOUNT, MOUNT, UNMOUNT));
    private static final class Attempts {
        final Attempt first = new Attempt(), second = new Attempt();
        final ClassRef<Interrupted> ref = new ClassRef<>(value -> {
            if (value instanceof Interrupted peer) return peer;
            throw new IllegalArgumentException("Expected interrupted peer");
        });
    }
    public static final ComponentType<Void> INTERRUPTED = new ComponentType<>("ClassLegacyInterrupted", ignored -> {
        Attempts owner = Hooks.useMemo(Attempts::new, Dependencies.none());
        State<String> selected = Hooks.useState("none"), inspection = Hooks.useState("uninspected");
        View pending = selected.value().equals("none") ? null : Views.component(INTERRUPTED_TYPE,
                new PendingProps(selected.value(), selected.value().equals("A") ? owner.first : owner.second), selected.value(), owner.ref);
        return Views.element("main", Attributes.create(),
                button("Start attempt A", () -> selected.set("A")),
                button("Start attempt B", () -> selected.set("B")),
                button("Abandon attempt", () -> selected.set("none")),
                button("Release attempt A", owner.first::release),
                button("Release attempt B", owner.second::release),
                button("Inspect attempt", () -> inspection.set(owner.ref.current() == null ? "detached" : owner.ref.current().props().id())),
                Views.element("output", Attributes.create().attr("data-testid", "attempt-inspection"), inspection.value()),
                React.suspense(Views.element("p", Attributes.create().attr("data-testid", "attempt-waiting"), "waiting"), pending));
    });
}
