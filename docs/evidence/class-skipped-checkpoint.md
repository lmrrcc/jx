# R-28-04b public commit observation audit

26 November 2026. Public-API design followed by a bounded local implementation and
real browser RED/GREEN on React 19.3.1. Source `4bd62af` qualifies connected,
owner-driven props-only updates whose explicit SCU decision skips rendering.
Hidden own updates, Pure, nullable/sparse codecs, delayed incoming owners and
complete discarded-work handling remain open. Mutation, full integration gate
and Verify are pending.

## Starting evidence and the unresolved distinction

`ClassCheckpoints` records render/SCU candidates, pending native updates and a
string published only by own class commit callbacks. The stable functional owner
uses an insertion effect for actual lifetime, and private Context carries exact
root membership. `HotReload.capture` refuses unresolved candidates, pending
updates, disconnected owners and unclaimed incoming rows before disposal.

The actual tests already establish:

- Visible SCU-false setState is supported through its own native callback.
- Hidden SCU-false setState defers that callback. The native control independently
  records the behavior; revealing reconnects lifecycle callbacks.
- Before this change, a props-only derived update with SCU false rejected capture.
- A suspended class candidate followed by an urgent retained descendant commit
  must not publish speculative state.
- Deleting an owner while hidden removes it permanently; portal and external-root
  membership use logical ownership, not DOM location.

