package jx.fixture;

import java.util.function.Supplier;
import jx.*;
import jx.react.React;
import org.teavm.jso.JSBody;

/** Compiling consumer for scalar identity; JS bodies only record observable test events. */
public final class ScalarBoundaryFixture {
    private ScalarBoundaryFixture() {}
    private static final Context<Double> NUMBER = Hooks.createContext(0.0);
    private static final ComponentType<Void> STATE = new ComponentType<>("ScalarState", ignored -> state());
    private static final ComponentType<Void> DEPENDENCIES = new ComponentType<>("ScalarDependencies", ignored -> dependencies());
    private static final ComponentType<Void> CONTEXT_VALUE = new ComponentType<Void>("ScalarContextValue", ignored ->
            output("context-value", sign(Hooks.useContext(NUMBER)))).memo((before, after) -> true);
    private static final ComponentType<Void> CONTEXT = new ComponentType<>("ScalarContext", ignored -> context());
    private static final ComponentType<Void> APPLICATION = new ComponentType<>("ScalarBoundary", ignored ->
            Views.element("main", Attributes.create(), Views.component(STATE, null),
                    Views.component(DEPENDENCIES, null), Views.component(CONTEXT, null)));

    public static void main() { React.mountStrict("root", APPLICATION, null); }

    private static View state() {
        State<Double> number = Hooks.useState(0.0);
        String current = sign(number.value());
        Hooks.useEffect(() -> { report("state", current); return null; }, Dependencies.of(number.value()));
        Hooks.useLayoutEffect(() -> { report("state-commit", current); return null; }, Dependencies.always());
        return Views.element("section", Attributes.create(), output("state-value", current),
                button("state-negative", event -> number.set(-0.0)),
                button("state-positive", event -> number.set(0.0)));
    }

    private static View dependencies() {
        // Boolean updates force rendering independently of numeric state equality.
        State<Boolean> negative = Hooks.useState(false);
        State<Integer> noise = Hooks.useState(0);
        State<String> inspected = Hooks.useState("uninspected");
        double number = negative.value() ? -0.0 : 0.0;
        String current = sign(number);
        String memo = Hooks.useMemo(() -> { report("memo", current); return current; }, Dependencies.of(number));
        Supplier<String> callback = Hooks.useCallback(() -> current, Dependencies.of(number));
        Ref<Supplier<String>> handle = Hooks.useRef((Supplier<String>) null);
        Hooks.useImperativeHandle(handle, () -> () -> current, Dependencies.of(number));
        Hooks.useInsertionEffect(() -> { report("insertion", current); return null; }, Dependencies.of(number));
        Hooks.useLayoutEffect(() -> { report("layout", current); return null; }, Dependencies.of(number));
        Hooks.useEffect(() -> { report("effect", current); return null; }, Dependencies.of(number));
        return Views.element("section", Attributes.create(), output("dependency-value", current),
                output("memo-value", memo), output("callback-value", callback.get()),
                output("handle-value", inspected.value()), output("noise-value", noise.value().toString()),
                button("dependency-negative", event -> negative.set(true)),
                button("dependency-positive", event -> negative.set(false)),
                button("dependency-noise", event -> noise.update(value -> value + 1)),
                button("inspect-handle", event -> inspected.set(handle.current().get())));
    }

    private static View context() {
        // A memoized child must receive context changes even when its props stay equal.
        State<Boolean> negative = Hooks.useState(false);
        double number = negative.value() ? -0.0 : 0.0;
        return Views.element("section", Attributes.create(),
                output("provider-value", sign(number)),
                button("context-negative", event -> negative.set(true)),
                button("context-positive", event -> negative.set(false)),
                NUMBER.provide(number, Views.component(CONTEXT_VALUE, null)));
    }

    private static String sign(double number) { return 1.0 / number == Double.NEGATIVE_INFINITY ? "-0" : "+0"; }
    private static View output(String id, String text) { return Views.element("output", Attributes.create().attr("id", id), text); }
    private static View button(String id, EventHandler handler) { return Views.element("button", Attributes.create().attr("id", id).on("onClick", handler), id); }
    @JSBody(params = {"kind", "value"}, script = "if (!globalThis.scalarEvents) globalThis.scalarEvents = []; globalThis.scalarEvents.push({kind: kind, value: value});")
    private static native void report(String kind, String value);
}
