package jx.fixture;

import jx.*;
import jx.router.Location;
import jx.router.Route;
import jx.router.Router;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.teavm.jso.JSBody;

/** Router -> immutable location -> owned resource, without a second state owner.
 * Controlled task completion is the external-data boundary of this fixture. */
final class RouteResourceApplication {
    private record Page(String id, String filter) {}
    private record Problem(String code) {}
    private record Pending(Page page, Consumer<Task.Result<Page, Problem>> completion) {}
    private static final List<Pending> pending = new ArrayList<>();
    private static final Route DETAIL = new Route("/todos/:id");
    private static final ComponentType<Router> PAGE = new ComponentType<>("RouteResourcePage", router -> {
        Resource<Page, Problem> data = useRouteData(router);
        String text = switch (data) {
            case Resource.Loading<Page, Problem> ignored -> "loading";
            case Resource.Ready<Page, Problem> ready -> ready.value().id() + "|" + ready.value().filter();
            case Resource.Failed<Page, Problem> failed -> "failed:" + failed.error().code();
        };
        return Views.element("output", Attributes.create().attr("id", "route-resource-result"), text);
    });
    static final ComponentType<Void> APP = new ComponentType<>("RouteResourceApplication", ignored -> {
        Router router = Hooks.useMemo(Router::new, Dependencies.none());
        Location location = Hooks.useSyncExternalStore(router);
        State<Boolean> mounted = Hooks.useState(true);
        State<Integer> pulse = Hooks.useState(0);
        return Views.element("main", Attributes.create(),
                Views.element("output", Attributes.create().attr("id", "route-resource-location"), location.path() + "?" + location.query()),
                Views.element("output", Attributes.create().attr("id", "route-resource-pulses"), pulse.value()),
                button("route-resource-second", event -> router.navigate("/todos/second%20item?filter=active")),
                button("route-resource-query", event -> router.replace("/todos/second%20item?filter=done")),
                button("route-resource-denied", event -> router.navigate("/todos/denied?filter=all")),
                button("route-resource-missing", event -> router.navigate("/not-a-detail")),
                button("route-resource-pulse", event -> pulse.update(value -> value + 1)),
                button("route-resource-mounted", event -> mounted.update(value -> !value)),
                button("route-resource-finish-first", event -> finish("first", "all")),
                button("route-resource-finish-active", event -> finish("second item", "active")),
                button("route-resource-finish-done", event -> finish("second item", "done")),
                button("route-resource-finish-denied", event -> finish("denied", "all")),
                mounted.value() ? Views.component(PAGE, router) : Views.fragment());
    });

    /** Ordinary custom-hook composition; a .jx component can use the same code. */
    private static Resource<Page, Problem> useRouteData(Router router) {
        Location location = Hooks.useSyncExternalStore(router);
        return Hooks.useResource(location, RouteResourceApplication::load);
    }

    private static Task<Page, Problem> load(Location location) {
        var matched = DETAIL.match(location);
        if (matched.isEmpty()) return completion -> {
            completion.accept(new Task.Failure<>(new Problem("NOT_FOUND")));
            return Cancellation.NONE;
        };
        Page page = new Page(matched.get().parameter("id"), location.queryParameter("filter").orElse("all"));
        return completion -> {
            if (pending.size() >= 64) throw new IllegalStateException("Route fixture task capacity exceeded");
            pending.add(new Pending(page, completion));
            report("start", page.id(), page.filter());
            return () -> report("cancel", page.id(), page.filter());
        };
    }

    private static void finish(String id, String filter) {
        for (Pending operation : List.copyOf(pending)) {
            Page page = operation.page();
            if (!page.id().equals(id) || !page.filter().equals(filter)) continue;
            report("reply", id, filter);
            operation.completion().accept(id.equals("denied")
                    ? new Task.Failure<>(new Problem("DENIED")) : new Task.Success<>(page));
        }
    }

    private static View button(String id, EventHandler action) {
        return Views.element("button", Attributes.create().attr("id", id).on("onClick", action), id);
    }
    @JSBody(params = {"kind", "id", "filter"}, script = "(globalThis.routeResourceEvents || (globalThis.routeResourceEvents = [])).push({kind: kind, id: id, filter: filter});")
    private static native void report(String kind, String id, String filter);
}
