package jx.conformance.cases;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import jx.*;
import jx.conformance.Bench;
import jx.react.React;

/** Native contextType consumers, including a retained class inside React Activity. */
public final class ClassContextCase {
    private ClassContextCase() {}
    public record Theme(String name) {}
    public record Props(String id) {}
    public record Model(int count) {}
    private static final Theme DEFAULT = new Theme("default");
    private static final Theme OUTER = new Theme("outer");
    private static final Theme INNER = new Theme("inner");
    private static final Context<Theme> THEME = new Context<>(DEFAULT);
    private static final ClassSchema.Field<Props,String> ID = ClassSchema.field("id", ClassSchema.STRING, Props::id);
    private static final ClassSchema<Props> PROPS = new ClassSchema<>(List.of(ID), reader -> new Props(reader.get(ID)));
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(COUNT), reader -> new Model(reader.get(COUNT)));
    private static final ClassComponentType<Props,Model,Normal> NORMAL = ClassComponentType.normalWithContext(
            "ClassContextNormal", PROPS, MODEL, THEME, Normal::new,
            Set.of(ClassComponentType.Lifecycle.MOUNT, ClassComponentType.Lifecycle.UPDATE,
                    ClassComponentType.Lifecycle.UNMOUNT, ClassComponentType.Lifecycle.SHOULD_UPDATE));
    private static final ClassComponentType<Props,Model,Pure> PURE = ClassComponentType.pureWithContext(
            "ClassContextPure", PROPS, MODEL, THEME, Pure::new,
            Set.of(ClassComponentType.Lifecycle.MOUNT, ClassComponentType.Lifecycle.UPDATE, ClassComponentType.Lifecycle.UNMOUNT));
    public static final ComponentType<Void> JX = new ComponentType<>("ClassContextCase", ignored -> render());

    public static final class Normal extends ClassComponent<Props,Model,Void> {
        private final Supplier<Theme> theme;
        private final String born;
        Normal(Props props, Supplier<Theme> theme) {
            super(props, new Model(0)); this.theme = theme; this.born = name(theme.get());
        }
        void addOne() { setState((pending, next) -> MODEL.patch().set(COUNT, pending.count() + 1),
                () -> Bench.log(props().id() + ":callback:" + state().count())); }
        boolean same(Theme value) { return theme.get() == value; }
        @Override protected boolean shouldComponentUpdate(Props props, Model state) { return false; }
        @Override protected void componentDidMount() { lifecycle(props(), "mount", theme.get(), state()); }
        @Override protected void componentDidUpdate(Props props, Model state, Void snapshot) { lifecycle(props(), "update", theme.get(), state()); }
        @Override protected void componentWillUnmount() { lifecycle(props(), "unmount", theme.get(), state()); }
        @Override public View render() { return value(props(), state(), theme.get(), born); }
    }

    public static final class Pure extends PureComponent<Props,Model,Void> {
        private final Supplier<Theme> theme;
        private final String born;
        Pure(Props props, Supplier<Theme> theme) {
            super(props, new Model(0)); this.theme = theme; this.born = name(theme.get());
        }
        void addOne() { setState((pending, next) -> MODEL.patch().set(COUNT, pending.count() + 1),
                () -> Bench.log(props().id() + ":callback:" + state().count())); }
        boolean same(Theme value) { return theme.get() == value; }
        String describe() { return name(theme.get()) + ";count=" + state().count(); }
        @Override protected void componentDidMount() { lifecycle(props(), "mount", theme.get(), state()); }
        @Override protected void componentDidUpdate(Props props, Model state, Void snapshot) { lifecycle(props(), "update", theme.get(), state()); }
        @Override protected void componentWillUnmount() { lifecycle(props(), "unmount", theme.get(), state()); }
        @Override public View render() { return value(props(), state(), theme.get(), born); }
    }

    private static String name(Theme theme) { return theme == null ? "<null>" : theme.name(); }
    private static void lifecycle(Props props, String phase, Theme theme, Model state) {
        Bench.log(props.id() + ":" + phase + ":" + name(theme) + ":" + state.count());
    }
    private static View value(Props props, Model state, Theme theme, String born) {
        // Render logs instrument the conformance fixture; application render methods stay pure.
        Bench.log(props.id() + ":render");
        return Views.element("output", Attributes.create().attr("data-testid", "context-" + props.id()),
                props.id() + ":" + name(theme) + ":" + state.count() + ":born=" + born);
    }
    private static ClassRef<Normal> normalRef() {
        return new ClassRef<>(value -> {
            if (value instanceof Normal peer) return peer;
            throw new IllegalArgumentException("Expected normal context peer");
        });
    }
    private static ClassRef<Pure> pureRef() {
        return new ClassRef<>(value -> {
            if (value instanceof Pure peer) return peer;
            throw new IllegalArgumentException("Expected pure context peer");
        });
    }
    private static String matched(boolean same) { return same ? "same" : "different"; }

    private static View render() {
        ClassRef<Normal> normal = Hooks.useMemo(ClassContextCase::normalRef, Dependencies.none());
        ClassRef<Pure> pure = Hooks.useMemo(ClassContextCase::pureRef, Dependencies.none());
        ClassRef<Pure> fallback = Hooks.useMemo(ClassContextCase::pureRef, Dependencies.none());
        ClassRef<Pure> nested = Hooks.useMemo(ClassContextCase::pureRef, Dependencies.none());
        ClassRef<Pure> activity = Hooks.useMemo(ClassContextCase::pureRef, Dependencies.none());
        Ref<Pure> retained = Hooks.useRef(null);
        State<Theme> theme = Hooks.useState(OUTER);
        State<Integer> pulse = Hooks.useState(0);
        State<Boolean> visible = Hooks.useState(true);
        State<Boolean> mounted = Hooks.useState(true);
        State<String> inspection = Hooks.useState("uninspected");
        State<String> retainedRead = Hooks.useState("uninspected");
        return Views.element("main", Attributes.create(),
                button("Increment blocked class", !mounted.value(), () -> normal.current().addOne()),
                button("Increment pure class", !mounted.value(), () -> pure.current().addOne()),
                button("Rerender context host", false, () -> pulse.update(previous -> previous + 1)),
                button("Replace equal context", false, () -> theme.set(new Theme("outer"))),
                button("Set dark context", false, () -> theme.set(new Theme("dark"))),
                button("Set null context", false, () -> theme.set(null)),
                button("Inspect context identity", !mounted.value() || !visible.value(), () -> inspection.set(
                        "normal=" + matched(normal.current().same(theme.value())) + ";pure=" + matched(pure.current().same(theme.value()))
                        + ";default=" + matched(fallback.current().same(DEFAULT)) + ";nested=" + matched(nested.current().same(INNER))
                        + ";activity=" + matched(activity.current().same(theme.value())))),
                button("Hide activity", !mounted.value() || !visible.value(), () -> {
                    retained.set(activity.current());
                    visible.set(false);
                }),
                button("Show activity", !mounted.value() || visible.value(), () -> visible.set(true)),
                button("Increment retained activity", !mounted.value(), () -> retained.current().addOne()),
                button("Inspect retained activity", false, () -> retainedRead.set(retained.current().describe())),
                button("Unmount contextual rows", !mounted.value(), () -> mounted.set(false)),
                Views.element("output", Attributes.create().attr("data-testid", "context-pulse"), pulse.value()),
                Views.element("output", Attributes.create().attr("data-testid", "context-inspection"), inspection.value()),
                Views.element("output", Attributes.create().attr("data-testid", "retained-context"), retainedRead.value()),
                Views.component(PURE, new Props("default"), "default", fallback),
                mounted.value() ? THEME.provide(theme.value(),
                        Views.component(NORMAL, new Props("normal"), "normal", normal),
                        Views.component(PURE, new Props("pure"), "pure", pure),
                        THEME.provide(INNER, Views.component(PURE, new Props("nested"), "nested", nested)),
                        React.activity(visible.value(), Views.component(PURE, new Props("activity"), "activity", activity))) : null);
    }
    private static View button(String title, boolean disabled, Runnable action) {
        return Views.element("button", Attributes.create().attr("disabled", disabled).on("onClick", event -> action.run()), title);
    }
}
