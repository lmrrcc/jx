# Java Server Components: Flight feasibility proof

This fixture compiles `ServerPage.jx` and `Counter.jx` into **separate TeaVM
programs**. The server runs under Node's `react-server` condition. The official
`react-server-dom-webpack` renderer emits Flight; its browser decoder loads a
registered client chunk and actual React renders the Java counter. The shared
`CounterProps` record has one source definition, compiled into each runtime.
Only its explicitly encoded native fields cross Flight.

The framework entry point is `ReactServerComponents.element(View)`. It installs
the React hook adapter and returns an element for the host transport; it neither
renders eagerly nor starts an HTTP server. See the compiling Java consumer in
[`ServerEntry`](server/src/main/java/jx/fixture/rsc/server/ServerEntry.java).
Server components can suspend on `Hooks.use(Promise)` while React owns the
request's dispatcher. Client-only hooks and class components remain invalid.

Run from the repository root after `npm ci`:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl tests/rsc-proof/server,tests/rsc-proof/client -am package
npm run test:rsc
```

`test:rsc` builds the framework host and the official webpack client manifest,
runs twenty-eight Node checks, and runs four Chrome checks (two against ordinary React
and two against JX). The loopback browser host defaults to port **4873**;
`RSC_PORT` can override it. It does not accept remote function calls.

The default is below Linux's [default automatic client-port range](https://docs.kernel.org/networking/ip-sysctl.html#ip-variables)
(32768–60999). PR71 Verify attempt 1 completed all 28 Node checks but its browser
host could not bind the old default 59182 (`EADDRINUSE`); no browser assertion ran.
An automatic client-port collision is a plausible explanation, not an identified
owner of that failed CI socket. Port 4873 avoids that default range while keeping
explicit port overrides and normal bind failures visible. No existing server is
reused and no check is skipped or retried by the fixture.

The tests establish:

- Actual Flight bytes and a browser round trip; no substitution with HTML SSR.
- A separately loaded Java client counter that still updates offline.
- Two simultaneously suspended Java server renders completed in reverse order,
  with data isolated to each request. Gates, not elapsed delays, control completion.
- Rejected data and an unknown client reference produce Flight errors; abort
  closes a suspended stream and late data cannot append output.
- A generated, bounded manifest; malformed manifests and chunk paths fail closed.
- Browser chunks **and source maps** exclude the server application and its source.
  A positive control checks the server artifact really contains that application.
- Malformed HTTP requests are rejected and server files are not served as assets.
- Eight differential Node checks (four per side) exercise request-scoped cache
  values, failures, promises, recursive invocation, native key equality and cache
  lifetime notifications. Identical keys in concurrent requests produce distinct
  values; completion of one request does not end another request's scope.
- Twelve differential Node checks keep request cache values and native cache signals
  across `Async.await`, `Promise.map` and rejection recovery. They cover separate
  externally settled promises, one Java promise observed by two requests, and new
  request observers registered after that shared promise has already settled.
- An isolated Node child verifies that a throwing Java observer reports the original
  exception once, without starving sibling observers or the cached native thenable,
  changing their delivery order, or creating an unhandled rejection.

The original server smoke failed because `React` eagerly initialized a client
error-boundary class absent from server-conditioned React. The adapter now
initializes that class only when a boundary is composed. Ordinary client behavior
remains covered by the existing browser gate.

This is R-30-01 evidence, **not complete RSC support**. The two client references
and props codecs are fixture-owned; general manifest generation, directive-based
Java module separation, typed server functions, authorization, version-skew
behavior and production transport/error
redaction remain open. Development Flight can contain server diagnostic metadata;
do not deploy this proof as a production endpoint. These requests carry public
fixture values, so the isolation check is not authenticated G0 isolation evidence.

React's protocol integration is pinned to the exact runtime version. See
[ADR 0007](../../docs/decisions/0007-react-server-components.md) for the build
choice and remaining slices. These dedicated proof results are separate from
the differential capability ledger: they do not automatically mark its RSC rows
supported or add interaction-pair credit.

## Typed server cache

`ReactCache.cache` accepts Java `Supplier`, `Function` and `BiFunction` and returns
the corresponding typed function. The actual React cache owns values, failures,
promises and request expiry; JX does not maintain a process-wide result cache.
The compiling [CacheProbe](server/src/main/jx/jx/fixture/rsc/server/CacheProbe.jx)
demonstrates these contracts, including a recursive function and nested failure.
Null results are cached; callbacks preserve their original typed Java arguments.

Keys use native strings/characters, booleans and boxed primitive numbers. Other
objects, including records and arbitrary-precision numbers, use identity. Inexact
long keys fail before lookup. The pinned React 19.3.1 cache uses native Map semantics
for primitives: NaN matches itself, and positive/negative zero share an entry.
Its request table keys the source function: two wrappers of the **same function
instance** share that source's cache, while distinct functions do not. The initial
reference test exposed this distinction from the description in React's cache
documentation. These are tested runtime semantics, not a promise about future
React versions; dependency upgrades must rerun the differential checks.

`ReactCache.cacheSignal()` returns null outside server rendering, or a read-only
`CacheSignal`. `onAbort` returns an idempotent cleanup and notifies immediately
when the scope already ended. Completion, failure and abort are all tested, as is
removal of a pending listener. `nativeSignal()` exposes the original standard
AbortSignal to explicit host bindings. Ending a React cache does not automatically
cancel arbitrary Java promises or HTTP work: the owner must propagate that signal.
This API is not the still-pending G0 cancellation/useResource implementation.

The typed adapter currently supports zero, one or two arguments. A record key
uses identity, not automatic tuple/value equality. Java wrappers of a native
signal need not have Java reference identity. Production streaming,
GC/retention measurement and the
main capability-matrix RSC qualification remain separate checks.

## Async request ownership

The first paired `await`/`map` regression passed in ordinary React and failed in
JX: the Java promise queued each observer only when another job settled it, so
the observer inherited the resolver's request context. React's cache and cache
signal were consequently unavailable after the Java continuation resumed.

`AsyncRuntime.reaction` now captures a scheduling operation when the observer is
registered. The React adapter registers a native Promise reaction immediately,
then resolves that promise directly at settlement. This retains the host context
without adding another microtask before callback delivery. It does not read React
private dispatchers or introduce a process-global request map. Existing runtimes
can use the default `defer` implementation; subscriptions made before runtime
installation keep their deferred bootstrap behavior and have no host context to
capture at registration.

All twelve differential context checks now pass. The ten JVM Promise checks and
sixteen Chrome runtime checks also pass, including the existing throwing-observer
regression. Browser observer failures use the host's `reportError`; Node's fallback
reports an uncaught microtask error. The child-process test explicitly observes
that error and must not be used as a production exception-recovery pattern.

A pending promise owns its registered observers and their captured context until
settlement or collection. Aborting a Flight request does not itself detach arbitrary
Java promise observers or cancel external work. Cancellation, bounded retention
under abandoned requests, production deployment and authenticated isolation retain
their separate G0 acceptance criteria. Node documents the relevant native promise
context behavior in [asynchronous context tracking](https://nodejs.org/api/async_context.html).
