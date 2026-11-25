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
runs seven Node checks, and runs four Chrome checks (two against ordinary React
and two against JX). The loopback browser host defaults to port **59182**;
`RSC_PORT` can override it. It does not accept remote function calls.

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

The original server smoke failed because `React` eagerly initialized a client
error-boundary class absent from server-conditioned React. The adapter now
initializes that class only when a boundary is composed. Ordinary client behavior
remains covered by the existing browser gate.

This is R-30-01 evidence, **not complete RSC support**. The two client references
and props codecs are fixture-owned; general manifest generation, directive-based
Java module separation, typed server functions, authorization, request-scoped
`cache`/`cacheSignal`, version-skew behavior and production transport/error
redaction remain open. Development Flight can contain server diagnostic metadata;
do not deploy this proof as a production endpoint. These requests carry public
fixture values, so the isolation check is not authenticated G0 isolation evidence.

React's protocol integration is pinned to the exact runtime version. See
[ADR 0007](../../docs/decisions/0007-react-server-components.md) for the build
choice and remaining slices. These dedicated proof results are separate from
the differential capability ledger: they do not automatically mark its RSC rows
supported or add interaction-pair credit.
