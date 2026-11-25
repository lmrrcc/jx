# R-28-04a conservative class commit journal

25 November 2026. This is a focused foundation, **not complete class hot reload**.
Source commit: `a371b25` on preserved integration base `e63ea76`.
The source-review corrections described below are prepared after `6b7ab4f`;
their regression executions are pending and are not part of the earlier 18/18 result.
The full R-28-04 requirement still includes hidden commits, skipped renders,
discarded work, incoming ownership, all class options and integration qualification.

## Contract

An ordinary native class descriptor can opt in with
`withStateCheckpoint("counter:model:1", codec)`. The compiling consumer is
[HotClassApplication](../../tests/runtime-browser/src/main/java/jx/fixture/HotClassApplication.java).
Its codec creates a fresh Java record from a string; no Java peer, callback,
resource or native instance crosses a TeaVM generation. Initial constructor work
runs again, and decoded state is installed before native derivation/render.
Ordinary instance fields, refs, effects and pending callbacks are not serialized.

Only this class's initial native mount, native update lifecycle and native queued
update callbacks publish an encoded payload. Activity/StrictMode reconnection
does not by itself promote state. A render/should-update candidate is evidence of
uncertainty, never evidence of commit. The journal retains bounded candidate
identities and pending-update counts, clearing only matching candidates after an
own native commit. Capture rejects any unresolved work instead of returning the
last payload as though it were current.

A stable functional owner uses an insertion effect for owner lifetime only;
React still renders the actual class and evaluates its original update policy.
The owner survives Activity layout disconnection and is removed on actual
deletion, including deletion while hidden. Promotion and cleanup require the
exact registered owner token, preventing old-generation work from affecting a
replacement owner. No private Fiber fields or duplicate state scheduler are used.

Root membership is an actual registered token passed through a private native
React context, not a path prefix or DOM position. Portals inherit that context.
A separately mounted native React root has no JX token: its exported Java class
still renders and updates, but capture rejects the foreign owner before disposal,
even when exactly one other JX root exists. Unmounting that foreign owner removes
the rejection. This correction and its portal counterpart await execution.

The first slice requires one JX-owned root, connected ordinary classes and an
initial restored tree whose class owners all commit. Pure is explicitly rejected
by the opt-in API. Hidden/disconnected owners, queued hidden updates, props-only
derivation with SCU false, unresolved discarded candidates, missing/delayed initial
owners and codec failures prevent replacement or readiness. An unresolved
candidate can conservatively prevent further replacement until its owner is
removed. These restrictions remain work to complete, not accepted parity limits.

Unclaimed incoming class payloads fail initial readiness. Restore admission closes
at that first root commit, so a later fresh insertion cannot resurrect an obsolete
matching path. This deliberately does not solve delayed hidden restoration or
removal before initial mount. It refuses those operations visibly.

## Bounds and compatibility

Each owner retains at most 16 unmatched candidates; more permanently disqualify
its journal. More than 256 pending updates also disqualify capture. Checkpoints
retain the existing aggregate eight-MiB limit and 4,096 component-owner limit,
with at most one MiB per class payload, 256-character signature/schema and
8,192-character owner path. Malformed class entries and duplicates are validated
before incoming maps change. Only encoded strings appear in captured class rows.

