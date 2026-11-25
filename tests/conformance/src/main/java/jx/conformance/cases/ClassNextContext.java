package jx.conformance.cases;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import jx.*;
import jx.conformance.Bench;

/** Native three-argument should-update with separate current and pending values. */
public final class ClassNextContext {
    private ClassNextContext() {}
    public record Theme(String name) {}
    public record Props(String id, int revision, boolean allow, Theme expected) {}
    public record Model(int count) {}
    private static final Theme DEFAULT = new Theme("default");
    private static final Theme LIGHT = new Theme("light");
    private static final Theme DARK = new Theme("dark");
    private static final Context<Theme> THEME = new Context<>(DEFAULT);
    /** Shared fixture context; the authored class declares the same native provider identity. */
    public static Context<Theme> context() { return THEME; }
    private static final ClassSchema.Field<Props,String> ID = ClassSchema.field("id", ClassSchema.STRING, Props::id);
    private static final ClassSchema.Field<Props,Integer> REVISION = ClassSchema.field("revision", ClassSchema.INTEGER, Props::revision);
    private static final ClassSchema.Field<Props,Boolean> ALLOW = ClassSchema.field("allow", ClassSchema.BOOLEAN, Props::allow);
    private static final ClassSchema.Field<Props,Theme> EXPECTED = ClassSchema.field("expected", ClassSchema.<Theme>reference(value -> {
        if (value instanceof Theme theme) return theme;
        throw new IllegalArgumentException("Expected Theme");
    }).nullable(), Props::expected);
    private static final ClassSchema<Props> PROPS = new ClassSchema<>(List.of(ID, REVISION, ALLOW, EXPECTED),
            reader -> new Props(reader.get(ID), reader.get(REVISION), reader.get(ALLOW), reader.get(EXPECTED)));
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(COUNT), reader -> new Model(reader.get(COUNT)));
    private static final ClassComponentType<Props,Model,Counter> TYPE = ClassComponentType.normalWithContext(
            "NextContextCounter", PROPS, MODEL, THEME, Counter::new,
            Set.of(ClassComponentType.Lifecycle.MOUNT, ClassComponentType.Lifecycle.UPDATE, ClassComponentType.Lifecycle.SHOULD_UPDATE))
            .withContextShouldUpdate(THEME, Counter::decide);
    public static final ComponentType<Void> JX = new ComponentType<>("ClassNextContext", ignored -> render());

    public static final class Counter extends ClassComponent<Props,Model,Void> {
        private final Supplier<Theme> theme;
        private final String born;
        Counter(Props props, Supplier<Theme> theme) {
            super(props, new Model(0)); this.theme = theme; this.born = name(theme.get());
        }
        private boolean decide(Props nextProps, Model nextState, Theme nextContext) {
            // Instrumentation only: application policies are pure and may be repeated/discarded.
            Bench.log("policy:" + props().id() + ":old=" + current() + ":next="
                    + values(nextProps, nextState, nextContext) + ":identity=" + (nextContext == nextProps.expected()));
            return nextProps.allow();
        }
        @Override protected boolean shouldComponentUpdate(Props nextProps, Model nextState) {
            Bench.log("legacy-policy:" + props().id());
            return false;
        }
        private void add() {
            setState((pending, nextProps) -> MODEL.patch().set(COUNT, pending.count() + 1),
                    () -> Bench.log("policy:callback:" + current()));
            Bench.log("policy:handler:" + current());
        }
        private void force() { forceUpdate(() -> Bench.log("policy:forced:" + current())); }
        private String current() { return values(props(), state(), theme.get()); }
        @Override protected void componentDidMount() { Bench.log("policy:mount:" + props().id()); }
        @Override protected void componentDidUpdate(Props oldProps, Model oldState, Void ignored) {
            Bench.log("policy:commit:" + props().id() + ":previous=" + oldProps.revision() + "/" + oldState.count()
                    + ":current=" + current());
        }
        @Override public View render() {
            return Views.element("output", Attributes.create().attr("data-testid", "policy-" + props().id()),
                    current() + ":born=" + born);
        }
    }
    private static String name(Theme value) { return value == null ? "<null>" : value.name(); }
    private static String values(Props props, Model state, Theme context) {
        return props.revision() + "/" + state.count() + "/" + name(context);
    }
    private static ClassRef<Counter> counterRef() {
        return new ClassRef<>(value -> {
            if (value instanceof Counter counter) return counter;
            throw new IllegalArgumentException("Expected Counter");
        });
    }
    private static View render() {
        ClassRef<Counter> counter = Hooks.useMemo(ClassNextContext::counterRef, Dependencies.none());
        ClassRef<AuthoredContextPolicy> authored = Hooks.useMemo(() -> new ClassRef<>(value -> {
            if (value instanceof AuthoredContextPolicy peer) return peer;
            throw new IllegalArgumentException("Expected AuthoredContextPolicy");
        }), Dependencies.none());
        State<Integer> revision = Hooks.useState(0);
        State<Theme> theme = Hooks.useState(LIGHT);
        State<Boolean> allow = Hooks.useState(false);
        State<String> inspection = Hooks.useState("uninspected");
        State<String> authoredInspection = Hooks.useState("uninspected");
        return Views.element("main", Attributes.create(),
                button("Increment context-policy state", () -> { counter.current().add(); authored.current().add(); }),
                button("Change context-policy props", () -> revision.update(value -> value + 1)),
                button("Force context-policy", () -> { counter.current().force(); authored.current().force(); }),
                button("Dark context-policy", () -> theme.set(DARK)),
                button("Null context-policy", () -> theme.set(null)),
                button("Allow context-policy", () -> allow.set(true)),
                button("Combined context-policy update", () -> {
                    revision.update(value -> value + 1); theme.set(DARK); counter.current().add(); authored.current().add();
                }),
                button("Inspect context-policy", () -> { inspection.set(counter.current().current()); authoredInspection.set(authored.current().current()); }),
                Views.element("output", Attributes.create().attr("data-testid", "policy-revision"), revision.value()),
                Views.element("output", Attributes.create().attr("data-testid", "policy-allow"), allow.value() ? "yes" : "no"),
                Views.element("output", Attributes.create().attr("data-testid", "policy-inspection"), inspection.value()),
                Views.element("output", Attributes.create().attr("data-testid", "authored-policy-inspection"), authoredInspection.value()),
                Views.component(TYPE, new Props("default", 0, false, DEFAULT), "default", null),
                THEME.provide(theme.value(), Views.component(TYPE,
                        new Props("provided", revision.value(), allow.value(), theme.value()), "provided", counter),
                        Views.component(AuthoredContextPolicy.JX,
                                new AuthoredContextPolicy.Props("authored", revision.value(), allow.value(), theme.value()), "authored", authored)),
                THEME.provide(null, Views.component(TYPE, new Props("null", 0, false, null), "null", null)));
    }
    private static View button(String title, Runnable action) {
        return Views.element("button", Attributes.create().on("onClick", event -> action.run()), title);
    }
}
