package jx.conformance.cases;

import java.util.List;
import java.util.Set;
import jx.*;
import jx.conformance.Bench;

/** Compiling Java consumer of explicit class descriptors, before class markup generation.
 * Render logs are test instrumentation only; application work belongs to commit/event callbacks. */
public final class ClassRuntime {
    private ClassRuntime() {}
    public record Settings(String theme) {}
    public record Props(String label, Settings settings, double number, boolean allowRender) {}
    public record Model(int count, String note) {}
    private static final ClassSchema.Field<Props,String> LABEL = ClassSchema.field("label", ClassSchema.STRING, Props::label);
    private static final ClassSchema.Field<Props,Settings> SETTINGS = ClassSchema.field("settings", ClassSchema.reference(value -> {
        if (value instanceof Settings settings) return settings;
        throw new IllegalArgumentException("Expected Settings");
    }), Props::settings);
    private static final ClassSchema.Field<Props,Double> NUMBER = ClassSchema.field("number", ClassSchema.DOUBLE, Props::number);
    private static final ClassSchema.Field<Props,Boolean> ALLOW = ClassSchema.field("allowRender", ClassSchema.BOOLEAN, Props::allowRender);
    private static final ClassSchema<Props> PROPS = new ClassSchema<>(List.of(LABEL, SETTINGS, NUMBER, ALLOW),
            reader -> new Props(reader.get(LABEL), reader.get(SETTINGS), reader.get(NUMBER), reader.get(ALLOW)));
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema.Field<Model,String> NOTE = ClassSchema.field("note", ClassSchema.STRING.nullable(), Model::note);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(COUNT, NOTE), reader -> new Model(reader.get(COUNT), reader.get(NOTE)));
    private static final Set<ClassComponentType.Lifecycle> LIFECYCLES = Set.of(ClassComponentType.Lifecycle.MOUNT,
            ClassComponentType.Lifecycle.UPDATE, ClassComponentType.Lifecycle.UNMOUNT, ClassComponentType.Lifecycle.SNAPSHOT);
    private static final ClassComponentType<Props,Model,Counter> COUNTER = ClassComponentType.normal("JavaClassCounter", PROPS, MODEL,
            Counter::new, Set.of(ClassComponentType.Lifecycle.MOUNT, ClassComponentType.Lifecycle.UPDATE,
                    ClassComponentType.Lifecycle.UNMOUNT, ClassComponentType.Lifecycle.SNAPSHOT, ClassComponentType.Lifecycle.SHOULD_UPDATE));
    private static final ClassComponentType<Props,Model,PureCounter> PURE = ClassComponentType.pure("JavaPureCounter", PROPS, MODEL,
            PureCounter::new, LIFECYCLES);
    public static final ComponentType<Void> JX = new ComponentType<>("ClassRuntime", ignored -> render());

    public static final class Counter extends ClassComponent<Props,Model,String> {
        private final DomRef output = new DomRef();
        public Counter(Props props) { super(props, new Model(0, "ready")); }
        public void addThree() {
            setState((pending, nextProps) -> MODEL.patch().set(COUNT, pending.count() + 1));
            setState((pending, nextProps) -> MODEL.patch().set(COUNT, pending.count() + 1));
            setState((pending, nextProps) -> MODEL.patch().set(COUNT, pending.count() + 1), this::callback);
            Bench.log("normal:handler:" + state().count());
        }
        public void clearNote() { setState(MODEL.patch().set(NOTE, null), this::callback); }
        public void emptyPatch() { setState(MODEL.patch(), this::callback); }
        public void nullUpdate() { setState((pending, nextProps) -> null, this::callback); }
        public void hideOutput() { setState(MODEL.patch().set(NOTE, "hidden")); }
        public void showOutput() { setState(MODEL.patch().set(NOTE, "ready")); }
        private String domText() { return output.current() == null ? "<none>" : output.current().textContent(); }
        private void callback() { Bench.log("normal:callback:" + state().count() + ":" + domText()); }
        @Override public View render() {
            Bench.log("normal:render");
            return "hidden".equals(state().note()) ? null : output("normal-value", output, state());
        }
        @Override protected boolean shouldComponentUpdate(Props nextProps, Model nextState) { return nextProps.allowRender(); }
        @Override protected void componentDidMount() { Bench.log("normal:mount"); }
        @Override protected void componentWillUnmount() { Bench.log("normal:unmount"); }
        @Override protected String getSnapshotBeforeUpdate(Props previousProps, Model previousState) { return domText(); }
        @Override protected void componentDidUpdate(Props previousProps, Model previousState, String snapshot) {
            Bench.log("normal:commit:" + snapshot + "->" + domText());
        }
    }
    public static final class PureCounter extends PureComponent<Props,Model,String> {
        private final DomRef output = new DomRef();
        public PureCounter(Props props) { super(props, new Model(0, "ready")); }
        public void emptyPatch() { setState(MODEL.patch(), this::callback); }
        public void sameState() { setState(MODEL.patch().set(COUNT, state().count()), this::callback); }
        public void addOne() { setState((pending, nextProps) -> MODEL.patch().set(COUNT, pending.count() + 1), this::callback); }
        private void callback() { Bench.log("pure:callback:" + state().count() + ":" + output.current().textContent()); }
        @Override public View render() { Bench.log("pure:render"); return output("pure-value", output, state()); }
        @Override protected void componentDidMount() { Bench.log("pure:mount"); }
        @Override protected void componentWillUnmount() { Bench.log("pure:unmount"); }
        @Override protected String getSnapshotBeforeUpdate(Props previousProps, Model previousState) { return output.current().textContent(); }
        @Override protected void componentDidUpdate(Props previousProps, Model previousState, String snapshot) {
            Bench.log("pure:commit:" + snapshot + "->" + output.current().textContent());
        }
    }
    private static View output(String id, DomRef output, Model model) {
        return Views.element("output", Attributes.create().attr("data-testid", id).ref(output), text(model));
    }
    private static String text(Model model) { return model.count() + ":" + (model.note() == null ? "<null>" : model.note()); }
    private static View button(String name, boolean disabled, Runnable action) {
        return Views.element("button", Attributes.create().attr("disabled", disabled).on("onClick", event -> action.run()), name);
    }
    private static View render() {
        ClassRef<Counter> normal = Hooks.useMemo(() -> new ClassRef<>(value -> {
            if (value instanceof Counter counter) return counter;
            throw new IllegalArgumentException("Expected Counter instance");
        }), Dependencies.none());
        ClassRef<PureCounter> pure = Hooks.useMemo(() -> new ClassRef<>(value -> {
            if (value instanceof PureCounter counter) return counter;
            throw new IllegalArgumentException("Expected PureCounter instance");
        }), Dependencies.none());
        State<Integer> tick = Hooks.useState(0);
        State<Settings> settings = Hooks.useState(new Settings("dark"));
        State<Double> number = Hooks.useState(0.0);
        State<Boolean> allow = Hooks.useState(true);
        State<Boolean> mounted = Hooks.useState(true);
        State<Integer> key = Hooks.useState(0);
        State<String> inspection = Hooks.useState("uninspected");
        State<String> snapshotIdentity = Hooks.useState("uninspected");
        Props props = new Props("Counter", settings.value(), number.value(), allow.value());
        return Views.element("main", Attributes.create(),
                button("Add three", !mounted.value(), () -> normal.current().addThree()),
                button("Clear note", !mounted.value(), () -> normal.current().clearNote()),
                button("Empty patch", !mounted.value(), () -> normal.current().emptyPatch()),
                button("Null update", !mounted.value(), () -> normal.current().nullUpdate()),
                button("Hide normal output", !mounted.value(), () -> normal.current().hideOutput()),
                button("Show normal output", !mounted.value(), () -> normal.current().showOutput()),
                button("Pure empty patch", false, () -> pure.current().emptyPatch()),
                button("Pure same state", false, () -> pure.current().sameState()),
                button("Pure add one", false, () -> pure.current().addOne()),
                button("Equal props", false, () -> tick.update(previous -> previous + 1)),
                button("New nested value", false, () -> settings.set(new Settings("dark"))),
                button("NaN prop", false, () -> number.set(Double.NaN)),
                button("Negative zero prop", false, () -> number.set(-0.0)),
                button("Positive zero prop", false, () -> number.set(0.0)),
                button("Block normal", false, () -> allow.set(false)),
                button("Allow normal", false, () -> allow.set(true)),
                button("Inspect instance", false, () -> {
                    Counter peer = normal.current();
                    inspection.set(peer == null ? "detached" : "count=" + peer.state().count()
                            + ";note=" + (peer.state().note() == null ? "<null>" : peer.state().note())
                            + ";allow=" + peer.props().allowRender());
                }),
                button("Inspect snapshot identity", !mounted.value(), () -> {
                    Counter peer = normal.current();
                    snapshotIdentity.set("props=" + (peer.props() == peer.props() ? "same" : "different")
                            + ";state=" + (peer.state() == peer.state() ? "same" : "different"));
                }),
                button("Force normal", !mounted.value(), () -> normal.current().forceUpdate()),
                button("Remount normal", !mounted.value(), () -> key.update(previous -> previous + 1)),
                button("Unmount normal", !mounted.value(), () -> mounted.set(false)),
                Views.element("output", Attributes.create().attr("data-testid", "instance-read"), inspection.value()),
                Views.element("output", Attributes.create().attr("data-testid", "snapshot-identity"), snapshotIdentity.value()),
                mounted.value() ? Views.component(COUNTER, props, "normal-" + key.value(), normal) : null,
                Views.component(PURE, props, "pure", pure));
    }
}
