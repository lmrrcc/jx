package jx.fixture;

import jx.*;
import jx.react.React;
import jx.react.ReactTesting;
import java.util.List;
import java.util.Set;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Independent-module consumer of the deliberately conservative native class journal. */
public final class HotClassApplication {
    private HotClassApplication() {}
    public record Props(boolean skip, int minimum) {}
    public record Model(int count) {}
    private static final ClassSchema.Field<Props,Boolean> SKIP = ClassSchema.field("skip", ClassSchema.BOOLEAN, Props::skip);
    private static final ClassSchema.Field<Props,Integer> MINIMUM = ClassSchema.field("minimum", ClassSchema.INTEGER, Props::minimum);
    private static final ClassSchema<Props> PROPS = new ClassSchema<>(List.of(SKIP, MINIMUM), value -> new Props(value.get(SKIP), value.get(MINIMUM)));
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(COUNT), value -> new Model(value.get(COUNT)));
    private static final StateCodec<Model> CODEC = new StateCodec<>() {
        public String schema() { return "fixture:class-count:" + schemaVersion(); }
        public String encode(Model model) {
            if (failEncoding()) throw new IllegalStateException("Fixture codec failed");
            return Integer.toString(model.count());
        }
        public Model decode(String value) { return new Model(Integer.parseInt(value)); }
    };
    private static Promise<Void> gate;
    private static Promise.Resolver<Void> release;
    private static Counter retained;
    private static final ClassRef<Counter> REF = new ClassRef<>(value -> {
        if (value instanceof Counter counter) return counter;
        throw new IllegalArgumentException("Expected current-generation Counter");
    });
    private static final ComponentType<Integer> DESCENDANT = new ComponentType<>("hot.ClassDescendant", "descendant:state,use:1", count -> {
        State<Integer> pulse = Hooks.useState(0);
        if (count == 99) Hooks.use(gate);
        return button("class-descendant", "Descendant " + pulse.value(), () -> pulse.update(previous -> previous + 1));
    });
    public static final class Counter extends ClassComponent<Props,Model,Void> {
        public Counter(Props props) { super(props, new Model(0)); }
        void increment() { setState((previous, props) -> MODEL.patch().set(COUNT, previous.count() + 1), () -> event("callback", state().count())); }
        void suspend() { React.startTransition(() -> setState(MODEL.patch().set(COUNT, 99), () -> event("callback", state().count()))); }
        @Override protected boolean shouldComponentUpdate(Props props, Model model) {
            event("should", model.count()); return !props.skip();
        }
        @Override protected void componentDidMount() { event("mount", state().count()); }
        @Override protected void componentDidUpdate(Props props, Model model, Void ignored) { event("commit", state().count()); }
        @Override public View render() {
            event("render", state().count());
            return Views.element("section", Attributes.create(),
                    Views.element("output", Attributes.create().attr("id", "class-value"), state().count()),
                    button("class-add", "Increment class", this::increment),
                    button("class-suspend", "Suspend class", this::suspend),
                    Views.component(DESCENDANT, state().count()));
        }
    }
    private static final ClassComponentType<Props,Model,Counter> COUNTER = ClassComponentType.normal(
            "hot.JournalCounter", PROPS, MODEL, Counter::new,
            Set.of(ClassComponentType.Lifecycle.MOUNT, ClassComponentType.Lifecycle.UPDATE, ClassComponentType.Lifecycle.SHOULD_UPDATE))
            .withDerivedStateFromProps((props, state) -> props.minimum() > state.count() ? MODEL.patch().set(COUNT, props.minimum()) : null)
            .withStateCheckpoint("class-count:1", CODEC);
    private static final ComponentType<Void> APP = new ComponentType<>("hot.ClassJournal", "journal:state,state,state,effect:1", ignored -> {
        State<Boolean> visible = Hooks.useState(true);
        State<Boolean> mounted = Hooks.useState(true);
        State<Integer> minimum = Hooks.useState(0);
        Hooks.useEffect(() -> {
            gate = Promise.create(resolver -> release = resolver);
            return () -> { if (release != null) release.resolve(null); release = null; };
        }, Dependencies.none());
        return Views.element("main", Attributes.create(),
                Views.element("h1", Attributes.create().attr("id", "class-generation"), label()),
                button("class-hide", "Hide class", () -> { retained = REF.current(); visible.set(false); }),
                button("class-show", "Show class", () -> visible.set(true)),
                button("class-remove", "Remove class", () -> mounted.set(false)),
                button("class-remount", "Remount class", () -> mounted.set(true)),
                button("class-derived", "Derive class state", () -> minimum.set(2)),
                Views.element("output", Attributes.create().attr("id", "class-mode"), visible.value() ? "visible" : "hidden"),
                React.suspense(Views.element("p", Attributes.create(), "Waiting"), React.activity(visible.value(), mounted.value() && !omit()
                        ? Views.component(COUNTER, new Props(skip(), minimum.value()), "counter", REF) : null)));
    });
    public static void main() {
        ClassCheckpointNativeProbe.install();
        export(action -> React.thenable(ReactTesting.act((Runnable) () -> {
            if (!action.equals("increment-retained") || retained == null) throw new IllegalArgumentException("No retained class action");
            retained.increment();
        })));
        React.mountStrict("root", APP, null);
    }
    private static View button(String id, String text, Runnable action) {
        return Views.element("button", Attributes.create().attr("id", id).on("onClick", event -> action.run()), text);
    }
    @JSFunctor interface TestAction extends JSObject { JSObject run(String action); }
    @JSBody(params = "action", script = """
        globalThis.hotClassAct = async function(name) {
            var previous = globalThis.IS_REACT_ACT_ENVIRONMENT;
            globalThis.IS_REACT_ACT_ENVIRONMENT = true;
            try { return await action(name); }
            finally { globalThis.IS_REACT_ACT_ENVIRONMENT = previous; }
        };
        """) private static native void export(TestAction action);
    @JSBody(script = "return new URLSearchParams(location.search).get('skip') === 'true';") private static native boolean skip();
    @JSBody(script = "return globalThis.hotClassSchema || '1';") private static native String schemaVersion();
    @JSBody(script = "return globalThis.hotClassLabel || 'First generation';") private static native String label();
    @JSBody(script = "return !!globalThis.hotClassFailEncoding;") private static native boolean failEncoding();
    @JSBody(script = "return !!globalThis.hotClassOmit;") private static native boolean omit();
    @JSBody(params = {"kind", "value"}, script = "if (!globalThis.hotClassEvents) globalThis.hotClassEvents = []; globalThis.hotClassEvents.push({kind: kind, value: value});")
    private static native void event(String kind, int value);
}
