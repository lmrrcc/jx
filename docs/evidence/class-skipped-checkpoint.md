# R-28-04b public commit observation audit

26 November 2026. Read-only design from the current journal and installed React
19.3.1; no new browser experiment or implementation is claimed. The existing
runtime tests remain the behavioral evidence. This proposal does not discharge
hidden, Pure, nullable/sparse, delayed incoming or discarded-work requirements.

## Current evidence and the unresolved distinction

`ClassCheckpoints` records render/SCU candidates, pending native updates and a
string published only by own class commit callbacks. The stable functional owner
uses an insertion effect for actual lifetime, and private Context carries exact
root membership. `HotReload.capture` refuses unresolved candidates, pending
updates, disconnected owners and unclaimed incoming rows before disposal.

The actual tests already establish:

- Visible SCU-false setState is supported through its own native callback.
- Hidden SCU-false setState defers that callback. The native control independently
  records the behavior; revealing reconnects lifecycle callbacks.
- A props-only derived update with SCU false currently rejects capture.
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

## Smallest bounded implementation candidate: owner-driven skipped updates

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

The exact owner effect/token mechanism is a candidate that needs native controls.
If an accepted owner effect can run while its exact class input remains abandoned,
the design must be rejected; equality of user props or native state values is not
an acceptable substitute.

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
5. Add a scoped hot test mutation that replaces token matching with the most
   recent eligible candidate; the abandoned props candidate must kill it.
   Keep this outside the conformance mutation catalog.

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

## Source preparation

Worktree `/private/tmp/jx-class-skipped-checkpoint` starts from integrated
`4dcaf41`. The audit is committed in `c8f313b`; buildable regression sources
are committed in `1a071dc`, retaining the old production journal for a genuine
RED run. They add the native default/ref control, an abandoned props-only
candidate plus retained descendant commit, and two same-user-props attempts
where only the later owner commits.

The proposed two-file production change is staged separately in
`/private/tmp/jx-class-skipped-checkpoint-fix/`; it has not been applied or
compiled. A source-only independent review found no concrete false-success
path. That review does not establish insertion-effect timing or any hidden-state
preservation. The targeted hot mutation is prepared outside the conformance
catalog. RED/GREEN, complete runtime regression and publication remain pending.
