# Native legacy class lifecycles

R-28-03h2a. This is the explicit typed runtime slice for the three deprecated
lifecycle methods and their three `UNSAFE_` counterparts. Compiler declaration
sugar is R-28-03h2b.

## Selected publication integration, 26 November 2026

The final selected source `7ab6a2f` is rebased above consumer candidate `1551e80`.
The reactor package passes **182 JVM tests** (84 core, 11 React, 65 compiler,
22 Maven plugin) and compiles the TeaVM consumers in **35.405 seconds**. The
complete matrix passes **1,001 checks / 223 declared exclusions / 153 cases** in
**4.5 minutes**: client 151 reference / 150 JX, hydration 111, static 117 and lazy
122 per implementation. The recorded conformance table adds exactly the four
legacy cases below; all previous rows are unchanged. The interaction table is
unchanged at **62 / 6,328** pairs. The publication gate and remote Verify remain
pending. These local results do not count as a new mutation campaign.

The source, native reference and shared spec retain the previously qualified
selected runtime semantics. Compiler discovery and advanced hot protocols are
separate candidates.

### Earlier selected focused qualification

Sources `b0fbead`/`a4aaa64` select only the legacy runtime on accepted main
`515f2c2`. The existing nullable/sparse checkpoint admission and committed
journal are preserved; the historical blanket rejection is not reinstated.
No creator changes or hot protocols 4/5 are included.

The selected reactor package passes **169 JVM tests** (83 core, 11 React,
60 compiler, 15 Maven plugin) in 27.692 seconds. An additional integration
contract then passes with all **4 legacy JVM tests**, proving default props,
nullable state, codec identity and pending-null dispatch survive both orders of
legacy modifier composition without mutating the current peer. This is codec
admission/composition evidence, not a new browser checkpoint-restoration claim.

All four differential cases pass **26 Chrome checks / 6 declared mode exclusions**
in 48.0 seconds on port 59185. Initial static HTML, hydration and lazy mounting
remain explicit mode contracts. This earlier focused run left the full result
tables unchanged; the subsequent complete integration is recorded above.
Two runtime mutants were killed in the historical `cc929bb` campaigns; those
results are not a new mutation campaign on this selected integration.

Logs: `/private/tmp/jx-legacy-runtime-integrated-build.log` and
`/private/tmp/jx-legacy-journal-composition.log`. The focused Playwright reporter
printed the 26/6 result; it does not rewrite aggregate result tables for a partial
run. No unrelated capability was imported to make this slice compile.

## Public contract

`ClassComponent` exposes `componentWillMount()`,
`componentWillReceiveProps(P)` and `componentWillUpdate(P,S)`, plus each
`UNSAFE_` spelling. Each is selected independently by its `Lifecycle` flag.
The native type installs only declared methods, and extends actual
`React.Component` or `React.PureComponent`. React decides when to invoke them;
JX does not simulate lifecycle ordering or suppress native warnings.

The context-aware policies are immutable descriptor modifiers:

```java
descriptor.withContextWillReceiveProps(theme, LegacyVariant.UNSAFE,
    (peer, nextProps, nextContext) -> { /* typed pending input */ });
descriptor.withContextWillUpdate(theme, LegacyVariant.DEPRECATED,
    (peer, nextProps, nextState, nextContext) -> { /* typed pending input */ });
```

The requested `Context<T>` must be the exact descriptor context. Null arguments
fail at construction; a different context fails explicitly. The pending context
value itself may be null. A policy replaces only its selected spelling, leaving
the other spelling and existing context/Pure/derivation/checkpoint settings intact.
The peer's current props, state and context are not overwritten before dispatch.
Pending values pass through the declared schemas; callback failures propagate.
No unchecked cast, dynamic property map or private React structure is involved.

These callbacks run during rendering. React may retry or abandon their work;
they must not acquire external resources or be treated as commit notifications.
Mount/receive callbacks can enqueue native state updates; will-update callbacks
must not call `setState`. Native derived-props or snapshot APIs suppress legacy
callbacks. A derived-error callback alone does not suppress them. Supported
deprecated behavior remains distinct from React 19's removed legacy context API.

## Historical first qualification

The [Java consumer](../../tests/conformance/src/main/java/jx/conformance/cases/ClassLegacy.java),
[native React reference](../../tests/conformance/reference/cases/class-legacy.jsx)
and [shared spec](../../tests/conformance/specs/class-legacy.spec.js) exercise:

- `class-legacy`: deprecated then UNSAFE mount queues; pending versus current
  values; queued receive updates before Pure/SCU; skipped render with committed
  callbacks/current state; force updates; pending context bypass; retained DOM.
- `class-legacy-suppression`: derived props and snapshots each suppress all
  six callbacks while preserving committed lifecycle methods; derived errors
  alone leave legacy callbacks active. Exact expected development warnings remain
  observable, and any additional page/console error fails the browser case.
- `class-legacy-interrupted`: initially absent content mounts into Suspense,
  queues state and suspends. Abandoning it leaves the public ref detached and
  never commits its callbacks or unmount method, even after its gate resolves.
  A later keyed attempt commits its own fresh state and callbacks. Two bounded
  fixture-owned promises are settled by explicit events, without timing sleeps.
- `class-legacy-server`: actual Node `renderToString` processes both mount
  queues before HTML, preserves modern suppression, and invokes no committed
  lifecycle or update callbacks. Its three other modes are explicitly `n/a`;
  the browser cases separately cover hydration, static HTML and lazy mounting.

Three JVM tests check variant dispatch, metadata preservation, typed policy
precedence, schema/context guards, current-value retention and original exception
identity. They do not substitute for native scheduling tests.

The package passes **72 core / 11 React / 54 compiler / 15 Maven-plugin tests**
and compiles the actual TeaVM browser/server/lazy programs. The four new cases
pass **26 checks**, with **6 n/a** cells: three cases in all eight projects and
the server trace on both implementations. Static projects compare initial HTML
only. This is Chrome evidence, not browser-wide or IDE qualification.

Logs: `/private/tmp/jx-class-legacy-complete-build.log` (23.460 seconds) and
`/private/tmp/jx-class-legacy-complete-focused.log` (26.7 seconds). The first
package found a fixture call to a nonexistent React factory; it was corrected
to the existing `new Context<>(...)` API before qualification. No runtime
behavior or warning filter was changed to make the reference pass.

At the first qualification, two curated mutations were registered but unrun:
`class-legacy-unsafe-mount-aliased` and `class-legacy-next-context-stale`.
They target native callback selection and the pending context passed to user
code. No new pair witness or aggregate result-table credit is claimed yet.

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl tests/conformance -am package
npm run build:bench
npm run build:bench-server
npm run build:bench-lazy
npm run build:bench-production
BENCH_PORT=59191 node_modules/.bin/playwright test -c tests/conformance/playwright.config.js -g 'class-legacy'
```

## Remaining scope

Automatic `.jx` declaration discovery, typed next-context overloads and original
source diagnostics are a separate compiler slice. The explicit Java consumer
does not establish native IDE completion, production-mode warnings, all possible
concurrent interruption sequences or checkpoint compatibility for these methods.
Default props and general sparse/nullable authoring have separate acceptance.
No supported class option is silently marked not applicable.

The public [React Component reference](https://react.dev/reference/react/Component)
describes the supported legacy methods and their caveats. The pinned React 19.3.1
client and server implementations determine the exact dual-spelling order,
modern-method suppression and pending context used in the differential fixtures.
