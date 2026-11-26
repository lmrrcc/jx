# R-28-04b2 nullable and sparse class checkpoint contract

26 November 2026. Implemented and locally qualified on production `6dd30ab`, with
the strengthened consumer in `1e589e7`, extending
the bounded owner-attempt journal `6acd612` (production `4bd62af`). The contract
below was recorded before implementation. The later publication assembly
`31bca85` passes 166 JVM and 39 runtime Chrome checks, with the scoped nullable
mutation killed as recorded below. The final push gate and Verify remain pending;
R-28-04 remains partial.

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

## Implemented production boundary

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

## Qualification scope

- Narrow JVM descriptor tests: explicit codec, both modifier orders, exact
  nullable/Presence roundtrip, strict required fields and Pure rejection.
- Real browser RED for the existing admission rejection, then GREEN with the same
  consumer, independently compiled TeaVM module imports and post-restore edits.
- Candidate decoder throw and schema-invalid return: visible failure, no success
  reset, retained string checkpoint and corrected-generation recovery.
- Existing 34 runtime checks and metadata checks; no conformance interaction
  credit from coexistence and no full R-28-04 completion claim.

## Actual RED/GREEN results

`ClassCheckpointPresenceTest` compiled and ran against the unchanged runtime:
both tests errored at the intended descriptor guards (`nullable aggregate state`
and `optional state fields ... note`). Maven exit1, two tests, 1.538 s; log
`/private/tmp/jx-class-checkpoint-presence-red-core.log`. This establishes the
current admission rejection, not browser behavior.

The first consumer package found a fixture overload mistake: the keyed class
component call needs the fourth ref argument. After correcting the fixture,
the old-production package passed with `-DskipTests` solely to obtain the
intentionally RED browser artifact. This compile correction is not counted as a
behavioral reproduction, and that package is not credited as a passing test run.

The actual browser RED then showed `presence-status` as
`rejected: Class checkpoints with nullable aggregate state are not qualified`
instead of `ready`. Production was changed only after this observable rejection.

| Check | Actual result |
| --- | --- |
| Original descriptor acceptance regression | 2 JVM tests errored at nullable/optional admission; 1.538 s |
| Original real browser consumer | 1 failed at the explicit nullable admission message; 5.2 s test time |
| Fixed focused JVM contracts | 8 passed; 1.604 s |
| Fixed reactor package and actual TeaVM compilation | 84 core + 17 React + 7 Elm tests passed; 5.002 s |
| New independent-generation browser consumer | 5 passed; 6.2 s |
| All preceding runtime-browser cases | 34 passed; 14.3 s |
| Strengthened constructor-vs-restored-state consumer | Same108 JVM checks/TeaVM passed in5.841 s; all5 new browser cases passed in4.0 s |

The two disjoint Chrome groups cover all 39 runtime tests. The new consumer
restores null aggregate state, a separate zero-field record, absent and
own-undefined fields, explicit null, and a Unicode string into independent TeaVM
modules. It verifies a new DOM node and generation for each replacement, then
edits the restored state. Every candidate Java constructor deliberately starts
from `value:constructor`, and its execution is observed before the saved value
replaces it. Thus null-to-null restoration cannot pass merely by silently
discarding the checkpoint and reusing the original initial null. Production was
unchanged for this strengthened final consumer run. Native SCU-false own callbacks and props-only derivation
also preserve state while the old DOM stays unchanged until replacement.

The pinned React runtime warns for `getDerivedStateFromProps` with initial null
state. The first run had all state/recovery assertions pass but correctly failed
its empty-console assertion. The final tests assert the exact warning text and
the expected count: initial mount plus null restoration in the roundtrip test,
initial mount only in the skipped-render test. Other errors are not filtered.

Three negative paths prove no success fallback: a changed candidate decoder
throws, a candidate returns a record with invalid field presence, and a declared
codec receives an unknown payload. Each rejects readiness with the specific
expected error, leaves no successful class output, and permits a corrected
generation to restore the retained string checkpoint and continue editing.
They explicitly verify that the old DOM has already been disposed in this
failure path. **Candidate decoder preflight before teardown is not implemented.**

The earlier admission test now verifies that Pure checkpoints, strict null
state and invalid metadata still reject without capture/dispose or replacing
the live DOM. The other 34 regressions retain hidden-update rejection, owner
correlation, incoming-owner readiness, portals, external roots, codec failures,
runtime attestation, hook/Elm state, scalar identities and Promise observers.

Commands used Maven Wrapper with the private `.cache/maven-repository`, then
`npm run build:runtime` and the configured runtime Playwright suite on
`RUNTIME_PORT=59192`. Logs are under
`/private/tmp/jx-class-checkpoint-presence-{red-core,red-build,green-core,green-build,final-build}.log`;
browser artifacts use the `red-results`, `green-results`, `runtime-results` and `final-results`
directories with the same prefix. These local durations are not performance
claims. No conformance table, feature denominator or interaction witness changes.

Production review of `ClassComponentType`, `ClassCheckpoints` and `ReactClasses`
found no concrete blocker. This does not extend the proof to hidden own updates,
Pure checkpointing, arbitrary codecs or candidate preflight. In the assembled
publication at `31bca85`, the separate `class-null-checkpoint-reset` campaign
passed its 39-check browser baseline, rebuilt the mutant successfully, and
observed the intended first independent-generation failure: `null` became
`value:constructor` at the existing assertion. The restored source rebuilt and
passed the same check. See [the exact campaign](class-journal-mutations.md#nullable-checkpoint-follow-up-26-november)
for the separate one-kill result, commands and limitations. This remains outside
the conformance catalog; final publication gate/Verify are pending.
