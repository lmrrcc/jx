# 0005: Lazily loaded modules cross a serialized boundary and share one React

Status: accepted, 2026-11-24. Tasks M1-22 and M1-23.

R-26 conformance-bench extension: implemented, in review; Luca's try-it check and
a green Verify workflow are still required for acceptance.

An application may keep part of its code out of the first download. In JX that part
is a second Maven module compiled by TeaVM into its own JavaScript module, loaded
with a dynamic `import()`.

## What was measured

The fixture in `tests/lazy-boundary` has three Maven modules: a contract (a record
and a context that both sides compile against), a host application and a module
loaded on demand. `npm run test:lazy` bundles the two applications in one esbuild
run with two entry points and code splitting, and records in Chrome:

| Crossing | Result |
| --- | --- |
| The lazy file is requested only when its component first renders | yes |
| A record with a string, an int, a boolean | crosses, encoded as a plain JavaScript object |
| A `long` beyond 2^53 | crosses exactly, encoded as text |
| A callback from the host | crosses, as a JavaScript function |
| Props updates, and state inside the loaded component | work; the state is kept across updates |
| A context declared in the shared contract | does **not** cross: the provider in the host is not seen |
| An exception thrown while the loaded component renders | reaches the host's error boundary with its message; the Java type is lost |
| A failed download | reaches the error boundary; `LazyComponent.retry()` loads it again |

## Conformance-bench extension (R-26)

The bench applies this boundary to its lazy-route rendering mode. Two TeaVM
entry points in the conformance Maven module compile independently: `LazyHost`
contains only the initial activation UI, and `LazyRoute` owns the case registry,
case implementations and their Java contexts. The host imports `route.js` only
after the user opens the route. `React.exportComponent` and `React.binding`
connect the runtimes with empty props; no Java object crosses. The reference React
fixture uses the corresponding host/route split. Both builds share one React.
The route contains the case registry as a whole, not one chunk per case.

The new acceptance checks cover both sides:

- Traverse esbuild's static-import graph, including shared chunks, to reject case
  code in the initial host and require it in the delayed route.
- Read TeaVM's source maps as a second check: host conformance sources are limited
  to `LazyHost.java`, `LazyHostPage.java` and `Bench.java`; the delayed compilation
  must contain `Main.java` and the exercised `cases/StateCounter.java`.
- Observe no route request before activation. Hold the first download and verify
  the loading UI without a committed case; release it and exercise the counter.
- Abort the first download, verify its error UI, then require an explicit retry
  and a second network request. The recovered counter works without replacing
  the original root container.
- Run applicable case behavior specs after the delayed route commits. Generated
  element, attribute and event comparisons use the same reference rendering mode.

`error-reporting` omits the outer route error boundary on both sides so that its
intentional render error can still reach and unmount the root. `strict-nested`
retains its non-Strict host. Root-owning hydration and root-options scenarios, and
the missing-component scenario, are explicitly excluded from lazy mode in
`tests/conformance/modes.tsv`. Node-only server tests remain outside browser modes.

The reporter records client, hydrate, static and lazy columns independently;
only a complete run may update the baseline. The two new curated mutations test
eager activation and accidentally compiling the case registry into the host.
Their scoped result must not be represented as a fresh full mutation campaign.

## Decision

1. **Two Java runtimes, one React.** Each TeaVM compilation has its own classes,
   statics and string and collection implementations. The two applications are
   bundled together so that `react` is one shared chunk; two copies of React would
   break hooks.
2. **The boundary is serialized.** Java objects do not cross. The loading side
   wraps the exported component with `React.binding` and encodes its props; the
   loaded side publishes it with `React.exportComponent` and decodes them into its
   own types, validating what it receives. This is the same rule as hot reload
   ([0003](0003-state-preserving-development.md)): a Java object of one generation
   means nothing to another.
3. **No shared Java runtime.** Compiling both parts into one TeaVM output with
   internal code splitting was not chosen: TeaVM 0.16 produces one module per
   compilation, and one shared runtime would tie every lazy part to the exact build
   of the host.
4. **Retry is explicit.** `LazyModules.load` forgets a failed download, and
   `LazyComponent.retry()` forgets a failed load so that the next render starts it
   again. Nothing retries by itself: an automatic retry inside rendering would
   repeat the download each time React renders the failed component again.

## Consequences and limits

- The props codec at the boundary is written by hand in the fixture. Generating it
  from the record is the same work as the generated contract codec (M1-19).
- A context value must be passed as a prop, or provided again inside the loaded
  application. A context shared by name is possible later for scalar values.
- The loaded application sees an error of the host, and the host an error of the
  loaded application, as a JavaScript error: message without Java type.
- Each lazy part carries its own copy of the Java runtime and of the JX classes it
  uses. In the fixture the loaded file is 809 KiB unminified next to a 1.9 MiB host;
  no size budget is claimed.
- The build wiring (two entry points, one esbuild run) is in the fixture's npm
  script. The Maven plugin does not produce lazy chunks yet.
- The lazy-route conformance mode (R-26) now builds on this boundary and awaits
  acceptance as described above. Automatic bundle splitting for
  `ComponentType.lazy` and production chunk-size budgets remain separate work.
