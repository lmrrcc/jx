# React interaction evidence — R-27

25 November 2026. Parent: [implementation sequence](ga-tasks.md#r-27-implementation-slices).
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
R-27-02c merged as `a0f5460980c6b288f3ac2fa2c4c73c0b774f0563` in
[PR #41](https://github.com/lmrrcc/jx/pull/41), with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45461059543) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45460586560).
The Promise prerequisite merged as `f2b64cbb92d2c77476840c77958a0eefdc87af8a` in
[PR #42](https://github.com/lmrrcc/jx/pull/42), with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45467245368) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45466911060).
R-27-03a merged as `1ba8c1daab8f5587b37f9cf9dd2fc7e369205aaa` in
[PR #43](https://github.com/lmrrcc/jx/pull/43), with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45480919951) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45480798051).
R-27-03b merged as `3e24edb1b9ce74575b4ab4bb5ac873147d8b773b` in
[PR #44](https://github.com/lmrrcc/jx/pull/44), with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45548420721) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45548370606).
R-27-03c merged as `812a27a4af3c684c1ca94400b958c909f806fe1f` in
[PR #45](https://github.com/lmrrcc/jx/pull/45), with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45551387801) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45551292980).
R-27-04 has complete local bench evidence for four server/resource pairs and a
fatal-shell readiness correction. Its three scoped mutants were killed; Verify is pending.
No additional human or cross-browser
qualification is implied by these automated results.

## Current evidence and remaining scope

The passing counts below include the complete R-27-04 local run. Its four
server/resource pairs extend the transition slice's 48 to **52 / 5,356**, with
**5,304** remaining. This server slice remains under review; the merged R-27-03c
baseline has 48 complete pair witnesses.
This is assertion evidence, not credit earned by registration alone.

| Measure | Result |
| --- | --- |
| Currently supported/equivalent feature IDs | 104 |
| Candidate unordered pairs among those IDs | 5,356 |
| Pairs with a passing witness in every declared mode, on both React and JX | 52 |
| Current candidate pairs without complete evidence | 5,304 |
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
| R-27-03a action case | Four pairs: action state with form actions, form status, Java async functions and the existing error-boundary equivalent. Queued submissions retain their payloads and compose previous results; rejection cancels successors, while retry and unmount dispose fixture-owned waits before a fresh form is mounted. |
| R-27-03b optimistic/store case | Three pairs: optimistic state with immutable external-store snapshots, transitions and Java async functions. Remote publications rebase the overlay; rejection removes only the optimistic item; retry commits one item while a second await keeps the transition pending; explicit owner close settles both gates and releases requests and subscriptions. |
| R-27-03c transition case | Three pairs: transition with urgent controlled input and ref-owned operations; Java async functions with effect disposal. Replaced and canceled consumers settle without waiting for transport; an already completed result cannot publish after owner replacement; unmount/remount isolates old work from the new instance. |

The interactive component scenarios run their assertions in client, hydrate and lazy modes.
R-27-04 owns its Node streaming and SSR-to-hydration lifecycle inside client-labelled
projects, as explained below.
Static mode renders the combined scenarios and compares HTML, but does not execute their
interactive assertion blocks and therefore earns no pair credit. None of the 52
current witnesses claims static interaction coverage.

The register explicitly limits lazy/Suspense and portal/events to client/lazy.
Hydration witnesses run inside the `hydrate-server` case in the **client projects**:
that case owns its server render and hydration itself. The streaming witness also
uses client projects but executes in Node, not a browser page. These project names
must not be mistaken for extra browser-mode coverage. Existing case exclusions
remain recorded in [`modes.tsv`](../tests/conformance/modes.tsv).

The original streaming witness checks shell/fallback/delayed-content ordering in
collected HTML. R-27-04 separately observes early bytes in a real Node Writable;
an HTTP streaming deployment remains unqualified.

## R-27-03c: urgent updates, operation ownership and completion races

Code revision `d89e23f` adds `combined-transition-cancel`. Native React and JX
share event-controlled transport gates, independently settled local consumers and
an operation owner retained by `useRef` and disposed by `useEffect`. The fixture
bounds requests to seven and audit entries to 64; it does not provide a public
cancellation API or cancel the transport itself.

| Witness | Observable behavior |
| --- | --- |
| `transition-urgent-input` | Controlled input state and a separate rendered value commit while the async transition remains pending. |
| `transition-owner-ref` | B replaces A and commits first; late A cannot overwrite B. Explicit cancellation settles C's consumer while its transport remains pending. A result already settled before replacement still cannot publish under its stale owner. |
| `async-effect-disposal` | Unmount cleanup settles the old consumer; a fresh instance starts independently, commits its own result and ignores the old transport's later completion. |

The completion race registers two transport observers. The first settles
`RACE_OLD`'s consumer with a successful value; the second creates `RACE_NEW` before
the first consumer's awaiting continuation runs. Canceling that already settled
consumer cannot alter its outcome, so publication must independently check the
current owner. The test observes the old transition becoming idle with the prior
visible result intact before attaching the new consumer to another transition.
This prevents concurrent action entanglement from hiding a stale intermediate
publication. The new request then completes and becomes visible.

The focused run passes **8 checks**. The full local bench passes **803 checks,
with 133 skipped, across 117 case IDs**:

| Mode | React | JX |
| --- | ---: | ---: |
| Client | 115 | 114 |
| Hydrate | 90 | 90 |
| Static HTML | 96 | 96 |
| Lazy route | 101 | 101 |

All three new witnesses pass in client, hydrate and lazy modes on both sides,
bringing complete local evidence to **48 / 5,356**, with **5,308** active pairs open.
The existing async/transition pair is not counted again. Static mode compares
initial HTML without interactive credit. The local ownership logic does not
complete G0's cancellation API or promise automatic cancellation of external I/O.

`transition-owner-guard-lost` removes the publication owner check while preserving
the canceled-outcome check. After the unmutated scratch baseline passed,
`combined-transition-cancel` killed it: **1 killed, 0 survived, 0 unusable**.
This result comes from the shared exclusive transition/SSR run at `abfb24a`, in
scratch `jx-mutation-1181316345297598843`; its transition implementation and spec
are identical to this slice's rebased `d89e23f`. The shared run's 112-entry catalog
includes later SSR cases; this slice's catalog has **110 entries**. Neither count
is a new full campaign, and the historical full result remains 100. The full local
gate and both Verify checks passed before PR #45 merged. To repeat the transition
check alone, exclusively:

```sh
java tools/MutateBench.java --only transition-owner-guard-lost
```

## R-27-03b: optimistic snapshots, rollback and request disposal

Merged revision `3e24edb` adds `combined-optimistic-store`, using native React hooks
and Java `Async.run`/`Async.await` against a typed, page-owned fixture store.
Its immutable snapshots preserve unchanged identity; all response and completion
gates are controlled by user events, without sleeps or network mocks.

| Witness | Observable behavior |
| --- | --- |
| `optimistic-store` | An independent remote publication rebases the pending optimistic view; after confirmation, the external snapshot and visible list contain exactly one local item. |
| `optimistic-transition` | A rejected action rolls back only its optimistic addition. Retry remains pending through a second await after commit, exposing duplicate overlays before the action completes. |
| `optimistic-async` | The Java async action catches rejection and releases its attempt in `finally`. Closing during either await settles owned gates; actual request and subscriber counts reach zero. |

Explicit close is fixture-owned disposal, not automatic framework cancellation
or completion of the G0 request-ownership API. A new page starts with an independent
store. The reducer's duplicate suppression is application logic exercised through
React's optimistic replay, not a deduplication guarantee supplied by JX.

The focused run passes **8 checks**. The full local bench passes **795 checks,
with 133 skipped, across 116 case IDs**:

| Mode | React | JX |
| --- | ---: | ---: |
| Client | 114 | 113 |
| Hydrate | 89 | 89 |
| Static HTML | 95 | 95 |
| Lazy route | 100 | 100 |

All three witnesses pass in client, hydrate and lazy modes on both implementations,
bringing complete local evidence to **45 / 5,356**, with **5,311** active pairs open.
Static HTML equality supplies no interactive witness. The full local gate and
both Verify checks passed before [PR #44](https://github.com/lmrrcc/jx/pull/44)
merged as `3e24edb`.

The unmutated scratch baseline passed, then `combined-optimistic-store` killed
`optimistic-duplicate-overlay`: **1 killed, 0 survived, 0 unusable**. The mutant
lets the pending optimistic reducer append a second copy of an already committed
item, and fails the visible-list assertion while the second gate is still pending.
At this slice the catalog contained **109 entries**; the historical full campaign still covers
100. Run the scoped check exclusively:

```sh
java tools/MutateBench.java --only optimistic-duplicate-overlay
```

## R-27-03-PROMISE: observer isolation prerequisite

Code revision `7ed3f2c` repairs a reproduced Promise delivery defect: one throwing
observer could prevent the bridge's later observer from settling React's cached
native thenable. Separate observer microtasks retain registration order and
visible error reporting while allowing the suspended reader to resume without an
unrelated parent update. Both JVM and Chrome regressions failed before repair.

Local evidence is **9 Promise JVM tests**, included within **36 runtime-package
JVM tests**, and **13 Chrome runtime checks**. The full local push gate and CI
Verify passed before PR #42 merged as `f2b64cb`. See the [runtime contract](runtime.md#promise-observer-isolation).
This prerequisite added no conformance case or pair witness to the portal
baseline of **779 passes, 133 skipped, 114 case IDs and 38 / 5,356 pairs**.
The action slice below extends that baseline. These runtime checks do not qualify
all asynchronous ownership, cancellation or cross-browser behavior.

## R-27-03a: queued form actions and recovery

`combined-action-queue` adds four explicit witnesses in client, hydrate and lazy
modes. It uses native React `useActionState` and `useFormStatus`, with Java
`Async.run`/`Async.await` on the JX side and native async functions in the reference.

| Witness | Observable behavior |
| --- | --- |
| `action-queue-form` | Two submissions capture distinct FormData values, execute serially and compose previous state into the final total. Later input edits do not alter queued payloads; successful completion resets the uncontrolled field. |
| `action-queue-status` | The descendant form status reports the latest submitted payload while the queue remains pending, then returns to idle with action state. |
| `action-queue-async` | Explicit gates suspend the real async actions; releasing the first starts the second with the first result, and the queue remains pending until both finish. |
| `action-queue-error` | Rejection reaches the boundary, prevents the queued successor from starting and preserves the surrounding sibling. Remounting creates a fresh form that accepts a successful submission. |

The final witness exercises the existing `react:Component` error-boundary
equivalent; it does not implement Java-authored class components from R-28.
The same spec also retries while two actions are pending and unmounts a pending
form. Each old owner settles its waiter, ignores queued domain work and releases
the replacement form to start from its own state. This ownership/cancellation
logic is fixture-local; it is not the G0 request-cancellation API.

The focused run passes **8 checks**. The full local bench passes **787 checks,
with 133 skipped, across 115 case IDs**:

| Mode | React | JX |
| --- | ---: | ---: |
| Client | 113 | 112 |
| Hydrate | 88 | 88 |
| Static HTML | 94 | 94 |
| Lazy route | 99 | 99 |

All four pair witnesses pass in every declared mode, bringing local evidence to
**42 / 5,356**, with **5,314** active pairs open. Static rendering compares initial
HTML and earns no interactive witness. The portal baseline merged in PR #41.
This action slice merged in [PR #43](https://github.com/lmrrcc/jx/pull/43) as
`1ba8c1d`, after the full local gate and both Verify checks passed.

`action-queue-previous-lost` changes the fixture's accumulated total to discard
the previous action state. The unmutated scratch baseline passed, then
`combined-action-queue` killed it: **1 killed, 0 survived, 0 unusable**.
At this slice the catalog contained **108 entries**; the historical full campaign still
covers 100. Run the scoped check exclusively:

```sh
java tools/MutateBench.java --only action-queue-previous-lost
```

The separate R-27-03-PROMISE prerequisite has local red/green JVM and Chrome
evidence: nine Promise tests, 36 runtime-package JVM tests and 13 runtime browser
checks pass. Its full local gate and CI Verify passed before PR #42 merged; it
adds no pair credit.

## R-27-04: streaming, resource hydration and fatal readiness

The combined local bench passes **809 checks, 151 skipped, across 120 case IDs**.
Client/API results are **118/118 reference and 117/117 JX**; hydrate **90/90 each**,
static **96/96 each** and lazy **101/101 each**. Four new executed witnesses bring
the combined interaction evidence to **52 / 5,356**, with **5,304** pairs open.
Its three scoped mutation checks passed; Verify is pending.

`combined-server-stream` uses actual Node `Writable` instances. Two request-owned
data promises remain gated while the spec observes the first shell and fallback
bytes. This would deadlock if piping waited for all content. The newer request
is aborted and its destination finishes and closes; the older suspended request
then resumes with its own context and data. Late data for the closed request
changes neither its bytes nor its write count. Starting and completing the
requests in opposite orders prevents a single global "current request" from
accidentally satisfying the isolation assertion.

`combined-resource-hydration` serves the actual server HTML as the initial browser
document while holding the application module. The spec retains element handles
to the resource link and button before allowing hydration. Two counter updates
prove that handlers are attached, preserve those original nodes and leave exactly
one preload link despite repeated preload calls.

| Witness | Interacting APIs | Evidence |
| --- | --- | --- |
| `stream-use-gate` | `renderToPipeableStream` + `use` | Shell bytes arrive while both data promises remain gated. |
| `stream-context-isolation` | `renderToPipeableStream` + `useContext` | Aborting one stream does not replace the surviving request's provider or leak its values. |
| `ssr-preload-dedupe` | `renderToString` + `preload` | Repeated hints produce one link in the initial document. |
| `hydrate-preload-reuse` | `hydrateRoot` + `preload` | Hydration and updates retain the original link and button without duplicate hints. |

All four witnesses run in the **client-labelled projects**: the streaming case
executes in Node, and the hydration case owns its own SSR-to-browser handoff.
The latter is excluded from generic hydrate/static/lazy modes in `modes.tsv`.
These results do not claim an HTTP streaming deployment, authenticated request
isolation, automatic cancellation of arbitrary application loaders or completion
of G0 M1-26. The older `stream-suspense` witness still proves collected HTML order;
the new gated Writable witness supplies the separate early-delivery evidence.

The additional `server-fatal-readiness` regression exposed a production defect.
Before the fix, the React reference passed while JX rejected `shellReady()` but
left `allReady()` pending after a fatal shell render. React's `onShellError` now
rejects both JX signals with the same failure. The green test observes both
rejections and one error notification, without treating an empty result as success.
It adds no interaction-pair credit. `allReady()` means React finished its server
work: a failed or aborted boundary may still contain fallback HTML.

The test suite covers the full SSR API cases plus the existing hydration case.
`npm run test:ssr` now selects streaming, prerender, resume and hydration as well
as the string renderers; its former `server-render` filter missed those cases.
The complete bench records every case and all declared interaction assertions.

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl tests/conformance -am package
npm run test:ssr
BENCH_UPDATE=1 npm run test:bench
```

The unmutated scratch baseline passed. An exclusive run at code revision
`abfb24a` tested these three SSR mutants together with
`transition-owner-guard-lost`: **4 killed, 0 survived, 0 unusable**. SSR accounts for
three of those results; the transition result is recorded in R-27-03c.

| Mutant | Detecting case |
| --- | --- |
| `hint-preload-dropped` | `combined-resource-hydration`, `resource-hints` |
| `stream-writable-waits-for-all` | `combined-server-stream` |
| `stream-fatal-all-pending` | `server-fatal-readiness` |

The last mutant restores the observed pending-readiness defect. The catalog has
112 entries; this scoped run is not a new full campaign. The historical full
campaign remains 100. Repeat the SSR subset exclusively:

```sh
java tools/MutateBench.java --only stream-writable-waits-for-all,stream-fatal-all-pending,hint-preload-dropped
```

Known unqualified failure path: a destination failure after the shell is emitted
does not necessarily trigger React's `onShellError`; the Java `HtmlSink` adapter
currently ignores destination destruction. The fatal-shell regression does not
establish terminal readiness/error delivery for that different path. Reproduce
and resolve it before advertising general sink-failure safety. Human and
cross-browser qualification also remain open.

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

PR #41 merged with green Verify. This slice brought the catalog to **107 entries**. The historical complete
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

R-27-02 extends identity/composition/DOM interactions. R-27-03a merged with evidence
for form actions and recovery. R-27-03b (optimistic updates over external-store
snapshots) also merged with bench, mutation and green Verify evidence.
R-27-03c (transition interactions) merged with bench evidence, a killed scoped
mutant and both Verify checks green.
Staged fixtures are preparation, not completed evidence.
R-27-04 has local evidence for early streaming, request-context isolation,
resource deduplication and retained hydration nodes; fatal shell errors reject both
readiness consumers. Its three scoped mutants were killed; Verify remains pending. Each slice selects explicit
remaining pair IDs. R-27-05 follows R-28–R-30, exercises the deferred features and
reviews the remaining applicability decisions. The parent R-27 stays open until
every eventual application-feature pair has evidence or an explicitly accepted
reason it does not apply; unimplemented APIs and option-level limits remain part
of R-31's parity gate.

The execution order authorized on 24 November is complete React parity and G0,
then the JX/Spring starter and faster hot reload. That authorizes continued work;
it does not substitute for Verify, runtime evidence, manual editor checks,
cross-browser qualification or an explicit decision to narrow scope.
