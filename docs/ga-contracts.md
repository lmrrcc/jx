# GA proposal: shared Java contracts and Spring integration

Status: design proposal, 19 November 2026, with a bounded locally verified
Todo-record slice added on 26 November. This is an appendix to the
[GA roadmap](ga-roadmap.md). Only the scope in
[ADR0009](decisions/0009-shared-record-wire-contract.md) is implemented;
the broader generated-protocol design below remains proposed.

## Consumer promise

A developer authors a public operation and its transport values once. The server
executes the operation with its own authorization and persistence. The browser gets
a typed asynchronous client, correct codecs and safe errors without hand-copying
record fields, endpoint strings or JSON property names. Pure reusable domain
calculations can run on both sides. Network calls remain visibly asynchronous.

Current evidence: [todo-contract](../examples/todo-contract/src/main/java/jx/example/contract/Todo.java)
now supplies the same response record and operation paths to Spring and the browser.
The Maven `record-codecs` goal generates its browser read/write codec from javac
record symbols; the actual Spring/TeaVM Todo flow passes all three Chrome scenarios.
Request DTO generation, richer wire policies, typed async operations and server
adapters remain open. The [browser contract](browser.md) supplies bounded
cancellable JSON requests; this first record codec is not a complete protocol.

## Module and dependency model

Suggested consumer modules (names are illustrative):

| Module | Contains | Must not depend on |
| --- | --- | --- |
| `domain-shared` | Immutable IDs, money/value types, deterministic calculations and pure validation | Spring, database entities, HTTP session, browser globals |
| `api-contract` | Public request/response values, operations, error codes, wire schema and evolution metadata | Persistence entities, credentials, implementation services |
| `web` | Java/.jx UI, generated async clients/codecs, browser adapters | Server implementation/Jackson/Spring runtime |
| `server` | Boot controllers/adapters, services, authorization, transactions and private domain model | UI component execution as JVM code |

Use shared types only where semantics genuinely match. A server-private domain
object, public DTO and editable form can legitimately differ. Generate transport
projections where appropriate, but do not remove a necessary security boundary in
the name of avoiding duplication. The browser must never receive every field of
an internal entity merely because the entity is easy to serialize.

The browser bytecode baseline is Java 25. Existing services may use Java 21 or
Kotlin. Establish the minimum shared-contract bytecode level separately: compile
portable contract artifacts for the oldest supported server where feasible while
JX browser code remains Java 25. If that cannot represent a required type, use
schema-derived equivalents or an explicit server upgrade. A Java 25 class file
cannot simply be loaded by a Java 21 backend.

## One authority, several generated targets

Build a small, typed contract intermediate representation (IR), containing:
operation ID, HTTP verb/path, path/query/header parameters, request/response types,
media types, status/media-type variants, typed response headers (including ETag,
Location, Retry-After and pagination metadata), declared errors, required/optional/null rules,
limits, pagination/cursors, authentication requirements and version metadata.
The declaration of auth requirements helps clients and tests; it never grants
authority or replaces server authorization.

Three input paths feed the same IR:

1. **Java-first new application:** one neutral Java declaration with values and
   endpoint metadata. Generate the server boundary and an async browser client.
   Choose annotation/interface syntax only after a compiling design spike.
2. **Existing Spring application:** import a documented subset of controller or
   HTTP-interface metadata. Compare with actual Spring handler mappings and the
   configured serialization rules. Unsupported custom behavior must produce a
   diagnostic or require an explicit adapter, not silently guessed JSON.
3. **Existing external API:** its OpenAPI/JSON Schema remains authoritative.
   Generate Java values/codecs and clients from it. This supports Gandalf's Rust
   services and Kotlin systems without forcing a Java backend rewrite.

Each API chooses exactly one authority; generated OpenAPI and generated Java are
not independently editable sources of truth. Stable operation IDs, deterministic
ordering, source locations and generator/version fingerprints make drift visible.
Publish contract artifacts for separately versioned repositories and clean-cache
consumers, not only Maven reactor siblings.

### Illustrative flow, not proposed final syntax

For an operation `createTask(CreateTask) → Task`, the authored contract owns the
path, method, input and public outcomes. Generation produces:

- A browser async operation returning an owned cancellable task/result, with
  deadlines and typed transport/domain failures.
- A server adapter calling the application service under the server's security
  and transaction policy; the domain service need not speak browser task types.
- Record codecs, validation metadata for the supported portable subset, a schema
  document and compatibility fixtures.

The developer writes the actual service behavior and UI state transition. The
generator does not claim exactly-once execution, infer safe retries, expose all
service methods, or turn arbitrary Spring beans into remotely callable objects.

## Wire decisions required before contract v1

