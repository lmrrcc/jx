# React interaction evidence — R-27

24 November 2026. Parent: [implementation sequence](ga-tasks.md#r-27-implementation-slices).
R-27 is **partial**. R-27-01 implements the interaction register, executed assertion
reporting and two combined scenarios. Its complete local bench and scoped
mutation run pass; this change's Verify acceptance is pending. No human or
cross-browser qualification is implied by these local results.

## Current evidence and remaining scope

| Measure | Result |
| --- | --- |
| Currently supported/equivalent feature IDs | 104 |
| Candidate unordered pairs among those IDs | 5,356 |
| Pairs with a passing witness in every declared mode, on both React and JX | 19 |
| Current candidate pairs without complete evidence | 5,337 |
| Eventual application-feature IDs | 118 |
| Eventual candidate pairs | 6,903 |
| Additional pairs involving the 14 deferred feature IDs | 1,547 |

The candidate universe comes from the [feature ledger](evidence/conformance.tsv).
Only `react-dom:browser` and `framework:react-compiler`, whose task is `none`, are
outside the eventual application-feature universe. Alias rows remain distinct.
R-28 adds `PureComponent`; R-29 adds nine deferred rows and R-30 adds four.
`Component` already counts as an active feature, although its current error-boundary
equivalent does not implement Java-authored class components.

Unlisted pairs are uncovered, not implicitly inapplicable. A pair may lack a
meaningful interaction, but closing it that way requires a recorded, reviewed
reason. This initial slice grants no such exclusions and does not reduce the
all-features objective to selected feature families. A new active feature expands
the candidate count automatically. These counts describe candidate relationships,
not a percentage of GA completion or coverage of every option of either feature.

## What earns pair evidence

[`tests/conformance/interactions.tsv`](../tests/conformance/interactions.tsv)
declares `id`, `feature-a`, `feature-b`, `case`, `modes` and `behavior`. Each row is
a positive witness for one pair, with feature IDs in canonical order. Both features
link to the case in the feature ledger. Several assertions may prove different
behaviors of one pair; the summary counts the pair once.

The shared spec uses `interaction(id, async () => { ...assertions... })`, or an
explicit list of IDs when its assertions demonstrate several interactions. This
helper runs a named Playwright step and adds an `interaction` annotation only after
the callback succeeds. Listing features together in a component or ledger earns
no credit. The assertions must describe how the features affect one another.

The reporter credits a witness only when the annotation was emitted by the
declared case, in a declared mode, and the whole case passed on that attempt. A
later case failure removes credit. Unknown markers, wrong cases or modes, and
missing markers in an applicable executed case fail the run. Result annotations
are scoped to an attempt so a prior retry cannot supply a missing witness.

[`docs/evidence/interaction-results.tsv`](evidence/interaction-results.tsv) records
the result in each of the eight React/JX projects, alongside the ordinary
[case results](evidence/conformance-results.tsv). A pair has complete recorded
evidence when at least one declared witness passes on both sides in every mode
that witness claims. The ordinary feature matrix alone cannot establish that.

`CheckBaseline` validates registry structure, feature/case links, canonical pairs,
duplicate claims and mode/result consistency. Its printed number is **registered
pairs**, not proof that assertions ran. The browser reporter supplies execution
evidence. Missing cases, projects or witness results prevent a complete baseline
update; focused runs leave both recorded tables unchanged. Mutation runs skip
golden-file comparison but retain checks on the assertions they execute.

## Scenarios and mode boundaries

| Scenario | Interaction evidence |
| --- | --- |
| `combined-keyed-context` | Seven pairs: keys with state, controlled inputs and effects; memo with context and prop updates; context with state and effects. Reordering preserves row identity and drafts; removing/reinserting resets only that row; context changes reach memoized readers and clean up old effects. |
| `combined-activity-store` | Four pairs: memo with an external store; Activity with store subscriptions, state and effects. Hiding releases subscriptions/effects while retaining DOM and state; revealing reads the latest snapshot; unmounting resets local state. |
| Existing focused cases | Eight pairs: Strict Mode/effects, layout/passive effect order, lazy/Suspense, async/transition, hydration/store, hydration/IDs, portal/events and streaming/Suspense. Named assertion blocks now record the existing behavioral evidence. |

The combined scenarios run their assertions in client, hydrate and lazy modes.
Static mode renders both scenarios and compares HTML, but does not execute their
interactive assertion blocks and therefore earns no pair credit. None of the 19
current witnesses claims static interaction coverage.

The register explicitly limits lazy/Suspense and portal/events to client/lazy.
Hydration witnesses run inside the `hydrate-server` case in the **client projects**:
that case owns its server render and hydration itself. The streaming witness also
uses client projects but executes in Node, not a browser page. These project names
must not be mistaken for extra browser-mode coverage. Existing case exclusions
remain recorded in [`modes.tsv`](../tests/conformance/modes.tsv).

The streaming witness checks shell/fallback/delayed-content ordering in the
collected HTML. It does not measure early network delivery; that requires a
separate controlled streaming assertion in R-27-04.

## Local results and repeatable checks

The complete local run on 24 November reports **731 passed, 133 skipped**, across
108 case IDs. Its interaction summary is **19 / 5,356**, with **5,337** remaining.

| Mode | React reference | JX |
| --- | --- | --- |
| Client, including Node API cases | 106 / 106 | 105 / 105 |
| Hydration | 81 / 81 | 81 / 81 |
| Static HTML | 87 / 87 | 87 / 87 |
| Lazy route | 92 / 92 | 92 / 92 |

After the Maven build, run from the repository root:

```sh
java tools/CheckBaseline.java
npm run test:bench
```

The npm command includes reporter regression checks before building and running
the browser matrix. To intentionally regenerate both result tables, use
`BENCH_UPDATE=1 npm run test:bench` and review both diffs.

Two new curated mutants target the combined behavior: `combined-unstable-key`
breaks keyed row identity, and `combined-store-leak` leaves a subscription active
after cleanup. Run these exclusively, without another build or browser suite:

```sh
java tools/MutateBench.java --only combined-unstable-key,combined-store-leak
```

The unmutated scratch baseline passed. The scoped run killed **both mutants**,
with zero survivors and zero unusable mutants: `combined-unstable-key` was detected
by `combined-keyed-context`, and `combined-store-leak` by `combined-activity-store`.
The catalog now has 104 mutants; the recorded historical full campaign covers
100. R-26 separately killed its two new mutants. Neither scoped run establishes a
fresh full 104-mutant campaign.

## Follow-up and acceptance

R-27-02 extends identity/composition/DOM interactions, R-27-03 extends async/forms
and stores, and R-27-04 extends server/resource interactions. Each selects explicit
remaining pair IDs. R-27-05 follows R-28–R-30, exercises the deferred features and
reviews the remaining applicability decisions. The parent R-27 stays open until
every eventual application-feature pair has evidence or an explicitly accepted
reason it does not apply; unimplemented APIs and option-level limits remain part
of R-31's parity gate.

The execution order authorized on 24 November is complete React parity and G0,
then the JX/Spring starter and faster hot reload. That authorizes continued work;
it does not substitute for Verify, runtime evidence, manual editor checks,
cross-browser qualification or an explicit decision to narrow scope.
