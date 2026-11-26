# R-28-04a conservative class commit journal

25 November 2026. This is a focused foundation, **not complete class hot reload**.
Source commit: `a371b25` on preserved integration base `e63ea76`.
Source-review corrections are committed as `a3e4e26` after `6b7ab4f`.
Their qualification below is separate from the original 18/18 result.
The full R-28-04 requirement still includes hidden commits, skipped renders,
discarded work, incoming ownership, all class options and integration qualification.

## Publication preparation, 26 November

The prepared publication at `0782e20` combines the original journal, explicit
admission, the [bounded skipped-commit correction](class-skipped-checkpoint.md)
and [nullable/sparse codecs](class-checkpoint-presence.md). It also restores the
typed-default input/clone adapters deliberately deferred from the preceding
default-props publication. The later sections below retain their original
qualification dates; the two linked follow-ups supersede only their stated
skipped-commit and nullable-state restrictions.

The assembled source passes **74 core and 11 React JVM checks**. Its
`ClassCheckpoints.java` matches the qualified nullable-codec source `7a4fafc`;
this is source comparison, not browser qualification of the new integration.
Current browser/package qualification, the final push gate and Verify remain
pending. The [three earlier journal mutations](class-journal-mutations.md)
remain scoped to their recorded source and assertions. A fourth planned mutant
replaces a successfully decoded null checkpoint with the constructor state;
it has not run and earns no mutation credit.

Pure checkpoints, hidden commits, complete incoming ownership and pre-teardown
candidate-codec validation remain separate requirements. This publication does
not import the later protocol-4 preflight or creator-ownership work.

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
the rejection. The focused ownership/portal tests and full runtime suite below
exercise both outcomes without suppressing browser console errors.

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

That original run did not include the full runtime/conformance matrix, mutation
campaign, push gate, Verify or new interaction-pair credit. The corrected source's
later qualification follows below. This branch starts at integration `e63ea76`;
later nextContext/Pure modifiers must preserve checkpoint metadata when integrated.

### Review corrections — local verification passed

- The scoped package passes **51 core, 3 React, 7 Elm, 52 compiler and 15 Maven
  tests**, including actual TeaVM output. The added JVM call to
  `HotRuntime.version()` works without browser-native startup. The first sandboxed
  attempt could not bind the Maven tests' local HTTP sockets; repeating the same
  package outside that sandbox passed without source changes.
- The shared marker reader accepts exactly one version export, tolerates other
  TeaVM metadata and rejects missing/ambiguous/accessor markers without invoking them.
- A fresh TeaVM candidate is imported without calling `main`; an observed static
  initializer on the entry class does not run during preflight.
- The real Maven development loop compiles the actual runtime sources in an
  isolated copy of Counter, with no shared JAR replacement or artifact install.
  Changing its protocol constant from 2 to 1 produces a real incompatible runtime;
  loading it rejects before capture/dispose while the old node and counter remain
  live. Restoring protocol 2 allows state-preserving replacement.
- The same session then compiles a main-only module without JX runtime reachability.
  Its missing named export fails bundling, leaving the published bundle byte
  identical and the old counter live. Restoring the entry point recovers normally.
- An exported checkpointed class in a separate native root rejects capture and
  replacement before teardown. Both roots retain their actual DOM nodes and continue
  incrementing; removing the foreign root leaves only the owned class checkpoint.
- A class in a portal outside the JX root's DOM container retains logical root
  ownership and restores its encoded counter in an independent TeaVM generation.

The three focused Chrome checks pass, then the **complete runtime suite passes
31/31**; those three are included in that total. The real hot workflow passes
**4/4 checks**: the marker parser plus three Chrome journeys (Counter, compiled
protocol drift/missing export, and Todo). The parent workflow uses an isolated
physical Maven repository, and the protocol mutation remains inside its temporary
source copy. No shared runtime artifact is replaced. Defaults for the normal
gate are unchanged; dedicated ports reproduce the local runs:

```sh
env RUNTIME_PORT=59190 node_modules/.bin/playwright test \
  -c tests/runtime-browser/playwright.config.js --workers=1
env HOT_COUNTER_PORT=59192 HOT_TODO_PORT=59189 npm run test:hot
```

The complete conformance run on clean `45ca7af` (production source `a3e4e26`)
then passes **905 checks, with 175 mode skips across 135 cases**. Client is
133/133 reference and 132/132 JX; hydration is 101/101 per side, static HTML
107/107 and lazy routes 112/112. Both result tables match their checked-in
baselines without regeneration. The pair ledger remains **54/6,328**, with
6,274 active candidate pairs still lacking complete witnesses. This verifies the
preserved `e63ea76` integration base plus the journal; it is not a claim that the
later class slices on main are already integrated into this branch.

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository \
  -pl tests/conformance -am package
