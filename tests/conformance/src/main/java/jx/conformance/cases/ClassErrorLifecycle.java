package jx.conformance.cases;

import java.util.List;
import java.util.Set;
import jx.*;
import jx.conformance.Bench;
import jx.react.React;
import jx.react.ReactError;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/** Native class boundaries; the foreign throw helpers are explicit React interop fixtures. */
public final class ClassErrorLifecycle {
    private ClassErrorLifecycle() {}
    private static final IllegalStateException FAILURE = new IllegalStateException("class child failed");
    private static final JSObject NATIVE_ERROR = nativeError();
    private static final JSObject NATIVE_OBJECT = nativeObject();
    private static final ComponentType<String> FOREIGN = React.binding("NativeFailure",
            nativeComponent(NATIVE_ERROR, NATIVE_OBJECT), ClassErrorLifecycle::foreignProps);
    public record BoundaryProps(String id, View child, boolean ownFailure) {}
    public record BoundaryState(Throwable failure, boolean failed, int count) {}
    public record LeafProps(String phase) {}
    public record LeafState(int count) {}
    private static final ClassSchema.Field<BoundaryProps,String> ID = ClassSchema.field("id", ClassSchema.STRING, BoundaryProps::id);
    private static final ClassSchema.Field<BoundaryProps,View> CHILD = ClassSchema.field("child", ClassSchema.reference(value -> {
        if (value instanceof View view) return view;
        throw new IllegalArgumentException("Expected child view");
    }), BoundaryProps::child);
    private static final ClassSchema.Field<BoundaryProps,Boolean> OWN = ClassSchema.field("ownFailure", ClassSchema.BOOLEAN, BoundaryProps::ownFailure);
    private static final ClassSchema<BoundaryProps> BPROPS = new ClassSchema<>(List.of(ID, CHILD, OWN),
            r -> new BoundaryProps(r.get(ID), r.get(CHILD), r.get(OWN)));
    private static final ClassSchema.Field<BoundaryState,Throwable> ERROR = ClassSchema.field("failure", ClassSchema.<Throwable>reference(value -> {
        if (value instanceof Throwable failure) return failure;
        throw new IllegalArgumentException("Expected failure");
    }).nullable(), BoundaryState::failure);
    private static final ClassSchema.Field<BoundaryState,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, BoundaryState::count);
    private static final ClassSchema.Field<BoundaryState,Boolean> FAILED = ClassSchema.field("failed", ClassSchema.BOOLEAN, BoundaryState::failed);
    private static final ClassSchema<BoundaryState> BSTATE = new ClassSchema<>(List.of(ERROR, FAILED, COUNT),
            r -> new BoundaryState(r.get(ERROR), r.get(FAILED), r.get(COUNT)));
    private static final ClassSchema.Field<LeafProps,String> PHASE = ClassSchema.field("phase", ClassSchema.STRING, LeafProps::phase);
    private static final ClassSchema<LeafProps> LPROPS = new ClassSchema<>(List.of(PHASE), r -> new LeafProps(r.get(PHASE)));
    private static final ClassSchema.Field<LeafState,Integer> LEAF_COUNT = ClassSchema.field("count", ClassSchema.INTEGER, LeafState::count);
    private static final ClassSchema<LeafState> LSTATE = new ClassSchema<>(List.of(LEAF_COUNT), r -> new LeafState(r.get(LEAF_COUNT)));
    private static final ClassComponentType<BoundaryProps,BoundaryState,Boundary> BOUNDARY = ClassComponentType.normal(
            "FailureBoundary", BPROPS, BSTATE, Boundary::new, Set.of(ClassComponentType.Lifecycle.CATCH))
            .withDerivedStateFromError(error -> BSTATE.patch().set(ERROR, error).set(FAILED, true));
    private static final ClassComponentType<LeafProps,LeafState,Leaf> LEAF = ClassComponentType.normal(
            "FailureLeaf", LPROPS, LSTATE, Leaf::new, Set.of(ClassComponentType.Lifecycle.UPDATE))
            .withDerivedStateFromProps((props, state) -> {
                if (props.phase().equals("derive")) throw FAILURE;
                return null;
            });
    public static final ComponentType<Void> JX = new ComponentType<>("ClassErrorLifecycle", ignored -> render());

    public static final class Boundary extends ClassComponent<BoundaryProps,BoundaryState,Void> {
        private final DomRef fallback = new DomRef();
        Boundary(BoundaryProps props) { super(props, new BoundaryState(null, false, 0)); }
        void increment() { setState((state, props) -> BSTATE.patch().set(COUNT, state.count() + 1)); }
        void reset() { setState(BSTATE.patch().set(ERROR, null).set(FAILED, false)); }
        @Override protected void componentDidCatch(Throwable error, ComponentErrorInfo info) {
            boolean same = state().failure() == error;
            if (state().failure() instanceof ReactError stored && error instanceof ReactError reported)
                same = sameNative(stored.nativeValue(), reported.nativeValue());
            boolean committed = fallback.current() != null
                    && fallback.current().textContent().equals(props().id() + ":fallback:" + state().count());
            Bench.log(props().id() + ":caught:" + label(error) + ":same=" + same + ":committed=" + committed
                    + ":stack=" + !info.componentStack().isEmpty());
        }
        @Override public View render() {
            if (props().ownFailure()) throw FAILURE;
            View body = !state().failed() ? props().child()
                    : Views.element("output", Attributes.create().attr("data-testid", props().id() + "-fallback").ref(fallback),
                            props().id() + ":fallback:" + state().count());
            return Views.element("section", Attributes.create().attr("data-testid", props().id() + "-boundary"),
                    Views.element("output", Attributes.create().attr("data-testid", props().id() + "-count"), state().count()), body);
        }
    }
    public static final class Leaf extends ClassComponent<LeafProps,LeafState,Void> {
        Leaf(LeafProps props) { super(props, new LeafState(0)); }
        @Override protected void componentDidUpdate(LeafProps props, LeafState state, Void snapshot) {
            if (props().phase().equals("commit")) throw FAILURE;
        }
        @Override public View render() {
            if (props().phase().equals("render")) throw FAILURE;
            if (props().phase().startsWith("native-")) return Views.component(FOREIGN, props().phase());
            return Views.element("div", Attributes.create().attr("data-testid", "failure-leaf"),
                    Views.element("output", Attributes.create().attr("data-testid", "failure-leaf-count"), state().count()),
                    button("Increment leaf", () -> setState((state, props) -> LSTATE.patch().set(LEAF_COUNT, state.count() + 1))),
                    button("Throw event", () -> { throw FAILURE; }));
        }
    }
    private static String label(Throwable failure) {
        if (failure == FAILURE) return "java";
        if (failure instanceof ReactError foreign) {
            // This is the Java projection contract, not a claimed React wrapper API.
            if (!stackMatches(foreign.nativeValue(), foreign.nativeStack()))
                throw new AssertionError("Foreign stack projection differs from its own data property");
            return nativeLabel(foreign.nativeValue(), NATIVE_ERROR, NATIVE_OBJECT) + ":" + foreign.kind();
        }
        return "unexpected";
    }
    private static ClassRef<Boundary> boundaryRef() { return new ClassRef<>(value -> {
        if (value instanceof Boundary peer) return peer;
        throw new IllegalArgumentException("Expected boundary peer");
    }); }
    private static View button(String title, Runnable action) {
        return Views.element("button", Attributes.create().on("onClick", event -> action.run()), title);
    }
    private static View render() {
        ClassRef<Boundary> outer = Hooks.useMemo(ClassErrorLifecycle::boundaryRef, Dependencies.none());
        ClassRef<Boundary> inner = Hooks.useMemo(ClassErrorLifecycle::boundaryRef, Dependencies.none());
        State<String> phase = Hooks.useState("ok");
        State<Integer> key = Hooks.useState(0);
        View leaf = Views.component(LEAF, new LeafProps(phase.value()), "leaf", null);
        View nested = Views.component(BOUNDARY, new BoundaryProps("inner", leaf, phase.value().equals("own")), "inner", inner);
        return Views.element("main", Attributes.create(),
                Views.element("output", Attributes.create().attr("data-testid", "error-sibling"), "unaffected"),
                button("Increment boundary", () -> inner.current().increment()),
                button("Fail render", () -> phase.set("render")),
                button("Fail derive", () -> phase.set("derive")),
                button("Fail commit", () -> phase.set("commit")),
                button("Fail native error", () -> phase.set("native-error")),
                button("Fail native object", () -> phase.set("native-object")),
                button("Fail native null", () -> phase.set("native-null")),
                button("Fail native undefined", () -> phase.set("native-undefined")),
                button("Fail own boundary", () -> phase.set("own")),
                button("Reset errors", () -> {
                    phase.set("ok"); outer.current().reset();
                    if (inner.current() != null) inner.current().reset();
                }),
                button("Remount boundaries", () -> key.update(value -> value + 1)),
                Views.component(BOUNDARY, new BoundaryProps("outer", nested, false), "outer-" + key.value(), outer));
    }
    @JSBody(script = "return new Error('foreign render');") private static native JSObject nativeError();
    @JSBody(script = "return Object.freeze({message: 'foreign object', stack: new Error('foreign object').stack});") private static native JSObject nativeObject();
    @JSBody(params = {"error", "object"}, script = "return function NativeFailure(props) { if (props.phase === 'native-error') throw error; if (props.phase === 'native-object') throw object; if (props.phase === 'native-null') throw null; if (props.phase === 'native-undefined') throw undefined; throw new Error('Invalid foreign throw mode'); };")
    private static native JSObject nativeComponent(JSObject error, JSObject object);
    @JSBody(params = "phase", script = "return {phase};") private static native JSObject foreignProps(String phase);
    @JSBody(params = {"value", "error", "object"}, script = "return value === error ? 'native-error' : value === object ? 'native-object' : value === null ? 'native-null' : value === undefined ? 'native-undefined' : 'unexpected';")
    private static native String nativeLabel(JSObject value, JSObject error, JSObject object);
    @JSBody(params = {"first", "second"}, script = "return Object.is(first, second);")
    private static native boolean sameNative(JSObject first, JSObject second);
    @JSBody(params = {"value", "projected"}, script = "var property = value !== null && typeof value === 'object' ? Object.getOwnPropertyDescriptor(value, 'stack') : null; var expected = property && typeof property.value === 'string' ? property.value : null; return projected === expected;")
    private static native boolean stackMatches(JSObject value, String projected);
}