React documents Activity as preserving state while destroying effects when hidden,
with lower-priority background rendering. A layout/passive/ref callback therefore
cannot be assumed to observe every hidden class update.
[Activity reference](https://react.dev/reference/react/Activity)

The installed native implementation corroborates the existing control: hidden
layout traversal suppresses class callbacks and inner Profiler callbacks; deferred
class callbacks run on reappearance. Those source observations guide tests only:
the proposed runtime must not read Fiber or depend on these private functions.

## Mechanisms that do not prove the required state

- A root/ancestor Profiler callback proves a commit somewhere in that subtree.
  Its documented callback carries timing and tree identity, not a class's committed
  state or update identity. An urgent retained descendant can trigger it while
  an ancestor's abandoned candidate remains recorded. Reading live instance.state
  there would conflate speculative assignments with the committed tree.
  [Profiler reference](https://react.dev/reference/react/Profiler)
- A should-update/render callback runs during evaluation and may be discarded.
  Returning false also does not stop independent descendant updates.
  [Component reference](https://react.dev/reference/react/Component#shouldcomponentupdate)
- A ref does not fire for each state commit and disconnects under Activity.
  A newly scheduled no-op/force update changes scheduling and is not observation.
- Encoding a queued updater result or replaying queued application logic in another
  owner would duplicate React's queue and still not prove acceptance of the work.
- An insertion effect has a commit-phase opportunity, but it must belong to the
  exact owner render whose class input was accepted. An arbitrary nearby effect
  is insufficient. It cannot depend on DOM/ref attachment ordering or update
  React state.
  [Insertion-effect caveats](https://react.dev/reference/react/useInsertionEffect#caveats)

No public per-update commit notification for **hidden own setState with SCU false**
has been demonstrated. That requirement stays open; this is not an approved
non-applicability decision or a proof that no solution can exist.

## Bounded implementation: owner-driven skipped updates

First qualify ordinary, already-connected classes receiving a props-only update
whose SCU returns false, with no pending queued native updates. This closes a real
current rejection without claiming hidden own-update support.

1. Give each functional owner render an opaque attempt token. Carry it through a
   reserved framework prop, for example `__jxCheckpointAttempt`, and close over
   it in that owner's commit effect. It is an in-generation identity, never a
   serialized value or a second application state owner.
2. Do not compare element.props identity. Native React copies class props while
   stripping ref and resolving defaults. The token survives ordinary own-prop
   copies. User schemas already reserve the `__jx` prefix.
3. Record candidate props/state/token and whether the explicit SCU decision
   skipped rendering. If render subsequently runs for that same candidate
   (for example context overrides the optimization), it is no longer eligible
   for this skipped-render promotion path.
4. The owner's commit effect may promote only its exact registered owner/journal,
   only when still connected, with zero pending native updates and exactly one
   eligible candidate carrying that exact committed attempt token.
5. Encode the captured candidate state through its typed schema and codec.
   Refactor the existing encoder to accept a native snapshot argument; do not
   read live instance.state from this new observer. Keep the codec pure and
   run it only once acceptance is established.
6. Clear only that exact candidate. Do not clear candidates from discarded owner
   attempts or reset pending counters. Preserve capacity, codec failure, root
   membership and incoming-owner admission rules.
7. Do not change the native class type, key, Java render result or update policy.
   Pure checkpoint opt-in is already rejected; the new changing private token
   must not silently enter a future Pure shallow comparison contract.

The native controls and adversarial cases below qualify this bounded mechanism.
An accepted owner effect must not publish an abandoned class input; equality of
user props or native state values is not an acceptable substitute for its token.

## Required RED then GREEN sequence

1. Strengthen the existing visible props-only skipped test: apply minimum=2 under
   SCU false, verify no Java render or own commit callback, retained ref/DOM, then
   require payload2 and successful independent-generation restoration to2.
   Current code must fail at capture. Include a native control whose derived
   state is2 while old DOM remains0.
2. Add an abandoned **props-driven** skipped candidate with no setState pending
   counter: transition to minimum99 while a sibling suspends under the same
   boundary; commit an urgent retained descendant update. Capture/replacement
   must still reject before disposal and preserve the current DOM. This
   discriminates exact attempt ownership from blanket ancestor-commit promotion.
3. Exercise class refs plus native defaultProps in the positive case so a
   props-object-identity shortcut fails. Count renders/callbacks to rule out
   forced or duplicate rendering.
4. Retain hidden own-update, deletion while hidden, incoming omission,
   disconnected/foreign-root, codec failure, schema reset and portal tests.
   Their rejection/ownership behavior must not weaken.
5. Run the prepared scoped hot mutation that clears every candidate after one
   owner attempt commits; the equal-user-props abandoned attempt must kill it.
   Keep this outside the conformance mutation catalog. This mutation is pending.

## Later hidden work, still unresolved

A render-output sentinel using an insertion effect may supply an exact captured
state for hidden **rendered** commits. It cannot observe a class that skips render,
and must prove no remount, identity changes, host wrapping, suspense promotion or
insertion-effect side effects. It is not the first implementation.

Hidden own-update callbacks and pending-count retirement, initial hidden incoming
owners, Pure/nullable/sparse codecs, and cleanup of superseded discarded candidates
need separate positive and adversarial controls. Preserving the current visible
rejection is safer than accepting a stale payload, but rejection alone cannot
mark R-28-04 complete.

## Local RED/GREEN evidence

Worktree `/private/tmp/jx-class-skipped-checkpoint` starts from integrated
`4dcaf41`. The audit is committed in `c8f313b`; buildable regression sources
are committed in `1a071dc`, retaining the old production journal for a genuine
RED run. They add the native default/ref control, an abandoned props-only
candidate plus retained descendant commit, and two same-user-props attempts
where only the later owner commits.

The production change was applied only after the regression failed behaviorally.
The positive case first proved the native control has committed state2, DOM0,
the same peer and no render/update callback. Old JX then returned
`Class checkpoint capture unqualified ... unmatched render candidates` instead
of payload2. Both discarded-work controls passed. The assertion reports the
actual rejection rather than dereferencing a missing snapshot.

| Check | Actual result |
| --- | --- |
| Old production reactor package, actual TeaVM compilation | PASS; 82 core, 17 React and 7 Elm JVM checks; 6.519 s |
| Old production, three owner-attempt browser tests | 1 failed / 2 passed; expected capture rejection; 2.3 s |
| Fixed source `4bd62af` reactor package, actual TeaVM compilation | PASS; same 106 JVM checks; 4.620 s |
| Fixed source, same three owner-attempt browser tests | 3 passed; 2.0 s |
| Complete `hot-class.spec.js` | 18 passed; 10.8 s |
| All other runtime-browser specs | 16 passed; 6.0 s |

The two disjoint browser groups cover the complete 34-test runtime suite on Chrome.
They include hidden pending-update rejection, deletion while hidden, abandoned
props and state attempts, same-user-props attempts, independent-generation
restoration, defaults/ref copies, codec failure, signature changes, missing
incoming owners, actual runtime attestation, foreign roots and logical portals.
No conformance result table or interaction credit changes in this slice.

The package command was:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl tests/runtime-browser -am package
npm run build:runtime
```

Browser checks used `RUNTIME_PORT=59192` with the configured
`tests/runtime-browser/playwright.config.js`. The focused selection was
`exact committed owner attempt|abandoned props-only skipped attempt|equal user props`.
The remaining group consists of `runtime.spec.js`, `hot.spec.js`,
`scalar-boundary.spec.js` and `promise-observer.spec.js`; no hot-class test was
silently omitted or counted twice in the 34-test total. The first sandbox launch
encountered a localhost proxy 502 before any browser test ran; the actual RED/GREEN
and regression executions used the normal local server outside that sandbox.

Local build logs are `/private/tmp/jx-class-skipped-checkpoint-red-build.log` and
`/private/tmp/jx-class-skipped-checkpoint-green-build.log`. Browser artifacts use
the corresponding `red-results`, `green-results`, `hot-results` and
`runtime-results` directories under `/private/tmp/jx-class-skipped-checkpoint-`.
These are local artifacts, not permanent CI evidence or performance measurements.

The prepared `class-owner-unmatched-cleared` hot mutation replaces exactly
`return value !== accepted;` with `return false;`. It must fail the same-user-props
discarded-attempt test after a baseline pass. It is unexecuted and outside the
conformance mutation catalog. Broader integration, push gate, Verify and
publication remain pending; R-28-04 is partial.
