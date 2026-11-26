# Browser HTTP and routing

`jx-browser` and `jx-router` are optional browser-only modules. They do not bring
Spring, persistence, a server session, or server credentials into the browser.
All callbacks execute on the browser thread; application logic remains Java.

## JSON and HTTP

Codecs explicitly validate their wire schema:

```java
import jx.Cancellation;

record Todo(String id, String title, boolean completed, int version) {
    static Todo decode(Json json) {
        return new Todo(json.get("id").stringValue(), json.get("title").stringValue(),
            json.get("completed").booleanValue(), json.get("version").intValue());
    }
}

Cancellation request = Http.get("/api/todos", json ->
    json.items().stream().map(Todo::decode).toList(), result -> {
        // Dispatch a typed success or failure message to the application owner.
    });
// The owning effect/command cancels this operation on disposal or replacement.
request.cancel();
```

`Json.object().put("title", Json.string(title))`, `Json.number(int)`, `Json.bool`,
`Json.array`, and `Json.nil()` construct immutable values. Accessors reject missing
properties and wrong types. `intValue` requires an exact signed 32-bit integer.
There is no reflective bean serialization, arbitrary class loading, or server-side
Jackson dependency. Application codecs must additionally validate domain invariants.

`Http.send(Request.post/put/delete(...), decoder, completion)` returns an idempotent
cancellation handle. `Success<T>(value, status)` includes the decoded value.
`Failure<T>(kind, status, message, body)` distinguishes HTTP, network, timeout,
payload-limit, invalid-JSON, and schema-decoding errors. HTTP failure bodies are
available as JSON when valid, for example a Spring ProblemDetail. A 204 response
decodes `Json.nil()`. Cancellation suppresses completion; timeout reports a failure.
Unexpected application completion-handler exceptions remain visible as errors.

Requests use same-origin absolute paths, `credentials: same-origin`, an Accept
header for JSON, and Content-Type for JSON request bodies. Redirects fail rather
than silently changing the request destination. No cross-origin credential API is
exposed. Defaults are 10 seconds and one MiB; custom Request limits are bounded by
one minute and eight MiB. Request encoding and streamed response reads both enforce
the byte limit. There is no automatic retry. The server still owns authentication,
authorization, CSRF policy, validation, and concurrency controls.

HTTP, Elm commands and subscriptions share `jx.Cancellation` from `jx-core`.
Return `Http.send(...)` directly from command work, as the compiling
[`TodoGateway`](../examples/todo-app/src/main/java/jx/example/todo/TodoGateway.java)
does. Store replacement additionally guards late results from transports that
ignore abort. Use `Cancellation.NONE` when no resource needs releasing.

This pre-GA API consolidation replaces `jx.browser.Cancellation` and
`jx.elm.Cancellation`. Update those imports to `jx.Cancellation` and recompile
consumers; the HTTP and command method return descriptors have changed. The core
contract adds no browser, Spring or networking dependency. Cancellation remains
idempotent, runs on the operation's owning thread and does not roll back server
work that has already happened.

The consolidation's selected Maven reactor (`tests/runtime-browser,examples/todo-app`
with dependencies) passes 144 JVM checks and compiles both TeaVM applications in
25.729 seconds. The complete runtime browser suite passes 16/16 in 5.0 seconds,
including real HTTP cancellation, late Elm replies, owned subscription disposal
and independent hot generations. A JVM regression additionally covers completion
before work returns its cancellation handle: the owner releases it once and
rejects later replies. [PR73](https://github.com/lmrrcc/jx/pull/73) was then
merged as `d9151c0` after the 15-step local gate passed in 313 seconds on
`0bb3f17`, and both [PR Verify](https://github.com/lmrrcc/jx/actions/runs/45791829041)
and [push Verify](https://github.com/lmrrcc/jx/actions/runs/45791655823) passed.
The integrated conformance run passes 975 checks with 217 mode exclusions across
149 cases; both examples and the real hot workflow also pass. Resource-hook
ownership and route-loading integration remain separate G0 tasks.

## Owned resources

`Http.task(request, decoder)` describes work without starting it. Every start
owns an independent HTTP operation and returns `jx.Cancellation`. Its typed
failure is the unchanged `Http.Failure<T>`, including HTTP status, timeout/network
category and response body.

```java
Resource<String, Http.Failure<String>> resource = Hooks.useResource(
    "/api/value",
    path -> Http.task(Http.Request.get(path), json -> json.get("title").stringValue()));

String text = switch (resource) {
    case Resource.Loading<String, Http.Failure<String>> ignored -> "Loading";
    case Resource.Ready<String, Http.Failure<String>> ready -> ready.value();
    case Resource.Failed<String, Http.Failure<String>> failed ->
        "Request failed: " + failed.error().kind();
};
```

The key identifies the complete read: include every reactive input, including
identity/tenant generation and explicit retries when needed. Inline loader lambdas
do not reload an unchanged key. Work starts after commit and is cancelled when the
key changes, the component unmounts or React disconnects its effects. Obsolete or
duplicate replies cannot publish into a new request. No value from the previous
key is exposed while the new key is loading. Effect reconnection starts fresh
work; use repeatable reads here and put mutations in explicit events/commands.

This API neither suspends nor supplies server data or a global cache. Unexpected
loader/start exceptions reach React error handling; declared failures remain
typed data. See [the ownership decision](decisions/0008-resource-task-ownership.md)
and the compiling [resource consumer](../tests/runtime-browser/src/main/java/jx/fixture/ResourceApplication.java).

## History routing

```java
Router router = Hooks.useMemo(Router::new, Dependencies.none());
Location location = Hooks.useSyncExternalStore(router);
String filter = location.queryParameter("filter").orElse("all");
var detail = new Route("/todos/:id").match(location);
// In an event callback:
router.navigate("/todos/123?filter=active");
```

`Location` is an immutable snapshot of encoded path, query, and hash; query/hash
omit their leading punctuation. `queryParameter` URL-decodes values. `Route`
matches exact path templates with named, nonempty segments. Captured parameters
are decoded; malformed encoding and encoded path separators do not match. Unknown
parameter names fail explicitly. This is a small route matcher, not an implicit
filesystem router or a wildcard routing DSL.

`navigate` pushes history, `replace` replaces it, and browser back/forward or hash
changes notify observers. Unchanged locations keep the same snapshot object.
Listeners are attached on first subscription and removed on the last cleanup;
`close` permanently disposes the router. Navigation is restricted to bounded
same-origin absolute paths. The server must serve the app shell for direct route
requests. Routing does not authorize any backend operation.

The runtime browser fixture contains compiling consumers and contract tests for
HTTP results and errors, limits, timeout/cancellation, push/replace, route and query
parameters, and browser back/forward. See [runtime verification](runtime.md).
