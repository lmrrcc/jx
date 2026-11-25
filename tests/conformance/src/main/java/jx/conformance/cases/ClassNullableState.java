package jx.conformance.cases;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jx.*;
import jx.conformance.Bench;

/** Manual typed runtime consumer: sparse state is distinct from a null aggregate. */
public final class ClassNullableState {
    private ClassNullableState() {}
    public record Props(String id, boolean blocked, int revision) {}
    public record Model(Presence<Integer> count, Presence<String> note) {}
    private static final ClassSchema.Field<Props,String> ID = ClassSchema.field("id", ClassSchema.STRING, Props::id);
    private static final ClassSchema.Field<Props,Boolean> BLOCKED = ClassSchema.field("blocked", ClassSchema.BOOLEAN, Props::blocked);
    private static final ClassSchema.Field<Props,Integer> REVISION = ClassSchema.field("revision", ClassSchema.INTEGER, Props::revision);
    private static final ClassSchema<Props> PROPS = new ClassSchema<>(List.of(ID, BLOCKED, REVISION), r -> new Props(r.get(ID), r.get(BLOCKED), r.get(REVISION)));
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.optionalField("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema.Field<Model,String> NOTE = ClassSchema.optionalField("note", ClassSchema.STRING.nullable(), Model::note);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(COUNT, NOTE), r -> new Model(r.find(COUNT), r.find(NOTE)));
    private static final ClassComponentType<Props,Model,Normal> NORMAL = ClassComponentType.normal(
            "NullableNormal", PROPS, MODEL, Normal::new,
            Set.of(ClassComponentType.Lifecycle.UPDATE, ClassComponentType.Lifecycle.SNAPSHOT, ClassComponentType.Lifecycle.SHOULD_UPDATE))
            .withNullableState();
    private static final ClassComponentType<Props,Model,Pure> PURE = ClassComponentType.pure(
            "NullablePure", PROPS, MODEL, Pure::new, Set.of(ClassComponentType.Lifecycle.UPDATE)).withNullableState();
    private static final ClassComponentType<Props,Model,Normal> DERIVED = ClassComponentType.normal(
            "NullableDerivedNormal", PROPS, MODEL, Normal::new,
            Set.of(ClassComponentType.Lifecycle.UPDATE, ClassComponentType.Lifecycle.SNAPSHOT, ClassComponentType.Lifecycle.SHOULD_UPDATE))
            .withDerivedStateFromProps(ClassNullableState::derive).withNullableState();
    private static final ClassComponentType<Props,Model,Pure> DERIVED_PURE = ClassComponentType.pure(
            "NullableDerivedPure", PROPS, MODEL, Pure::new, Set.of(ClassComponentType.Lifecycle.UPDATE))
            .withNullableState().withDerivedStateFromProps(ClassNullableState::derive);
    private static final ClassComponentType<Props,Model,Normal> ERRORS = ClassComponentType.normal(
            "NullableErrorBoundary", PROPS, MODEL, Normal::new, Set.of(ClassComponentType.Lifecycle.CATCH,
                    ClassComponentType.Lifecycle.UPDATE, ClassComponentType.Lifecycle.SNAPSHOT, ClassComponentType.Lifecycle.SHOULD_UPDATE))
            .withNullableState().withDerivedStateFromError(error -> MODEL.patch().set(NOTE, "caught"));
    private static final ComponentType<Void> FAIL = new ComponentType<>("NullableFailure", ignored -> { throw new IllegalStateException("nullable child failed"); });
    public static final ComponentType<Void> JX = new ComponentType<>("ClassNullableState", ignored -> render());
    public static final ComponentType<Void> DERIVED_JX = new ComponentType<>("ClassNullableDerived", ignored -> renderDerived());

    private static StatePatch<Model> derive(Props props, Model pending) {
        Bench.log(props.id() + ":derive:" + props.revision() + ":" + summary(pending));
        return switch (props.revision()) {
            case 1 -> MODEL.patch().set(COUNT, 10);
            case 2 -> MODEL.patch().set(NOTE, null);
            case 3 -> MODEL.patch().undefined(NOTE);
            default -> null;
        };
    }

    private static String value(Presence<?> field) {
        return switch (field) {
            case Presence.Absent<?> ignored -> "absent";
            case Presence.Undefined<?> ignored -> "undefined";
            case Presence.Present<?> present -> present.value() == null ? "null" : present.value().toString();
        };
    }
    private static String summary(Model state) { return state == null ? "null" : "count=" + value(state.count()) + ";note=" + value(state.note()); }
    private static int count(Model state) { return state != null && state.count() instanceof Presence.Present<Integer> present ? present.value() : 0; }
    private static void operate(ClassComponent<Props,Model,?> peer, String operation) {
        Model before = peer.state();
        Runnable committed = () -> Bench.log(peer.props().id() + ":callback:" + operation + ":" + summary(peer.state())
                + ":same=" + (peer.state() == before));
        switch (operation) {
            case "direct" -> peer.setState((StatePatch<Model>) null, committed);
            case "functional" -> peer.setState((pending, props) -> {
                Bench.log(props.id() + ":updater:" + summary(pending)); return null;
            }, committed);
            case "empty" -> peer.setState(MODEL.patch(), committed);
            case "partial" -> peer.setState(MODEL.patch().set(COUNT, 1), committed);
            case "undefined" -> peer.setState(MODEL.patch().undefined(NOTE), committed);
            case "null-field" -> peer.setState(MODEL.patch().set(NOTE, null), committed);
            case "queued" -> {
                peer.setState((pending, props) -> {
                    Bench.log(props.id() + ":updater:first:" + summary(pending));
                    return MODEL.patch().set(COUNT, count(pending) + 1);
                });
                peer.setState((pending, props) -> {
                    Bench.log(props.id() + ":updater:second:" + summary(pending));
                    return MODEL.patch().set(COUNT, count(pending) + 1);
                }, committed);
            }
            default -> throw new IllegalArgumentException("Unknown state operation");
        }
        Bench.log(peer.props().id() + ":handler:" + operation + ":" + summary(peer.state()) + ":same=" + (peer.state() == before));
    }
    private static View output(Props props, Model state) {
        Bench.log(props.id() + ":render");
        return Views.element("output", Attributes.create().attr("data-testid", "nullable-" + props.id()), summary(state));
    }
    public static final class Normal extends ClassComponent<Props,Model,String> {
        Normal(Props props) { super(props, props.id().equals("seed") ? new Model(Presence.absent(), Presence.undefined()) : null); }
        @Override public View render() {
            View output = output(props(), state());
            if (props().id().equals("error") && props().revision() > 0
                    && !(state() != null && state().note() instanceof Presence.Present<String> present && "caught".equals(present.value())))
                return Views.fragment(output, Views.component(FAIL, null));
            return output;
        }
        @Override protected void componentDidCatch(Throwable error, ComponentErrorInfo info) {
            Bench.log(props().id() + ":caught:" + error.getMessage() + ":state=" + summary(state()) + ":stack=" + !info.componentStack().isEmpty());
        }
        @Override protected boolean shouldComponentUpdate(Props next, Model pending) {
            Bench.log(props().id() + ":should:" + summary(state()) + "->" + summary(pending));
            return !next.blocked();
        }
        @Override protected String getSnapshotBeforeUpdate(Props previous, Model previousState) { return summary(previousState); }
        @Override protected void componentDidUpdate(Props previous, Model previousState, String snapshot) {
            Bench.log(props().id() + ":commit:" + summary(previousState) + "->" + summary(state()) + ":snapshot=" + snapshot);
        }
    }
    public static final class Pure extends PureComponent<Props,Model,Void> {
        Pure(Props props) { super(props, null); }
        @Override public View render() { return output(props(), state()); }
        @Override protected void componentDidUpdate(Props previous, Model previousState, Void snapshot) {
            Bench.log(props().id() + ":commit:" + summary(previousState) + "->" + summary(state()));
        }
    }
    private static ClassRef<Normal> normalRef() { return new ClassRef<>(value -> {
        if (value instanceof Normal peer) return peer; throw new IllegalArgumentException("Expected normal peer");
    }); }
    private static ClassRef<Pure> pureRef() { return new ClassRef<>(value -> {
        if (value instanceof Pure peer) return peer; throw new IllegalArgumentException("Expected pure peer");
    }); }
    private static View button(String label, Runnable action) { return Views.element("button", Attributes.create().on("onClick", event -> action.run()), label); }
    private static void controls(List<View> views, String id, ClassRef<? extends ClassComponent<Props,Model,?>> ref) {
        for (String operation : List.of("direct", "functional", "empty", "partial", "undefined", "null-field", "queued"))
            views.add(new View.Keyed(button(id + " " + operation, () -> operate(ref.current(), operation)), id + ":" + operation));
    }
    private static View render() {
        ClassRef<Normal> normal = Hooks.useMemo(ClassNullableState::normalRef, Dependencies.none());
        ClassRef<Normal> blocked = Hooks.useMemo(ClassNullableState::normalRef, Dependencies.none());
        ClassRef<Pure> pure = Hooks.useMemo(ClassNullableState::pureRef, Dependencies.none());
        ClassRef<Normal> seed = Hooks.useMemo(ClassNullableState::normalRef, Dependencies.none());
        State<Integer> key = Hooks.useState(0);
        var controls = new ArrayList<View>();
        controls(controls, "normal", normal); controls(controls, "pure", pure); controls(controls, "blocked", blocked);
        return Views.element("main", Attributes.create(),
                controls, button("Remount nullable classes", () -> key.update(value -> value + 1)),
                ClassStateObserver.observe(Views.component(NORMAL, new Props("normal", false, 0), "normal-" + key.value(), normal)),
                ClassStateObserver.observe(Views.component(PURE, new Props("pure", false, 0), "pure-" + key.value(), pure)),
                ClassStateObserver.observe(Views.component(NORMAL, new Props("blocked", true, 0), "blocked-" + key.value(), blocked)),
                ClassStateObserver.observe(Views.component(NORMAL, new Props("seed", false, 0), "seed-" + key.value(), seed)));
    }
    private static View renderDerived() {
        ClassRef<Normal> normal = Hooks.useMemo(ClassNullableState::normalRef, Dependencies.none());
        ClassRef<Normal> blocked = Hooks.useMemo(ClassNullableState::normalRef, Dependencies.none());
        ClassRef<Pure> pure = Hooks.useMemo(ClassNullableState::pureRef, Dependencies.none());
        ClassRef<Normal> error = Hooks.useMemo(ClassNullableState::normalRef, Dependencies.none());
        State<Integer> revision = Hooks.useState(0);
        State<Boolean> fail = Hooks.useState(false);
        var controls = new ArrayList<View>();
        controls(controls, "normal", normal); controls(controls, "pure", pure); controls(controls, "blocked", blocked);
        return Views.element("main", Attributes.create(), controls,
                button("Advance nullable derivation", () -> revision.update(value -> value + 1)),
                button("Fail nullable child", () -> fail.set(true)),
                Views.element("output", Attributes.create().attr("data-testid", "nullable-revision"), revision.value()),
                ClassStateObserver.observe(Views.component(DERIVED, new Props("normal", false, revision.value()), "normal", normal)),
                ClassStateObserver.observe(Views.component(DERIVED_PURE, new Props("pure", false, revision.value()), "pure", pure)),
                ClassStateObserver.observe(Views.component(DERIVED, new Props("blocked", true, revision.value()), "blocked", blocked)),
                ClassStateObserver.observe(Views.component(ERRORS, new Props("error", false, fail.value() ? 1 : 0), "error", error)));
    }
}
