# R-28-04b2 nullable and sparse class checkpoint contract

26 November 2026. Work starts from the qualified bounded owner-attempt journal
`6acd612` (production `4bd62af`). This document states acceptance criteria before
implementation. Only the RED results explicitly recorded below have executed.

The existing explicit `StateCodec<S>` remains the serialization boundary. The
framework does not infer a serializer from arbitrary objects or encode Java
identity. A nullable descriptor may decode Java null; every non-null state must
still satisfy its exact `ClassSchema<S>`. Optional fields retain
`Presence.Absent`, `Presence.Undefined`, `Presence.Present(null)` and present
values. An empty object produced by React's empty partial merge differs from
null aggregate state. Required state fields and props remain strict.

The vertical consumer must prove actual independent TeaVM generations for null,
an empty record, a sparse record with an absent field, own undefined, explicit
null and a present string. React still owns merging, derived state and SCU. Both
ordinary own callbacks and the bounded connected props-only skipped commit path
must preserve the selected state without forcing an extra render. Pure and hidden
state requirements remain separate and their existing restrictions stay intact.

RED first: current descriptor guards reject this explicit nullable/sparse codec.
After implementation, malformed codec input and an invalid decoded state must
fail observably rather than silently reset. Tests must distinguish a candidate
whose decoder throws from one whose decoder returns a schema-invalid record,
and prove a subsequent corrected generation can restore the retained checkpoint.

## Candidate preflight is separate

The current development loader orders `capture`, `dispose`, `restore`, then the
candidate entry point. Consequently it invokes the new class decoder after the
old tree is disposed. Validating a roundtrip with the old codec would not prove
that a changed candidate decoder with the same schema accepts the payload.

This slice does **not** claim invalid candidate decode is rejected before
teardown. A separate checkpoint-preflight task must provide a declared typed
candidate codec registry/export and validate incoming payloads before old
`dispose`, without running candidate `main`, rendering a hidden duplicate tree,
or importing server secrets. It needs its own API decision, initializer/DOM
isolation proof and malformed/new-decoder regression. Readiness failure and
retained-checkpoint recovery are useful here but are not equivalent to that
preflight guarantee.

## Production change plan

There is no new implicit serializer and no second update queue. The existing
`withStateCheckpoint(signature, StateCodec<S>)` already declares the typed codec;
the implementation extends its qualified state shapes after the recorded RED:

1. Remove only the constructor's blanket nullable/optional-state exclusion after
   qualifying those shapes. Retain Pure rejection, exact schema ownership,
   metadata bounds and every connected/pending/candidate journal admission rule.
   A plain descriptor remains strict about null; `withNullableState` is required.
   Both modifier orders must preserve all other descriptor options.
2. Pass the complete typed class descriptor into `ClassCheckpoints.restore`.
   Decode once in the receiving TeaVM generation and call
   `ClassComponentType.validateState` on the result. Calling only
   `ClassSchema.validate` incorrectly rejects legitimate null aggregate state;
   skipping validation would admit invalid non-null records. No codec exception
   becomes an initial-state fallback.
3. Preserve the existing nullable-aware native snapshot decoder introduced by
   `4bd62af`. It distinguishes a null native state from a non-null object and uses
   schema presence readers for the latter. The journal still serializes only
   the selected committed snapshot and transfers strings, not Java peers.
4. Update the earlier admission regressions to retain the now-relevant negative
   boundaries: Pure checkpoints, strict null state and invalid metadata must
   reject without capture/disposal. Replace their intentionally superseded
   blanket nullable/optional assertions with the new positive vertical proof;
   do not simply delete the old browser safety check.
5. Document that a candidate decoder error currently rejects readiness after
   teardown. Keep the retained checkpoint for corrected-generation recovery;
   require the separate candidate registry/export before claiming pre-disposal
   validation. The browser tests deliberately perform a real replacement when
   observing this failure, rather than calling `main` as a speculative validator.

The test codecs use a closed bounded representation: `null`, `absent`,
`undefined`, `present-null`, and `value:` followed by a string. The separate
zero-field record uses its own signature/schema and `empty-record` payload.
These application-specific codecs are examples, not a new universal wire format.
Neither optional properties nor null aggregate state imply permission to copy
resources, callbacks or opaque references across generations.

## Planned qualification

- Narrow JVM descriptor tests: explicit codec, both modifier orders, exact
  nullable/Presence roundtrip, strict required fields and Pure rejection.
- Real browser RED for the existing admission rejection, then GREEN with the same
  consumer, independently compiled TeaVM module imports and post-restore edits.
- Candidate decoder throw and schema-invalid return: visible failure, no success
  reset, retained string checkpoint and corrected-generation recovery.
- Existing 34 runtime checks and metadata checks; no conformance interaction
  credit from coexistence and no full R-28-04 completion claim.

## Preparation and RED status

`ClassCheckpointPresenceTest` compiled and ran against the unchanged runtime:
both tests errored at the intended descriptor guards (`nullable aggregate state`
and `optional state fields ... note`). Maven exit1, two tests, 1.538 s; log
`/private/tmp/jx-class-checkpoint-presence-red-core.log`. This establishes the
current admission rejection, not browser behavior.

The five browser tests and `HotClassPresenceApplication` consumer are prepared.
Their first package attempt, using `-DskipTests` solely to build the intentionally
RED consumer, found a fixture overload mistake: the keyed class component call
needs the fourth ref argument. The source is corrected; compilation and browser
RED are pending the next coordinated window. That compile failure is not counted
as a behavioral reproduction. No production fix has been applied.
