package jx.fixture;

import jx.*;
import jx.browser.Json;
import jx.elm.*;
import jx.react.HotReload;
import jx.react.React;
import java.util.List;
import org.teavm.jso.*;

/** New copies of this TeaVM module deliberately have independent Java class identities. */
public final class HotApplication {
    private record Model(int count, List<String> labels) { Model { labels = List.copyOf(labels); } }
    private static final StateCodec<Model> CODEC = new StateCodec<>() {
        public String schema() { return "fixture:model:" + codecVersion(); }
        public String encode(Model value) { return Json.object().put("count", Json.number(value.count())).put("labels", Json.array(value.labels().stream().map(Json::string).toList())).stringify(); }
        public Model decode(String text) { Json json = Json.parse(text); return new Model(json.get("count").intValue(), json.get("labels").items().stream().map(Json::stringValue).toList()); }
    };
    private static Store<Model, Integer> store;
    private static final Component<String> COUNTER = new Component<>("hot.Counter", signature(), label -> {
        State<Integer> count = Hooks.useState(0);
        Hooks.useEffect(() -> { report("setup", label); return () -> report("cleanup", label); }, Dependencies.none());
        return button("hot-counter-" + label, label + ": " + count.value(), event -> count.update(old -> old + 1));
    });
    private static final Component<Void> APP = new Component<>("hot.Application", "app:state,string,reducer,model,external,ref,effect:1", ignored -> {
        if (failStartup()) throw new IllegalStateException("Hot fixture startup failed");
        State<Boolean> reverse = Hooks.useState(false);
        State<String> draft = Hooks.useState("");
        ReducerState<Integer, Integer> reducer = Hooks.useReducer(Integer::sum, 0);
        State<Model> record = Hooks.useState(new Model(0, List.of("record")), CODEC);
        Model model = Hooks.useSyncExternalStore(store);
        Ref<String> ref = Hooks.useRef(label());
        Hooks.useEffect(() -> { store.start(); return store::stop; }, Dependencies.none());
        // A cross-generation Java object would fail these current-runtime instanceof checks.
        if (!(record.value() instanceof Model) || !(model instanceof Model)) throw new IllegalStateException("Old Java instance crossed the runtime boundary");
        View a = Views.component(COUNTER, "A", "A");
        View b = Views.component(COUNTER, "B", "B");
        return Views.element("main", Attributes.create(),
            Views.element("h1", Attributes.create().attr("id", "hot-label"), label()),
            Views.element("input", Attributes.create().attr("id", "hot-draft").attr("value", draft.value()).on("onChange", event -> draft.set(event.targetValue()))),
            button("hot-reverse", "Reverse", event -> reverse.update(old -> !old)),
            Views.element("section", Attributes.create().attr("id", "hot-counters"), reverse.value() ? List.of(b, a) : List.of(a, b)),
            button("hot-reducer", "Reducer " + reducer.value(), event -> reducer.dispatch().accept(3)),
            button("hot-record", "Record " + record.value().count() + " " + record.value().labels().getFirst(), event -> record.update(old -> new Model(old.count() + 1, old.labels()))),
            button("hot-model", "Model " + model.count() + " " + model.labels().getFirst(), event -> store.dispatch(1)),
            Views.element("output", Attributes.create().attr("id", "hot-ref"), ref.current()));
    });
    public static void main() {
        Program<Model, Integer> program = new Program<>() {
            public Transition<Model, Integer> init() { return new Transition<>(new Model(0, List.of("elm")), List.of(new Command<>("init", reply -> { report("init-command", "store"); return Cancellation.NONE; }))); }
            public Transition<Model, Integer> update(Model model, Integer message) { return Transition.stay(new Model(model.count() + message, model.labels())); }
            public View view(Model model, Dispatch<Integer> dispatch) { return Views.fragment(); }
            public List<Subscription<Integer>> subscriptions(Model model) { return List.of(new Subscription<>("source", reply -> { report("subscription", "store"); return () -> report("unsubscribe", "store"); })); }
        };
        store = new Store<>(program);
        HotReload.trackStore("fixture-store", "workflow:1", store, CODEC);
        React.mountStrict("root", APP, null);
    }
    private static View button(String id, String label, EventHandler handler) { return Views.element("button", Attributes.create().attr("id", id).on("onClick", handler), label); }
    @JSBody(script = "return globalThis.hotFixtureLabel || 'Before edit';") private static native String label();
    @JSBody(script = "return globalThis.hotFixtureSignature || 'counter:state,effect:1';") private static native String signature();
    @JSBody(script = "return globalThis.hotFixtureCodecVersion || '1';") private static native String codecVersion();
    @JSBody(script = "return !!globalThis.hotFixtureFailStartup;") private static native boolean failStartup();
    @JSBody(params = {"kind", "name"}, script = "if (!globalThis.hotFixtureEvents) globalThis.hotFixtureEvents = []; globalThis.hotFixtureEvents.push({kind: kind, name: name});") private static native void report(String kind, String name);
}
