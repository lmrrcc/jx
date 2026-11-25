package jx.conformance.cases;

import java.util.List;
import java.util.Set;
import jx.*;
import jx.conformance.Bench;

/** Explicit native class descriptors with pure static prop-to-state derivation. */
public final class ClassDerivedProps {
    private ClassDerivedProps() {}
    public record Props(String id, int revision, int base, boolean clear, boolean blocked, boolean empty) {}
    public record Model(int revision, int count, String note, int edits) {}
    private static final ClassSchema.Field<Props,String> ID = ClassSchema.field("id", ClassSchema.STRING, Props::id);
    private static final ClassSchema.Field<Props,Integer> REVISION = ClassSchema.field("revision", ClassSchema.INTEGER, Props::revision);
    private static final ClassSchema.Field<Props,Integer> BASE = ClassSchema.field("base", ClassSchema.INTEGER, Props::base);
    private static final ClassSchema.Field<Props,Boolean> CLEAR = ClassSchema.field("clear", ClassSchema.BOOLEAN, Props::clear);
    private static final ClassSchema.Field<Props,Boolean> BLOCKED = ClassSchema.field("blocked", ClassSchema.BOOLEAN, Props::blocked);
    private static final ClassSchema.Field<Props,Boolean> EMPTY = ClassSchema.field("empty", ClassSchema.BOOLEAN, Props::empty);
    private static final ClassSchema<Props> PROPS = new ClassSchema<>(List.of(ID, REVISION, BASE, CLEAR, BLOCKED, EMPTY),
            r -> new Props(r.get(ID), r.get(REVISION), r.get(BASE), r.get(CLEAR), r.get(BLOCKED), r.get(EMPTY)));
    private static final ClassSchema.Field<Model,Integer> SEEN = ClassSchema.field("revision", ClassSchema.INTEGER, Model::revision);
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema.Field<Model,String> NOTE = ClassSchema.field("note", ClassSchema.STRING.nullable(), Model::note);
    private static final ClassSchema.Field<Model,Integer> EDITS = ClassSchema.field("edits", ClassSchema.INTEGER, Model::edits);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(SEEN, COUNT, NOTE, EDITS),
            r -> new Model(r.get(SEEN), r.get(COUNT), r.get(NOTE), r.get(EDITS)));
    private static final ClassComponentType<Props,Model,Normal> NORMAL = ClassComponentType.normal(
            "DerivedNormal", PROPS, MODEL, Normal::new,
            Set.of(ClassComponentType.Lifecycle.MOUNT, ClassComponentType.Lifecycle.UPDATE,
                    ClassComponentType.Lifecycle.SNAPSHOT, ClassComponentType.Lifecycle.SHOULD_UPDATE))
            .withDerivedStateFromProps(ClassDerivedProps::derive);
    private static final ClassComponentType<Props,Model,Pure> PURE = ClassComponentType.pure(
            "DerivedPure", PROPS, MODEL, Pure::new,
            Set.of(ClassComponentType.Lifecycle.MOUNT, ClassComponentType.Lifecycle.UPDATE))
            .withDerivedStateFromProps(ClassDerivedProps::derive);
    public static final ComponentType<Void> JX = new ComponentType<>("ClassDerivedProps", ignored -> render());

    private static StatePatch<Model> derive(Props props, Model state) {
        if (props.empty()) return MODEL.patch();
        if (props.revision() != state.revision()) {
            StatePatch<Model> patch = MODEL.patch().set(SEEN, props.revision()).set(COUNT, props.base());
            return props.clear() ? patch.set(NOTE, null) : patch;
        }
        return state.count() > props.base() + 5 ? MODEL.patch().set(COUNT, props.base() + 5) : null;
    }
    private static Model initial() { return new Model(-1, 1, "initial", 0); }
    private static String summary(Model state) { return state.count() + ":" + state.edits() + ":" + (state.note() == null ? "<null>" : state.note()); }
    private static String value(Props props, Model state, int born) { return props.id() + ":" + summary(state) + ":born=" + born; }
    private static StatePatch<Model> increment(Model pending, int amount) {
        return MODEL.patch().set(COUNT, pending.count() + amount).set(EDITS, pending.edits() + 1);
    }
    private static void committed(Props props, Model state) { Bench.log(props.id() + ":callback:" + summary(state)); }

    public static final class Normal extends ClassComponent<Props,Model,String> {
        private final DomRef output = new DomRef();
        private final int born;
        Normal(Props props) { super(props, initial()); born = state().count(); }
        void addTwo() {
            setState((pending, props) -> increment(pending, 1));
            setState((pending, props) -> increment(pending, 1), () -> committed(props(), state()));
        }
        void addHundred() { setState((pending, props) -> increment(pending, 100), () -> committed(props(), state())); }
        @Override protected boolean shouldComponentUpdate(Props next, Model state) { return !next.blocked(); }
        @Override protected void componentDidMount() { Bench.log(props().id() + ":mount:" + summary(state())); }
        @Override protected String getSnapshotBeforeUpdate(Props props, Model state) { return output.current().textContent(); }
        @Override protected void componentDidUpdate(Props previousProps, Model previousState, String snapshot) {
            Bench.log(props().id() + ":commit:" + snapshot + "->" + value(props(), state(), born));
        }
        @Override public View render() {
            Bench.log(props().id() + ":render");
            return Views.element("output", Attributes.create().attr("data-testid", "derived-" + props().id()).ref(output), value(props(), state(), born));
        }
    }
    public static final class Pure extends PureComponent<Props,Model,Void> {
        private final int born;
        Pure(Props props) { super(props, initial()); born = state().count(); }
        void addTwo() {
            setState((pending, props) -> increment(pending, 1));
            setState((pending, props) -> increment(pending, 1), () -> committed(props(), state()));
        }
        void addHundred() { setState((pending, props) -> increment(pending, 100), () -> committed(props(), state())); }
        @Override protected void componentDidMount() { Bench.log(props().id() + ":mount:" + summary(state())); }
        @Override protected void componentDidUpdate(Props props, Model state, Void snapshot) { Bench.log("pure:commit:" + summary(state())); }
        @Override public View render() {
            Bench.log("pure:render");
            return Views.element("output", Attributes.create().attr("data-testid", "derived-pure"), value(props(), state(), born));
        }
    }
    private static ClassRef<Normal> normalRef() { return new ClassRef<>(value -> {
        if (value instanceof Normal peer) return peer;
        throw new IllegalArgumentException("Expected derived normal peer");
    }); }
    private static ClassRef<Pure> pureRef() { return new ClassRef<>(value -> {
        if (value instanceof Pure peer) return peer;
        throw new IllegalArgumentException("Expected derived pure peer");
    }); }
    private static View button(String title, Runnable action) {
        return Views.element("button", Attributes.create().on("onClick", event -> action.run()), title);
    }
    private static View render() {
        ClassRef<Normal> normal = Hooks.useMemo(ClassDerivedProps::normalRef, Dependencies.none());
        ClassRef<Normal> blocked = Hooks.useMemo(ClassDerivedProps::normalRef, Dependencies.none());
        ClassRef<Pure> pure = Hooks.useMemo(ClassDerivedProps::pureRef, Dependencies.none());
        State<Integer> revision = Hooks.useState(0), pulse = Hooks.useState(0);
        State<Boolean> empty = Hooks.useState(false);
        State<String> inspection = Hooks.useState("uninspected");
        int base = (revision.value() + 1) * 10;
        return Views.element("main", Attributes.create(),
                button("Add two to classes", () -> { normal.current().addTwo(); pure.current().addTwo(); blocked.current().addTwo(); }),
                button("Add hundred to classes", () -> { normal.current().addHundred(); pure.current().addHundred(); blocked.current().addHundred(); }),
                button("Advance derived props", () -> revision.update(value -> value + 1)),
                button("Rerender equal props", () -> pulse.update(value -> value + 1)),
                button("Use empty derived patch", () -> empty.set(true)),
                button("Force blocked class", () -> blocked.current().forceUpdate()),
                button("Inspect derived state", () -> inspection.set("normal=" + summary(normal.current().state())
                        + ";pure=" + summary(pure.current().state()) + ";blocked=" + summary(blocked.current().state()))),
                Views.element("output", Attributes.create().attr("data-testid", "derived-pulse"), pulse.value()),
                Views.element("output", Attributes.create().attr("data-testid", "derived-inspection"), inspection.value()),
                Views.component(NORMAL, new Props("normal", revision.value(), base, revision.value() >= 2, false, empty.value()), "normal", normal),
                Views.component(PURE, new Props("pure", revision.value(), base, revision.value() >= 2, false, empty.value()), "pure", pure),
                Views.component(NORMAL, new Props("blocked", revision.value(), base, revision.value() >= 2, true, empty.value()), "blocked", blocked));
    }
}