| Concern | Proposed rule / acceptance |
| --- | --- |
| IDs and 64-bit integers | Choose representation per declared type/field, never per individual value. Proposed default: all `long`/arbitrary integer wire values use canonical strings, including small values. Test JVM min/max, ±2^53 boundaries and mixed-version decoding |
| Money and decimal | Decimal strings with explicit currency/unit, scale and rounding where meaningful. No implicit conversion through JS Number |
| Floating point | Explicit finite-value policy; NaN/infinity/signed-zero treatment specified independently from React dependency comparison |
| Missing/null/default | Distinguish absent from null and required from optional. Defaults applied at a named boundary; PATCH semantics must not accidentally clear fields |
| Dates/time | Separate instant, local date and zoned/offset representations; specify precision and timezone data. Test DST gaps, offsets and locale-independent wire text |
| Records/enums/sealed variants | Explicit discriminator/unknown-variant rules; no polymorphic arbitrary-class deserialization. Added enum cases must have a deliberate compatibility policy |
| Collections/maps | Bounded size/depth; immutable decoded ownership; explicit key restrictions/order semantics; no cyclic object graphs by default |
| Errors | Stable code, HTTP status and field paths; safe Problem Details-compatible envelope. UI branches on codes, not free-form exception text |
| Concurrency | Version/ETag preconditions, conflict result and user recovery. Mutation retries require declared idempotency keys and server deduplication |
| Pagination | Bounded pages, opaque cursors, stable filter/sort encoding, cancellation and stale-generation protection |
| Validation | Pure reusable rules may execute twice; server always validates. Unsupported Bean Validation/custom validators remain server-owned with typed returned field errors |
| Custom codecs | Explicit registered schema and ownership, deterministic round trips, bounds and conformance vectors; no unconstrained reflection fallback |

Pin actual Jackson/Boot behavior in tests. A generated schema can disagree with
custom serializers, naming strategies, null rules or controller advice. Test real
HTTP responses and inbound request binding/validation, defaults, media negotiation
and controller-advice outcomes, not just two instances of the same generator.

## Async, state and session ownership

Define one browser task abstraction before expanding HTTP, loaders, Suspense and
Elm adapters: success, declared domain failure, transport failure, cancellation,
timeout and unexpected programmer failure remain distinguishable. Cancellation
must dispose owned resources and suppress stale callbacks; it cannot promise that
the remote mutation never committed. Unknown mutation outcome requires an explicit
status/idempotency recovery path.

One session owner coordinates refresh/logout/tenant changes across requests, stores
and tabs. On identity-generation changes: abort owned work, discard stale responses,
clear protected caches and stop streams. Do not retry every mutation after a token
refresh. Keep HttpOnly session/refresh credentials under the existing backend policy;
generated clients must not introduce new localStorage token persistence by default.
Revalidate on tab/BFCache resume. Cross-tab notifications improve responsiveness;
the backend remains authoritative even if a notification is lost.

## Spring development and production

**Preferred development candidate:** the Spring backend is the browser-visible
origin, with narrowly configured forwarding of development assets/hot channels.
Alternative: a constrained local proxy with explicit auth/cookie/redirect rules.
Choose after an actual session/CSRF/OIDC test; neither is proven today.

Current `DevelopmentProxy` deliberately drops Cookie, Authorization and Set-Cookie.
Do not casually turn it into a general-purpose authenticated proxy. Required tests:
host/origin restrictions, cookie path/domain/secure behavior, CSRF refresh after
login/logout, redirects, WebSocket/SSE handshakes, header allowlists, body bounds,
SSRF prevention, cancellation and separate trusted-project launch rules.

The starter integrates classpath fingerprinted assets, manifest and shell routes;
does not create an allow-all security chain; respects custom beans/properties; and
must be removable without corrupting ordinary Boot behavior. Test `/api/missing`,
non-GET browser paths, missing chunks, management routes, context paths, forwarded
prefixes, CSP, cache headers and MIME types. The maintained example must run from
an empty directory with only the packaged CSR/SSG artifact and normal runtime
configuration. Request-time SSR separately qualifies its renderer process/service.

Coordinate backend start/restart and UI rebuild in the IDE without orphaned
processes. Expose one URL, useful original-source failures and a visible distinction
between browser-only `Main` and JVM application entry points.

## Evolution and compatibility gates

Separate Java source/binary compatibility, generator metadata compatibility and
wire compatibility. SemVer alone is not an interoperability test. For a documented
deployment skew window, test old client/new server, new client/old server where
supported, and rollback with old cached chunks. Permit additive changes only when
the actual readers tolerate them. Breaking changes require versioning and a
consumer migration, not regeneration followed by a silently coordinated release.

First milestone: change the shared Todo contract once, regenerate, compile both
consumers, and exercise an actual browser request through Spring. An incompatible
field/type change must fail compatibility checks. A negative fixture must reject
an entity or dependency pulling JDBC/server credentials into the browser closure.
Later: external Boot/Kotlin and schema-first Rust consumers; two-tenant auth;
streaming/upload; old-tab and rollback tests; clean published-artifact onboarding.

Review references: [Spring mappings](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html),
[Spring CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html),
[TeaVM compile-time metaprogramming](https://teavm.org/docs/runtime/metaprogramming.html).
These establish mechanisms and constraints; the design above is JX's proposed work.
