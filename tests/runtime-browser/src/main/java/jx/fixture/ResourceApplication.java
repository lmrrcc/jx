package jx.fixture;

import jx.*;
import jx.browser.Http;
import jx.react.React;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.teavm.jso.JSBody;

/** Real React hook consumer. Only event/effect callbacks start or settle tasks. */
final class ResourceApplication {
    private static final List<Pending> pending = new ArrayList<>();
    private record Pending(String key, Consumer<Task.Result<String, String>> done) {}
    private record Props(String key, String id) {}
    private static final ComponentType<Props> RESOURCE = new ComponentType<>("OwnedResource", props -> {
        Resource<String, String> resource = Hooks.useResource(props.key(), key -> done -> {
            if (pending.size() >= 64) throw new IllegalStateException("Fixture task capacity exceeded");
            pending.add(new Pending(key, done));
            report("start", key);
            return () -> report("cancel", key);
        });
        String label = switch (resource) {
            case Resource.Loading<String, String> ignored -> "loading";
            case Resource.Ready<String, String> ready -> "ready:" + ready.value();
            case Resource.Failed<String, String> failure -> "failed:" + failure.error();
        };
        Hooks.useLayoutEffect(() -> { report("snapshot", props.key() + ":" + label); return null; }, Dependencies.always());
        return Views.element("output", Attributes.create().attr("id", props.id()), label);
    });
    private static final ComponentType<String> HTTP = new ComponentType<>("HttpResource", url -> {
        Resource<String, Http.Failure<String>> resource = Hooks.useResource(url,
                key -> Http.task(Http.Request.get(key), json -> json.get("title").stringValue()));
        String label = switch (resource) {
            case Resource.Loading<String, Http.Failure<String>> ignored -> "loading";
            case Resource.Ready<String, Http.Failure<String>> ready -> "ready:" + ready.value();
            case Resource.Failed<String, Http.Failure<String>> failed -> "failed:" + failed.error().kind() + ":" + failed.error().status();
        };
        return Views.element("output", Attributes.create().attr("id", "resource-http"), label);
    });
    private static final ComponentType<Promise<String>> UNCOMMITTED = new ComponentType<>("UncommittedResource", gate -> {
        Hooks.useResource("uncommitted", key -> done -> {
            report("start", key);
            return () -> report("cancel", key);
        });
        return Views.fragment(Hooks.use(gate));
    });
    private static final ComponentType<Void> SYNCHRONOUS = new ComponentType<>("SynchronousResource", ignored -> {
        Resource<String, String> resource = Hooks.useResource("synchronous", key -> done -> {
            report("start", key);
            done.accept(new Task.Success<>("immediate"));
            done.accept(new Task.Failure<>("duplicate"));
            return () -> report("cancel", key);
        });
        String text = switch (resource) {
            case Resource.Loading<String, String> loading -> "loading";
            case Resource.Ready<String, String> ready -> "ready:" + ready.value();
            case Resource.Failed<String, String> failed -> "failed:" + failed.error();
        };
        return Views.element("output", Attributes.create().attr("id", "resource-synchronous"), text);
    });
    private static final ComponentType<String> FAILING = new ComponentType<>("FailingResource", mode -> {
        Resource<String, String> resource = Hooks.useResource("failure:" + mode, key -> {
            if (mode.equals("loader")) throw new IllegalStateException("resource loader failed");
            return done -> {
                if (pending.size() >= 64) throw new IllegalStateException("Fixture task capacity exceeded");
                pending.add(new Pending(key, done));
                report("start", key);
                throw new IllegalStateException("resource start failed");
            };
        });
        return Views.element("output", Attributes.create().attr("id", "resource-unexpected-success"), resource.toString());
    });
    private static final class Gate {
        private Promise.Resolver<String> resolver;
        final Promise<String> promise = Promise.create(value -> resolver = value);
        void open() { resolver.resolve("open"); }
    }
    static final ComponentType<Void> APP = new ComponentType<>("ResourceApplication", ignored -> render());

    private static View render() {
        State<String> key = Hooks.useState("A");
        State<Integer> pulse = Hooks.useState(0);
        State<Boolean> mounted = Hooks.useState(true);
        State<Boolean> visible = Hooks.useState(true);
        State<String> url = Hooks.useState("/api/value");
        State<Boolean> httpMounted = Hooks.useState(true);
        State<Boolean> showUncommitted = Hooks.useState(false);
        State<Gate> gate = Hooks.useStateLazy(Gate::new);
        State<String> failureMode = Hooks.useState("");
        return Views.element("main", Attributes.create(),
                button("resource-key-b", event -> key.set("B")),
                button("resource-key-c", event -> key.set("C")),
                button("resource-pulse", event -> pulse.update(value -> value + 1)),
                Views.element("output", Attributes.create().attr("id", "resource-pulses"), pulse.value()),
                button("resource-mounted", event -> mounted.update(value -> !value)),
                button("resource-visible", event -> visible.update(value -> !value)),
                button("resource-finish-a", event -> finish("A", false)),
                button("resource-finish-b", event -> finish("B", false)),
                button("resource-fail-b", event -> finish("B", true)),
                button("resource-finish-c", event -> finish("C", false)),
                button("resource-finish-other", event -> finish("other", false)),
                mounted.value() ? React.activity(visible.value(), Views.component(RESOURCE, new Props(key.value(), "resource-left"), "left")) : Views.fragment(),
                Views.component(RESOURCE, new Props("other", "resource-right"), "right"),
                Views.component(SYNCHRONOUS, null),
                button("resource-http-value", event -> url.set("/api/value")),
                button("resource-http-error", event -> url.set("/api/error")),
                button("resource-http-held", event -> url.set("/api/resource-hold")),
                button("resource-http-mounted", event -> httpMounted.update(value -> !value)),
                httpMounted.value() ? Views.component(HTTP, url.value()) : Views.fragment(),
                button("resource-uncommitted", event -> showUncommitted.update(value -> !value)),
                button("resource-open-gate", event -> gate.value().open()),
                React.suspense(Views.element("output", Attributes.create().attr("id", "resource-suspended"), "waiting"),
                        showUncommitted.value() ? Views.component(UNCOMMITTED, gate.value().promise) : Views.fragment()),
                button("resource-throw-loader", event -> failureMode.set("loader")),
                button("resource-throw-start", event -> failureMode.set("start")),
                button("resource-finish-failed", event -> finish("failure:start", false)),
                failureMode.value().isEmpty() ? Views.fragment()
                        : new View.Keyed(React.boundary(Views.component(FAILING, failureMode.value()), error ->
                                Views.element("output", Attributes.create().attr("id", "resource-unexpected-error"), error.getMessage())), failureMode.value()));
    }

    private static void finish(String key, boolean failure) {
        // Deliberately non-cooperative source: even cancelled tasks still invoke
        // completion. Resource ownership must suppress those stale replies.
        for (Pending value : List.copyOf(pending)) if (value.key().equals(key)) {
            report("reply", key);
            value.done().accept(failure ? new Task.Failure<>("denied:" + key) : new Task.Success<>(key));
        }
    }
    private static View button(String id, EventHandler handler) {
        return Views.element("button", Attributes.create().attr("id", id).on("onClick", handler), id);
    }
    @JSBody(params = {"kind", "value"}, script = "(globalThis.resourceEvents ||= []).push({kind, value});")
    private static native void report(String kind, String value);
}
