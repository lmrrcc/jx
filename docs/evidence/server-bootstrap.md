# Request-time bootstrap and CSP options

Status: **implemented locally; publication and complete option qualification pending**.
Source `8682f3d`, based on the synchronous sink-delivery slice `7636fae`.

`ReactServer.StreamOptions` accepts trusted inline bootstrap source and a typed
nonce union. `withNonce(String)` retains React's string option;
`withNonces(new ReactServer.Nonces(script, style))` retains the separate object
option. Null fields are omitted, empty strings preserved, and both-null object
nonces rejected. The original four-argument constructor remains available.
The record's canonical shape, equality and printed representation have expanded;
record patterns and consumers of that shape must be updated before GA.

The HTTP host owns fresh unpredictable per-response nonce generation and the
matching CSP header. A managed `<style precedence="...">` must receive the same
style nonce in its element props. `ReactStatic` rejects nonce-bearing options
for all four prerender entry points before entering the native runtime: reusable
static output must not retain per-request CSP authority. A request-time resume
can pass a fresh nonce through `ReactServer`; resume nonce behavior is not
qualified by this case.

Inline bootstrap content is executable, framework-owned source. React's script
terminator escaping does not sanitize code or serialize untrusted application
data. Do not concatenate request data into it. Ordinary JX application logic
remains Java; this is an explicit bootstrap/interop boundary.

## Evidence

- Maven package: **40 core, 2 React options, 48 compiler and 15 Maven-plugin
  tests passed** in 31.272 seconds. New JVM tests exercise defensive copies,
  option preservation, legacy construction, string/object distinction and
  rejection by all four static entry points.
- Differential case `server-bootstrap`: **2 passed, 6 mode cells inapplicable**
  in 5.5 seconds. Real Node renderers produce HTML served with CSP in real Chrome.
  This is not a bench-root hydration or lazy-route case.
- Exact JX/native React HTML matches for pipeable and readable rendering with
  absent, string, style-only and separate nonces, and both prerender variants
  without nonces. Concurrent outputs with distinct fixture tokens stay separate.
  Classic/module execution and computed CSS succeed with matching CSP; all
  execution and the style are denied with another response's nonce.
- An inline string containing closing/opening script tags round-trips without
  introducing an executable second script. Three actual bootstrap script elements
  retain the response's script nonce.
- Related SSR regression run: **36 passed, 108 skipped**, including terminal sink
  failure, streaming Suspense, fatal readiness, prerender and resume.
- Three scoped mutants are registered but **not yet run**. Full bench, local
  publication gate and Verify remain pending. No interaction-pair credit is added.

Logs: `/private/tmp/jx-server-bootstrap-build.log`,
`/private/tmp/jx-server-bootstrap-focused.log`, and
`/private/tmp/jx-server-bootstrap-ssr.log`. The first package attempt was blocked
by sandbox socket permissions; the successful run used the permitted local-server
environment. Initial focused runs exposed a wrapper representation error and
the upstream behavior below before the final passing run.

## Observed upstream limitation

The pinned React runtime serializes an **object** nonce as `[object Object]` on
the classic script's preload link. The actual script element and the module
preload use the correct script nonce. Under the fixture's nonce-only policy,
Chrome blocks that classic preload and reports one CSP error; the actual script
and module execute successfully. Both native React and JX must exhibit this
exact behavior. The test allows exactly that report and rejects other console
or page errors. Converting a string nonce into the object form changes output.

This defect remains visible; the slice neither patches React nor relaxes the
browser policy. Streaming continuation scripts, resumed output, import maps,
header hints, form-state transport, production bundles and additional browsers
need their own evidence. This is not blanket CSP or SSR qualification.

Primary contracts: [React pipeable rendering](https://react.dev/reference/react-dom/server/renderToPipeableStream),
[readable rendering](https://react.dev/reference/react-dom/server/renderToReadableStream),
and [static prerender's nonce restriction](https://react.dev/reference/react-dom/static/prerender).
