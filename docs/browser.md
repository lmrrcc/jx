# Browser HTTP and routing

`jx-browser` and `jx-router` are optional browser-only modules. They do not bring
Spring, persistence, a server session, or server credentials into the browser.
All callbacks execute on the browser thread; application logic remains Java.

## JSON and HTTP

Codecs explicitly validate their wire schema:

```java
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

For Elm commands, return `request::cancel` from the command work. Store command
replacement additionally guards late results from transports that ignore abort.

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