env BENCH_PORT=59191 npm run test:bench
```

The package and full comparison logs are
`/private/tmp/jx-class-journal-conformance-package.log` and
`/private/tmp/jx-class-journal-conformance.log`. That run did not qualify scoped
journal mutations, publication gates, Verify or complete R-28-04 support. The
later scoped campaign is recorded below; no new interaction credit is claimed.

### Scoped mutation checks

The original two single-target source patches were prepared separately from the
conformance-only `MutateBench` catalog. Both are now killed after passing baselines
on integrated source `5b2b024`, together with the exact-owner unmatched-candidate
mutation: **three killed, zero survived/unusable**. See the
[direct failures and reproduction record](class-journal-mutations.md).
Run them only in a clean scratch copy with its
own physical Maven repository and an exclusive test window, after the same focused
commands pass unmutated. A build/infrastructure failure is not a behavioral kill.

| Mutation | Actual subject and required regression |
| --- | --- |
| `class-root-membership-lost` | Remove the registered-root identity guard from capture. The external-root browser case must detect accepted foreign ownership; the portal control must still pass. |
| `compiled-runtime-attestation-lost` | Replace the generated Maven bootstrap's compiled runtime marker with constant 2. The real protocol-drift development journey must detect capture/dispose or destruction of the old application before rejection. |

The first runs the external-root and portal cases in the runtime browser suite.
The second runs `compiled runtime drift` in the real Maven hot workflow; the
mutated Maven plugin must be installed only in the scratch repository. These
probes do not belong in conformance mutation totals.

### Nullable/sparse integration audit — required before composition

The separate R-28-03h1 source `bf3dfa4` permits null aggregate state and explicit
`Presence.Absent`, own `Presence.Undefined`, and `Presence.Present` values. It is
not integrated into this journal branch. Its state bridge preserves those
distinctions; it does not supply a portable checkpoint codec.

Combining the current modifiers without another contract would be unsafe:
`ClassCheckpoints.restore` validates decoded state through `ClassSchema.validate`,
which rejects null aggregates. A codec could therefore capture null successfully
and fail restoration only after teardown. Optional fields also need codecs whose
qualification distinguishes absence, own undefined, defined null and values;
default values or ordinary JSON omission must not silently collapse them.

The conservative integration boundary is to reject checkpoint descriptors with
nullable aggregate state or optional state fields, in either modifier order,
before a running application can request replacement. The combined-branch
implementation and its separate evidence follow below.
Future support must prove cross-generation round trips for all these states and
retain the existing own-commit proof. This is pending work, not an accepted
exclusion or a reason to mark R-28-04 complete.

### Combined nullable/journal admission — source `c205ca2`

The integration branch starts from qualified server/class candidate `f8565e6`,
then combines nullable source `50fde0a`/`1ff6ac6` and the journal source and review
corrections above. All immutable modifiers retain pending-context policies,
nullable-state policy and checkpoint metadata; the renderer retains nullable
decoding for constructor, derivation, previous-state and snapshot callbacks.

The central descriptor constructor rejects a checkpoint when aggregate state is
nullable or any state field is optional. Both modifier orders fail synchronously,
without constructing a peer or executing its state codec. Required fields with
nullable values remain legal. Ordinary nullable/sparse classes without checkpoints
keep their native React behavior. No value is defaulted or synthesized.

This prevents unqualified live state from entering the capture protocol. It does
**not** preflight arbitrary initializers of a newly edited candidate: other invalid
candidate startup code can still fail after teardown. That limitation remains
separate from the actual compiled-runtime protocol preflight.

The two new JVM regressions first failed because no exception was thrown; the
browser regression independently observed `accepted` for the unsupported
configuration. After the guard, the full package passes **69 core, 11 React,
7 Elm, 54 compiler and 15 Maven tests**, including actual TeaVM compilation.
All **32 runtime Chrome checks** pass, including the same admission regression;
the old root and actual DOM node remain live, increment again and produce the
correct committed checkpoint after all three rejected configuration attempts.
The initial guarded run caught a missing favicon in the hot test page; adding
the same data favicon used by the ordinary runtime page fixed the request,
without filtering console errors. The class comparison then passes **96/96**
checks: twelve cases across all eight React/JX projects.

Logs: `/private/tmp/jx-checkpoint-admission-jvm-red.log`,
`/private/tmp/jx-checkpoint-admission-browser-red.log`,
`/private/tmp/jx-checkpoint-admission-package-green.log`,
`/private/tmp/jx-checkpoint-admission-runtime-final.log`, and
`/private/tmp/jx-checkpoint-admission-class-focused.log`.
The combined full conformance run then passes **953 checks, with 199 mode skips
across 144 cases**: client 142/142 reference and 141/141 JX; hydration 106/106,
static HTML 112/112 and lazy routes 117/117 per side. The result table adds only
the two nullable cases; all existing rows and the **62/6,328** pair witnesses stay
unchanged (6,266 active candidate pairs remain open). This qualifies the combined
`f8565e6` base and source `c205ca2`, not every later branch or main revision.
The log is `/private/tmp/jx-checkpoint-admission-full.log`.

Targeted hot mutations, nullable-state mutations and publication gate/Verify are
still pending. This admission guard closes neither nullable checkpoint support
nor the remaining R-28-04 commit-correlation requirements.

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