The in-memory runtime protocol is now 2. Portable checkpoint JSON stays version 1
with an optional validated `classes` array. A generated development bootstrap
uses the shared protocol guard at module evaluation, before application startup.
The corrected bootstrap reads a single validated `protocolN` export name on the
actual TeaVM-compiled `HotRuntime` class, not a plugin constant. This name and the
Java runtime's check derive from the same version constant. It never invokes the
exported wrapper, which TeaVM would otherwise use to initialize the application
entry class. The probe neither enables hot replacement nor initializes React or
application state. A missing `JxHotRuntime` named export fails bundling before
publishing assets. Runtime reachability supplies the export automatically; no
entry-point annotation is required. Production still needs only `main`. See the
[migration instructions](../maven.md#development-runtime-attestation).
The actual development loader rejects an old candidate without matching protocol
metadata before calling capture/dispose. Changing runtime protocols requires a
fresh development page; silently reusing the old version-1 maps is unsupported.

## Focused evidence

The following results qualify source `a371b25`, before the source-review corrections.

- Targeted Maven packages pass 51 core tests (four new descriptor contracts), two
  React tests, seven Elm tests, 52 compiler tests and 15 Maven-plugin tests.
- The counter's real `package -Djx.hotReload=true` execution copies and bundles the
  shared protocol guard and exports `hotRuntimeVersion` in its generated entry.
- Twelve new Chrome probes and six existing independent-generation hot probes
  pass: **18/18**. The late-owner rejection additionally asserts its exact runtime
  failure, not only absent DOM.
- A fresh imported `main` function proves each successful replacement uses another
  TeaVM module. The new class payload restores counts 2 and 1 respectively for a
  normally rendered class and a visible class whose render was skipped.
- Hidden skipped updates and a suspended class candidate followed by an urgent
  retained descendant commit reject capture. The original DOM nodes survive
  rejected replacement. Candidate state 99 is never advertised as committed.
- Hidden deletion clears the owner; replacement and reinsertion start at zero.
  A schema change deliberately resets state. Codec failure preserves the running
  application. Malformed/oversized input fails without changing incoming state.
- An actual native React control matches the hidden callback observation: in the
  tested StrictMode/Activity/SCU-false sequence, reveal reports state 1 through
  mount callbacks but the queued callback is absent on both React and JX. This
  is an observed pinned-runtime sequence, not a general callback guarantee.

The tests are
[hot-class.spec.js](../../tests/runtime-browser/tests/hot-class.spec.js) and
[hot.spec.js](../../tests/runtime-browser/tests/hot.spec.js). They run with:

```sh
env RUNTIME_PORT=59190 node_modules/.bin/playwright test \
  -c tests/runtime-browser/playwright.config.js \
  tests/runtime-browser/tests/hot-class.spec.js \
  tests/runtime-browser/tests/hot.spec.js --workers=1
```

No full runtime/conformance matrix, mutation campaign, push gate, Verify or new
interaction-pair credit is claimed. This branch starts at integration `e63ea76`;
later nextContext/Pure modifiers must preserve checkpoint metadata when integrated.

### Prepared review regressions — execution pending

- A JVM call to `HotRuntime.version()` must work without browser-native startup.
- The shared marker reader must accept exactly one version export, tolerate other
  TeaVM metadata and reject missing/ambiguous/accessor markers without invoking them.
- A fresh TeaVM candidate is imported without calling `main`; an observed static
  initializer on the entry class must not run during preflight.
- The real Maven development loop compiles the actual runtime sources in an
  isolated copy of Counter, with no shared JAR replacement or artifact install.
  Changing its protocol constant from 2 to 1 produces a real incompatible runtime;
  loading it must reject before capture/dispose while the old node and counter
  remain live. Restoring protocol 2 allows state-preserving replacement.
- The same session then compiles a main-only module without JX runtime reachability.
  Its missing named export must fail bundling, leaving the published bundle byte
  identical and the old counter live. Restoring the entry point recovers normally.
- An exported checkpointed class in a separate native root must reject capture and
  replacement before teardown. Both roots retain their actual DOM nodes and continue
  incrementing; removing the foreign root leaves only the owned class checkpoint.
- A class in a portal outside the JX root's DOM container must retain logical root
  ownership and restore its encoded counter in an independent TeaVM generation.

These additional probes have not yet run; the exclusive mutation campaign in a
different worktree must finish before compilation or browser verification.

## Remaining design requirement

React's public class callbacks do not presently provide the complete signal this
feature needs. Hidden updates can commit while class callbacks are deferred;
SCU false can prevent a render-result observer from running. A root/Profiler
observer reading a live class instance does not prove that the instance's current
candidate committed. A complete solution needs a demonstrated ownership and
commit correlation mechanism; forcing renders or returning stale payloads is not
a substitute. The existing functional Activity checkpoint defect is separate and
is not repaired by this class-owner wrapper.

The insertion-effect lifetime mechanism is based on the pinned React 19.3.1
implementation and requires explicit upgrade qualification. The public hook is
intended for CSS library integration and forbids state updates and ref/DOM timing
assumptions; this owner performs only bookkeeping. See
[useInsertionEffect](https://react.dev/reference/react/useInsertionEffect),
[Activity](https://react.dev/reference/react/Activity) and
[Component](https://react.dev/reference/react/Component).
