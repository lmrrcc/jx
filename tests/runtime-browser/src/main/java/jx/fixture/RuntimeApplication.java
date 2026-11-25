package jx.fixture;

import jx.*;
import jx.elm.*;
import jx.react.React;
import jx.browser.Http;
import jx.browser.Json;
import jx.router.Router;
import jx.router.Location;
import jx.router.Route;
import java.util.List;
import java.util.function.Supplier;
import org.teavm.jso.*;

/** Compiling consumer fixture; Java owns application logic, JS bodies only instrument browser boundaries. */
public final class RuntimeApplication {
    public record CounterProps(String label, int initial) {}
    private record Value(int value) {}
    private static final Context<String> THEME = Hooks.createContext("default");
    private static final Context<Integer> SCALAR_CONTEXT = Hooks.createContext(0);
    private static final ComponentType<CounterProps> COUNTER = new ComponentType<>("Counter", RuntimeApplication::counter);
    private static final ComponentType<Void> CHECKS = new ComponentType<>("HookChecks", ignored -> checks());
    private static final ComponentType<Void> BROWSER = new ComponentType<>("Browser", ignored -> browser());
    private static final ComponentType<Void> ELM = new ComponentType<>("Elm", ignored -> elm());
    private static final ComponentType<Void> BROKEN = new ComponentType<>("Broken", ignored -> {
        State<Boolean> broken = Hooks.useState(false);
        if (broken.value()) throw new IllegalStateException("render exploded");
        return button("throw-render", "Throw during render", event -> broken.set(true));
    });
    private static final ComponentType<String> FOREIGN = React.binding("ForeignBadge", foreign(), RuntimeApplication::foreignProps);
    private static final ComponentType<Void> APP = new ComponentType<>("Application", ignored -> {
        State<Boolean> reverse = Hooks.useState(false);
        State<Boolean> hidden = Hooks.useState(false);
        State<Integer> parent = Hooks.useState(0);
        State<String> error = Hooks.useState("");
        View a = hidden.value() ? Views.fragment() : Views.component(COUNTER, new CounterProps("A", 0), "A");
        View b = Views.component(COUNTER, new CounterProps("B", 10), "B");
        return SCALAR_CONTEXT.provide(Integer.valueOf(1000), THEME.provide("dark", Views.element("main", Attributes.create(),
            Views.element("h1", Attributes.create(), "JX runtime contracts"),
            button("reverse", "Reverse", event -> reverse.update(old -> !old)),
            button("hide", "Toggle A", event -> hidden.update(old -> !old)),
            button("parent", "Parent " + parent.value(), event -> parent.update(old -> old + 1)),
            Views.element("section", Attributes.create().attr("id", "counters"), reverse.value() ? List.of(b, a) : List.of(a, b)),
            Views.component(CHECKS, null), Views.component(ELM, null), Views.component(BROWSER, null),
            React.boundary(Views.component(BROKEN, null), failure -> Views.element("p", Attributes.create().attr("id", "render-error"), failure.getMessage())),
            button("throw-event", "Throw event", React.guarded(event -> { throw new IllegalArgumentException("event exploded"); }, failure -> error.set(failure.getMessage()))),
            Views.element("output", Attributes.create().attr("id", "event-error"), error.value()),
            Views.component(FOREIGN, "Java bound prop"))));
    });
    private static View counter(CounterProps props) {
        State<Integer> count = Hooks.useStateLazy(props::initial);
        String theme = Hooks.useContext(THEME);
        Hooks.useEffect(() -> { report("setup", props.label(), count.value()); return () -> report("cleanup", props.label(), count.value()); }, Dependencies.of(count.value()));
        return button("counter-" + props.label(), props.label() + ": " + count.value() + " " + theme, event -> {
            count.update(old -> old + 1); count.update(old -> old + 1); count.update(old -> old + 1);
            report("snapshot", props.label(), count.value());
        });
    }
    private static View checks() {
        State<Integer> number = Hooks.useState(1000);
        State<Integer> noise = Hooks.useState(0);
        State<Supplier<String>> callable = Hooks.useState(() -> "first");
        Ref<Value> stable = Hooks.useRef(new Value(1));
        Ref<Integer> effectCount = Hooks.useRef(0);
        DomRef input = Hooks.useDomRef();
        String id = Hooks.useId();
        Supplier<String> callback = Hooks.useCallback(() -> "callback", Dependencies.none());
        Ref<Supplier<String>> firstCallback = Hooks.useRef(callback);
        ReducerState<Integer, Integer> reducer = Hooks.useReducer(Integer::sum, 0);
        Integer scopedNumber = Hooks.useContext(SCALAR_CONTEXT);
        Ref<Integer> firstScopedNumber = Hooks.useRef(scopedNumber);
        Hooks.useEffect(() -> { effectCount.set(effectCount.current() + 1); report("scalar-effect", "checks", effectCount.current()); return null; }, Dependencies.of(Integer.valueOf(number.value()), stable.current(), callback));
        Hooks.useEffect(() -> { report("identity-effect", "checks", noise.value()); return null; }, Dependencies.of(new Value(1)));
        Hooks.useLayoutEffect(() -> { input.current().focus(); return null; }, Dependencies.none());
        return Views.element("section", Attributes.create(),
            Views.element("label", Attributes.create().attr("for", id), "Controlled value"),
            Views.element("input", Attributes.create().attr("id", id).attr("ref", input).attr("value", callable.value().get()).on("onChange", event -> { String value = event.targetValue(); callable.set(() -> value); })),
            button("noise", "Noise " + noise.value(), event -> noise.update(old -> old + 1)),
            button("scalar", "Same scalar", event -> number.set(Integer.valueOf(1000))),
            button("callable", "Set callable", event -> callable.set(() -> "second")),
            button("reduce", "Reducer " + reducer.value(), event -> { reducer.dispatch().accept(1); reducer.dispatch().accept(2); }),
            Views.element("output", Attributes.create().attr("id", "callback-stable"), callback == firstCallback.current() ? "stable" : "changed"),
            Views.element("output", Attributes.create().attr("id", "context-stable"), scopedNumber == firstScopedNumber.current() ? "stable" : "changed"),
            Views.element("p", Attributes.create().attr("id", "escaped").attr("style", Style.create().property("color", "rgb(1, 2, 3)")), "<script>unsafe</script>"));
    }
    private static View browser() {
        State<String> result = Hooks.useState("idle");
        Ref<jx.browser.Cancellation> pending = Hooks.useRef(() -> {});
        Router router = Hooks.useMemo(Router::new, Dependencies.none());
        Location location = Hooks.useSyncExternalStore(router);
        Hooks.useEffect(() -> () -> pending.current().cancel(), Dependencies.none());
        java.util.function.Consumer<Http.Result<String>> complete = response -> result.set(switch (response) {
            case Http.Success<String> success -> success.status() + " " + success.value();
            case Http.Failure<String> failure -> failure.kind().name() + " " + failure.status();
        });
        java.util.function.Consumer<Http.Request> request = value -> {
            pending.current().cancel(); result.set("loading");
            pending.set(Http.send(value, json -> {
                if (json.isNull()) return "empty";
                if (json.has("version") && (json.get("version").intValue() != 7 || !json.get("completed").booleanValue())) throw new IllegalArgumentException("Invalid fixture schema");
                if (json.has("items") && !json.get("items").items().getFirst().get("title").stringValue().equals("nested")) throw new IllegalArgumentException("Invalid array schema");
                return json.get("title").stringValue();
            }, complete));
        };
        String parameter = new Route("/todos/:id").match(location).map(match -> match.parameter("id")).orElse("none");
        return Views.element("section", Attributes.create(),
            Views.element("output", Attributes.create().attr("id", "http-result"), result.value()),
            button("http-get", "GET", event -> request.accept(Http.Request.get("/api/value"))),
            button("http-post", "POST", event -> request.accept(Http.Request.post("/api/echo", Json.object().put("title", Json.string("posted")).put("completed", Json.bool(true)).put("version", Json.number(7))))),
            button("http-error", "HTTP error", event -> request.accept(Http.Request.get("/api/error"))),
            button("http-invalid", "Invalid JSON", event -> request.accept(Http.Request.get("/api/invalid"))),
            button("http-decode", "Wrong schema", event -> request.accept(Http.Request.get("/api/decode"))),
            button("http-empty", "Empty response", event -> request.accept(Http.Request.delete("/api/empty"))),
            button("http-limit", "Body limit", event -> request.accept(new Http.Request(Http.Method.GET, "/api/value", null, 1000, 8))),
            button("http-timeout", "Timeout", event -> request.accept(new Http.Request(Http.Method.GET, "/api/slow", null, 20, 1024))),
            button("http-cancel", "Cancel", event -> { request.accept(Http.Request.get("/api/slow")); pending.current().cancel(); result.set("cancelled"); }),
            Views.element("output", Attributes.create().attr("id", "location"), location.path() + " " + location.queryParameter("filter").orElse("all") + " " + parameter),
            button("navigate", "Navigate", event -> router.navigate("/todos/item%20one?filter=active")),
            button("replace", "Replace", event -> router.replace("/todos/item%20two?filter=done")));
    }
    private record Model(int value, String status, boolean listening) {}
    private sealed interface Message permits Increment, Request, Response, Toggle {}
    private record Increment() implements Message {}
    private record Request(int value, int delay) implements Message {}
    private record Response(int value) implements Message {}
    private record Toggle() implements Message {}
    private static final Program<Model, Message> PROGRAM = new Program<>() {
        @Override public Transition<Model, Message> init() { return Transition.stay(new Model(0, "idle", false)); }
        @Override public Transition<Model, Message> update(Model model, Message message) {
            return switch (message) {
                case Increment ignored -> Transition.stay(new Model(model.value() + 1, model.status(), model.listening()));
                case Request request -> new Transition<>(new Model(model.value(), "loading", model.listening()), List.of(new Command<>("request", reply -> {
                    delayed(() -> { report("response-attempt", "elm", request.value()); reply.send(new Response(request.value())); }, request.delay());
                    return () -> report("command-cancel", "elm", request.value()); // Deliberately non-cooperative transport: stale-result guard must still work.
                })));
                case Response response -> Transition.stay(new Model(response.value(), "success", model.listening()));
                case Toggle ignored -> Transition.stay(new Model(model.value(), model.status(), !model.listening()));
            };
        }
        @Override public List<Subscription<Message>> subscriptions(Model model) {
            return model.listening() ? List.of(new Subscription<>("feed", dispatch -> {
                report("subscription-start", "elm", 0);
                return () -> report("subscription-stop", "elm", 0);
            })) : List.of();
        }
        @Override public View view(Model model, Dispatch<Message> dispatch) {
            return Views.element("section", Attributes.create(),
                Views.element("output", Attributes.create().attr("id", "model"), model.value() + " " + model.status()),
                button("elm-increment", "Increment model", event -> dispatch.send(new Increment())),
                button("slow", "Slow request", event -> dispatch.send(new Request(1, 120))),
                button("fast", "Fast request", event -> dispatch.send(new Request(2, 5))),
                button("subscription", "Toggle subscription", event -> dispatch.send(new Toggle())));
        }
    };
    private static View button(String id, String label, EventHandler handler) { return Views.element("button", Attributes.create().attr("id", id).on("onClick", handler), label); }
    private static View elm() { return Programs.useProgram(PROGRAM); }
    public static void main(String[] args) {
        if (hotFixture()) { HotApplication.main(); return; }
        if (scalarBoundaryFixture()) { ScalarBoundaryFixture.main(); return; }
        if (promiseObserverFixture()) React.mountStrict("root", PromiseObserverFixture.COMPONENT, null);
        else React.mountStrict("root", APP, null);
        exported(React.exportComponent(COUNTER, props -> new CounterProps("Export", initial(props))));
    }
    @JSFunctor interface Task extends JSObject { void run(); }
    @JSBody(script = "return !!globalThis.hotFixture;") private static native boolean hotFixture();
    @JSBody(script = "return new URLSearchParams(location.search).get('fixture') === 'scalar-boundary';")
    private static native boolean scalarBoundaryFixture();
    @JSBody(script = "return new URLSearchParams(location.search).get('fixture') === 'promise-observer';")
    private static native boolean promiseObserverFixture();
    @JSBody(params = {"task", "delay"}, script = "setTimeout(task, delay);") private static native void delayed(Task task, int delay);
    @JSBody(params = {"kind", "label", "value"}, script = "if (!globalThis.runtimeEvents) globalThis.runtimeEvents = []; globalThis.runtimeEvents.push({kind: kind, label: label, value: value});") private static native void report(String kind, String label, int value);
    @JSBody(script = "return globalThis.ForeignBadge;") private static native JSObject foreign();
    @JSBody(params = "label", script = "return {label: label};") private static native JSObject foreignProps(String label);
    @JSBody(params = "component", script = "globalThis.ExportedCounter = component;") private static native void exported(JSObject component);
    @JSBody(params = "props", script = "if (!Number.isInteger(props.initial)) throw new TypeError('initial must be integer'); return props.initial;") private static native int initial(JSObject props);
}
