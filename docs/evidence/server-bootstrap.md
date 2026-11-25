# Request-time bootstrap and CSP options

Status: **bootstrap slice merged in PR #66; resume publication and complete option qualification pending**.
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
can pass a fresh nonce through `ReactServer`; the separate resume case below
qualifies that behavior for the pinned Node/Chrome combination.

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
- Integrated source `0da2b25`, including class lifecycle authoring, responsive
  preloads, sink delivery and synchronous `act`: package **47 core, 4 React,
  52 compiler and 15 Maven-plugin tests** passes in 45.033 seconds. Full bench:
  **907 passed /181 skipped /136 cases** in 4.4 minutes; client 134 reference /133
  JX, hydrate 101, static 107 and lazy 112 per side. The 54/6328 pair register is
  unchanged. The bootstrap implementation and consumer files match `8682f3d`.
- All **three scoped bootstrap mutants were killed** by `server-bootstrap` after
  the unmutated scratch baseline passed. The joint campaign also qualified four
  class mutants: **7 killed, 0 survived, 0 unusable**, candidate `f49b48f`, source
  `193181f`. Its integrated package passes 56 core/4 React/54 compiler/15 plugin/
  10 language-server checks; the full bench passes **931/181/139 cases** with
  unchanged 54/6328 pair evidence. Bootstrap source and consumer files are
  byte-identical to this branch. This is a scoped campaign, not a new full-catalog
  result.
- Bootstrap PR [#66](https://github.com/lmrrcc/jx/pull/66) merged as `d683c4d`
  after the 15-step local push gate passed in 335 seconds and both
  [PR Verify](https://github.com/lmrrcc/jx/actions/runs/45696891784) and
  [push Verify](https://github.com/lmrrcc/jx/actions/runs/45696708415) passed on
  `224f6a1`. The final integrated bench passes **931/181/139 cases**:
  client 137 reference /136 JX, hydrate 104, static 110 and lazy 115 per side.
  Pair evidence remains 54/6328. The separate resume addition below still
  requires its own publication gate and Verify.

Logs: `/private/tmp/jx-server-bootstrap-build.log`,
`/private/tmp/jx-server-bootstrap-focused.log`, and
`/private/tmp/jx-server-bootstrap-ssr.log`. The first package attempt was blocked
by sandbox socket permissions; the successful run used the permitted local-server
environment. Initial focused runs exposed a wrapper representation error and
the upstream behavior below before the final passing run.
Integrated logs are `/private/tmp/jx-bootstrap-integrated-build.log` and
`/private/tmp/jx-bootstrap-integrated-bench.log`. Independent source review found
no blocking contract/security defect; the record-shape compatibility change and
bounded resume qualification below remain explicit.

The completed mutation log is `/private/tmp/jx-class-bootstrap-mutations.log`;
scratch `jx-mutation-2296619748002841958` is retained under the macOS temporary
directory. `/private/tmp/VerifyClassBootstrapSources.java` checked 27 class files
and 23 parent-capability files against their qualified branches before execution.

## Observed upstream limitation

The pinned React runtime serializes an **object** nonce as `[object Object]` on
the classic script's preload link. The actual script element and the module
preload use the correct script nonce. Under the fixture's nonce-only policy,
Chrome blocks that classic preload and reports one CSP error; the actual script
and module execute successfully. Both native React and JX must exhibit this
exact behavior. The test allows exactly that report and rejects other console
or page errors. Converting a string nonce into the object form changes output.

This defect remains visible; the slice neither patches React nor relaxes the
browser policy. Ordinary streaming continuations, import maps, header hints,
form-state transport, production bundles and additional browsers need their own
evidence. This is not blanket CSP or SSR qualification.

Primary contracts: [React pipeable rendering](https://react.dev/reference/react-dom/server/renderToPipeableStream),
[readable rendering](https://react.dev/reference/react-dom/server/renderToReadableStream),
and [static prerender's nonce restriction](https://react.dev/reference/react-dom/static/prerender).

## Request-time resume qualification

Source `7686627` adds a Java consumer and a native React reference for
`resume` and `resumeToPipeableStream`. It waits for an actual Suspense attempt
before aborting prerender, using a microtask observation barrier rather than a
timer. Trusted postponed state crosses a JSON round trip. Two concurrent renders
resume with different response nonces and independent data promises; there is
no shared mutable request slot.

Package passes **47 core, 4 React, 52 compiler and 15 Maven-plugin tests** in
22.377 seconds. Focused Chrome run: **2 passed /6 mode cells inapplicable** in
7.1 seconds. Exact native/JX shell and continuation HTML match. The continuation
does not repeat the shell or replace the original bootstrap configuration.
With the correct nonce, React's continuation instructions reveal the resolved
content and apply its deferred style. With another response's nonce, that
content stays hidden and the fallback remains; exactly the script and style CSP
violations are expected. The original bootstrap executes exactly once in both
cases. No production rendering code changed; its Java contract documentation
now clarifies that resume does not retroactively authorize static scripts.

The static prelude contains two known fixture scripts without nonces. The test
asserts their exact source strings before granting their CSP hashes; it does
not authorize arbitrary scripts found in untrusted HTML. React's documented
guidance is to omit prerender bootstrap scripts when using request-time nonces.
A nonce-only deployment cannot authorize already emitted static scripts merely
by adding a nonce to resume. This fixture uses a deliberately bounded hash-plus-
nonce policy; a general CSP host, external-script resume behavior, partial network
delivery and cross-browser qualification remain open.

The object nonce variant is evidenced against pinned React 19.3.1; the resume
reference pages describe the string variant. See [resume](https://react.dev/reference/react-dom/server/resume)
and [resumeToPipeableStream](https://react.dev/reference/react-dom/server/resumeToPipeableStream).
Logs: `/private/tmp/jx-resume-nonce-build.log` and
`/private/tmp/jx-resume-nonce-focused.log`.

The later shared server-option integration `eb536cd` passes **943 checks /217
inapplicable cells /145 cases**, including this case's two passing client cells
and six mode exclusions. The Java consumer, native reference and shared spec
are byte-identical to the current resume slice. Its recorded row is retained
from that actual run; the final publication gate will verify the narrower slice
on merged main. No new pair coverage or mutation qualification is attributed
to the resume case. Final publication gate and Verify remain pending.
