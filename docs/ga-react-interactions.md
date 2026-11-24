# React interaction evidence — R-27

24 November 2026. Parent: [implementation sequence](ga-tasks.md#r-27-implementation-slices).
R-27 is **partial**. R-27-01 implements the interaction register, executed assertion
reporting and two combined scenarios. Its tested head `56e247d` merged as
`f6018a4` in [PR #38](https://github.com/lmrrcc/jx/pull/38), with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45435501261)
and [green CI](https://github.com/lmrrcc/jx/actions/runs/45435438849).
Its complete local bench and scoped mutation run pass. R-27-02a merged as
`413558b636679dd5926f4d7bf1c987ea707ed95c` in [PR #39](https://github.com/lmrrcc/jx/pull/39),
with [green CI](https://github.com/lmrrcc/jx/actions/runs/45443187836) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45442627237).
R-27-02b merged as `15c0c6eee36f506910e854c568547ebc6d3758ea` in
[PR #40](https://github.com/lmrrcc/jx/pull/40), with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45452120669) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45452801956).
R-27-02c has a passing complete local bench and its scoped mutant was killed;
Verify is pending.
No additional human or cross-browser
qualification is implied by these automated results.

## Current evidence and remaining scope

The passing counts below include the complete R-27-02c local run. Its six new
portal pairs increase the merged R-27-02b baseline of 32 to **38 / 5,356**, with **5,318**
remaining. This is assertion evidence, not credit earned by registration alone.

| Measure | Result |
| --- | --- |
| Currently supported/equivalent feature IDs | 104 |
| Candidate unordered pairs among those IDs | 5,356 |
| Pairs with a passing witness in every declared mode, on both React and JX | 38 |
| Current candidate pairs without complete evidence | 5,318 |
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
| R-27-02a children cases | Six pairs: Children with keys, state, list rendering, children props, explicit fragments and child values. Mapping and flattening preserve composed identity; shape and validity checks match native React. |
| R-27-02b ref cases | Seven pairs: callback refs with keys, callbacks, layout effects and Strict Mode; imperative handles with keys, changed props and ref props. Reordering retains real nodes and handles; removal clears refs; dependency and ref changes update ownership without replacing the input. |
| R-27-02c portal case | Six pairs: portal with context, capture phases, event payloads, default/propagation controls, element refs and layout effects. Context follows React ancestry while DOM stays under `document.body`; close clears refs and disconnects the node, and reopen focuses a fresh input. |

The combined scenarios run their assertions in client, hydrate and lazy modes.
Static mode renders the combined scenarios and compares HTML, but does not execute their
interactive assertion blocks and therefore earns no pair credit. None of the 38
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

## R-27-02c: portal lifecycle, context and events

`combined-portal-lifecycle` adds six explicit pair witnesses in client, hydrate
and lazy modes. The portal is **initially closed**, so SSR and static rendering
produce an ordinary host tree without a portal. Opening after hydration exercises
the browser portal; this is not evidence of server rendering an open portal.

| Witness | Observable behavior |
| --- | --- |
| `portal-context` | The panel's actual parent is `document.body`, outside its React owner's DOM subtree. A provider update reaches it while its input retains the same Playwright ElementHandle and draft. |
| `portal-capture` | Capture runs through logical owner and panel, then the target and both bubble handlers run in the expected order. |
| `portal-payload` | The nested `SPAN` remains the target; each handler sees its own currentTarget. Bubble handlers observe the target's updated `defaultPrevented` flag. |
| `portal-propagation` | Preventing default keeps the URL unchanged; stopping propagation retains capture and target events while excluding both ancestor bubble handlers. |
| `portal-dom-ref` | Closing clears the actual DOM ref and disconnects the retained input handle. Reopening creates a different input with its initial value. |
| `portal-layout` | Committed layout setup focuses the attached input; closing runs cleanup, including after a reopen. |

The focused run passes **8 checks**. The complete local bench passes **779 checks,
with 133 skipped, across 114 case IDs**:

| Mode | React reference | JX |
| --- | --- | --- |
| Client, including Node API cases | 112 / 112 | 111 / 111 |
| Hydration | 87 / 87 | 87 / 87 |
| Static HTML | 93 / 93 | 93 / 93 |
| Lazy route | 98 / 98 | 98 / 98 |

All six new witnesses pass in their three declared interactive modes, raising
complete evidence to **38 / 5,356**, with **5,318** active pairs open. Static HTML
equality gives no interactive pair credit. These are Chrome automated checks;
human and cross-browser qualification remain open.

The unmutated scratch baseline passed, then `combined-portal-lifecycle` killed
`portal-context-stale`: **1 killed, 0 survived, 0 unusable**. The mutation freezes
the provider at its initial theme, so the context-update assertion fails. Repeat
the check exclusively, without another build or browser suite:

```sh
java tools/MutateBench.java --only portal-context-stale
```

Verify is pending. The catalog has **107 entries**. The historical complete
mutation campaign still covers 100, and no fresh complete 107-mutant result is
claimed.

## R-27-02b: keyed refs and imperative handles

Two differential scenarios add seven explicit pair witnesses:

| Case | Witnesses and observable behavior |
| --- | --- |
| `combined-keyed-refs` | `key-callback-ref`, `callback-ref-identity`, `layout-callback-ref`, `strict-callback-ref`: stable callbacks avoid reattachment on parent rerenders; changed dependencies detach the old callback and attach the new one to the same node. Keyed reordering retains nodes; removal cleans only the removed row; restoration creates a fresh node. Layout reads the attached input, and final removal balances Strict Mode's attachment/cleanup calls. |
| `combined-imperative-handles` | `key-imperative-handle`, `props-imperative-handle`, `refprop-imperative-handle`: handles and their DOM nodes follow keys; changed props create fresh handles whose methods use the current prefix; switching the supplied ref clears the old owner and populates the new one without replacing the input. Unmount clears the published handles. |

The specs compare actual Playwright element handles for DOM identity. The fixtures
remember actual imperative-handle objects and compare Java identity (`==`) with
the reference's JavaScript identity (`===`); `fill`, `read` and focus operate on
the real input. Layout assertion refinement additionally reads the row-specific
input value, so two different `INPUT` elements cannot satisfy it merely by sharing
a tag name. All eight focused rendering projects pass after this refinement.
The slice merged in [PR #40](https://github.com/lmrrcc/jx/pull/40) as
`15c0c6eee36f506910e854c568547ebc6d3758ea`, with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45452120669) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45452801956).

The recorded complete local bench passes **771 checks, with 133 skipped, across
113 case IDs**:

| Mode | React reference | JX |
| --- | --- | --- |
| Client, including Node API cases | 111 / 111 | 110 / 110 |
| Hydration | 86 / 86 | 86 / 86 |
| Static HTML | 92 / 92 | 92 / 92 |
| Lazy route | 97 / 97 | 97 / 97 |

All seven new witnesses pass on both sides in client, hydrate and lazy modes,
raising complete pair evidence to **32 / 5,356**, with **5,324** active pairs open.
Static HTML equality contributes no interactive pair credit. These are Chrome
automated results, not additional human or cross-browser qualification.

The unmutated scratch baseline passed, then `imperative-stale-props` was killed by
`combined-imperative-handles`: **1 killed, 0 survived, 0 unusable**. This mutation
removes the imperative handle's reactive dependencies, exposing stale captured
props. Run it exclusively, without another build or browser suite:

```sh
java tools/MutateBench.java --only imperative-stale-props
```

That slice's catalog had **106 entries**. The historical full campaign still covers
100; the scoped R-26/R-27 runs do not establish a fresh complete 106-mutant result.

## R-27-02a: children contracts

Three differential reproduction cases passed on React and failed on JX before
the fixes. They exposed lost mapped keys and the conflation of component children
collections with explicitly authored fragments. The six selected pairs combine
`react:Children` with `jsx:key`, `react:useState`, `jsx:list-rendering`,
`jsx:children`, `react:Fragment` and `jsx:child-values`.

| Case | Observable contract |
| --- | --- |
| `combined-children-map` | Composed source/wrapper keys preserve independent child state across reordering; changing a wrapper key resets its subtree; removing and reinserting resets the removed child. |
| `combined-children-list` | Flattened nested arrays retain local key namespaces and child state, including repeated keys in different nested collections. |
| `combined-children-contract` | Explicit fragments are opaque single elements; nested arrays are traversed; `only` rejects singleton arrays, text and null; implicit children and explicit `children` props agree. Native element/portal checks run after a browser event without mounting the portal. Numeric key strings are produced by the compiled Java consumer and compared with native React. |

The compiler now emits `Views.childrenValue` in build and editor projections,
preserving null, singleton and collection shapes independently of explicit
fragments. `Children.map` and `toList` compose keys through `View.Keyed`, without
adding a React reconciliation boundary. `View.Items.staticChildren` preserves
React's fixed-sibling validation metadata while dynamic collections still undergo
list-key checks. `View.ElementExtension` identifies native elements as distinct
from portal nodes. See [runtime contracts and compiling consumers](runtime.md#children-traversal-composed-keys-and-native-nodes)
and the linked API Javadocs.

JVM and TeaVM numeric vectors cover `1.0`, negative zero, `1e-6`, `1e-7`, `1e20`,
`1e21`, `1e23`, the smallest subnormal and largest finite double, widened `1.1f`,
`NaN`, both infinities and `9007199254740991L`. The browser assertion compares
effective keys with native JavaScript number spelling and React key escaping;
initial HTML comparison also observes those generated strings. This tests the
Java numeric conversion after TeaVM compilation, rather than inferring browser
behavior from JVM tests.

The final Maven build passes **28 core, 41 compiler and 15 Maven-plugin tests**.
After the fixed-sibling validation repair, four focused cases passed in all eight
projects (32 passes). A separate native-wrapper hot-reload reproduction then found
checkpoint paths following positions instead of composed keys. Its repair passes
the complete **12 / 12 runtime suite**, including mapped DOM and native-wrapper
hot replacement.

The native hot-replacement fixture uses `StrictMode`. Opaque foreign bindings and
error-boundary fallback callbacks outside their original Java render frame still
need separate ownership/identity qualification; this result does not close those
remaining combinations.

The complete bench passes **755 checks, with 133 skipped, across 111 case IDs**:

| Mode | React reference | JX |
| --- | --- | --- |
| Client, including Node API cases | 109 / 109 | 108 / 108 |
| Hydration | 84 / 84 | 84 / 84 |
| Static HTML | 90 / 90 | 90 / 90 |
| Lazy route | 95 / 95 | 95 / 95 |

All six new pairs have passing assertion witnesses on both sides in every declared
mode, raising complete pair evidence to **25 / 5,356**. The unmutated scratch
baseline passed, then all three scoped mutants were killed, with zero survivors
or unusable mutants. `strict-mode-dropped` failed `strict-nested`;
`children-not-listed` failed the existing child utilities and new contract/list
cases; `children-source-key-lost` also failed the mapped-child case. R-27-02a merged
in [PR #39](https://github.com/lmrrcc/jx/pull/39) as
`413558b636679dd5926f4d7bf1c987ea707ed95c`, with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45443187836) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45442627237).
At this slice the catalog had 105 entries (one added, two adapted); the historical
full campaign still covers 100. A scoped result must not be reported as a full
105-mutant campaign.

Run the scoped campaign exclusively, without concurrent builds or browser suites:

```sh
java tools/MutateBench.java --only children-not-listed,children-source-key-lost,strict-mode-dropped
```

The new witnesses declare client, hydrate and lazy modes. Static HTML comparison
checks initial rendering, including numeric key output, but supplies no interactive
pair credit. Existing per-case exclusions remain in effect.

## R-27-01 results and repeatable checks

The R-27-01 local run on 24 November reported **731 passed, 133 skipped**, across
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
The R-27-01 catalog had 104 mutants; the recorded historical full campaign covers
100. R-26 separately killed its two new mutants. Neither scoped run establishes a
fresh full 104-mutant campaign.

## Follow-up and acceptance

R-27-02 extends identity/composition/DOM interactions. R-27-03 is split before
implementation into planned R-27-03a (form actions and failure/recovery), R-27-03b
(optimistic updates over external-store snapshots) and R-27-03c (transition
interactions). Staged fixtures are preparation, not completed evidence.
R-27-04 extends server/resource interactions. Each selects explicit
remaining pair IDs. R-27-05 follows R-28–R-30, exercises the deferred features and
reviews the remaining applicability decisions. The parent R-27 stays open until
every eventual application-feature pair has evidence or an explicitly accepted
reason it does not apply; unimplemented APIs and option-level limits remain part
of R-31's parity gate.

The execution order authorized on 24 November is complete React parity and G0,
then the JX/Spring starter and faster hot reload. That authorizes continued work;
it does not substitute for Verify, runtime evidence, manual editor checks,
cross-browser qualification or an explicit decision to narrow scope.
