# GA implementation sequence

Revision 13, 25 November 2026. Parent: [GA roadmap](ga-roadmap.md). This file turns
the [work package register](ga-execution.md) into an ordered list of tasks, grouped
by the roadmap's milestones. Work proceeds down this list, one task at a time.

Execution order confirmed by Luca on 24 November 2026: continue implementation
through the complete React parity track and the G0 proofs, then deliver the
JX/Spring starter and the new fast hot-reload workflow. Keep one PR per coherent
slice and preserve automated gates. Record outstanding human qualification or
applicability decisions explicitly; the request to continue is not evidence that
an untested capability works.

Progress, 23 November 2026: M1-01 to M1-05 are done. R-01 to R-18 and R-32 were
recorded complete; the audit of that day reopened R-16 because its promised
`useFormState` alias was absent, and the alias with its bench case was added the
same day. That makes 19 complete React tasks; R-19 (root options) followed, also on 23 November, making 20. M1-24 and R-20 (server rendering to a string, in a Node host) followed on 24 November, making 21. M1-25 and R-21 (hydration of Node-rendered HTML) followed the same day, making 22. R-22 (streaming server rendering) followed, making 23. R-23 (static prerender) followed, making 24. R-24 (resume) followed, making 25; no bench case is reference-only any more. R-25 (generated coverage of
elements, attributes and event names) followed on 24 November, making 26. The latest code revision `2667d2c` has
[Verify green](https://github.com/lmrrcc/jx/actions/runs/45015947179).
`main` is protected: a change reaches it only through a pull request whose Verify
check has passed. R-13 added `Async.run`, `Async.await` and `Promise`; R-14 delivered
the `lazy` factory, and M1-22/M1-23 subsequently proved independently compiled
modules across a serialized boundary. R-26 (every case in every applicable
rendering mode) is complete: Luca's try-it run matched all recorded results on
24 November, and code revision `eb5a18a` passed
[Verify](https://github.com/lmrrcc/jx/actions/runs/45372691183).
That makes 27 complete React tasks. R-27-01 and R-27-02a–02c merged with green
Verify, establishing 38 executed pair witnesses. The Promise prerequisite also
merged in [PR #42](https://github.com/lmrrcc/jx/pull/42) with green Verify.
R-27-03a merged in [PR #43](https://github.com/lmrrcc/jx/pull/43) as `1ba8c1d`,
with both Verify checks green. The derived-props candidate over merged
functional PR #54 passes 879 local bench checks
with 54 witnessed pairs among 6,328 active candidates; 6,274 remain without
complete evidence. The optimistic/store, transition and SSR scoped mutants were killed
after their unmutated baselines passed. R-27-03b merged as `3e24edb` in
[PR #44](https://github.com/lmrrcc/jx/pull/44), and R-27-03c as `812a27a` in
[PR #45](https://github.com/lmrrcc/jx/pull/45), with both Verify checks green.
R-27-04 merged in [PR #47](https://github.com/lmrrcc/jx/pull/47) as `7759933`,
with [green push Verify](https://github.com/lmrrcc/jx/actions/runs/45555926188)
and [green PR Verify](https://github.com/lmrrcc/jx/actions/runs/45556004135).
R-28-01 merged as `ccfcb2d` in [PR #48](https://github.com/lmrrcc/jx/pull/48), with
[green push Verify](https://github.com/lmrrcc/jx/actions/runs/45560759038) and
[green PR Verify](https://github.com/lmrrcc/jx/actions/runs/45560907924). R-27 remains
partial; its slice status and the
[interaction evidence record](ga-react-interactions.md) distinguish local results
from outstanding acceptance.
M1-26 (protected data route) is the
remaining server-render proof. See the [dated status review](status.md#progress-review--23-november-2026)
for remaining platform work and evidence limits. G0 has not passed.

The Flight proof merged in [PR #49](https://github.com/lmrrcc/jx/pull/49) as
`5fa53c0`, after the 15-step gate and both Verify checks passed. Direct `.jx`
class authoring merged in [PR #50](https://github.com/lmrrcc/jx/pull/50) as
`3e1db97`, after its 15-step gate and both Verify checks passed. R-29's eight
utility rows merged in [PR #52](https://github.com/lmrrcc/jx/pull/52) as `8fdbed0`:
the 15-step local gate passed in 285 seconds, and both
[push Verify](https://github.com/lmrrcc/jx/actions/runs/45591209626) and
[PR Verify](https://github.com/lmrrcc/jx/actions/runs/45591325376) passed on tested head
`f0b5a5b`. All four scoped mutants were killed after the baseline passed. Its active feature universe
has 113 features and 6,328 candidate pairs, with 54 witnessed and 6,274 open.
The five deferred features contribute a further 575 eventual candidate pairs.

## Rules

1. **One task is one isolated change**: one commit (or pull request) that builds and
   passes the configured checks on its own. A task never depends on a later task.
2. **Every task implements something.** It adds or changes code and adds an
   automated check that exercises the new behavior. Design decisions are written as
   an ADR inside the task that first needs them, not as a task of their own. The
   only exceptions are the gate reviews at the end of a milestone.
3. **Every task has a "try it" command** that Luca runs before the next task starts.
   A task is `done` only after that check and a green Verify workflow.
4. A new public capability comes with a compiling consumer example and a row in the
   [capability ledger](evidence/capabilities.tsv).
5. A task that turns out too large is split here before work starts; a spike that
   fails keeps its test as evidence and records a go/change/stop decision.
6. Tasks in M1 and M2 are specified. Tasks in M3 and later are the intended slices;
   they are re-cut into changes of this size when their milestone starts, and may
   change after the gate reviews.

Status flags: **✅ done · 🟡 partial · ⬜ planned / to do · ➡️ next · ⛔ blocked**.
Every task table, including later milestones, carries an explicit status.

Status values: `done` (approved, committed and green in Verify), `partial`
(delivered behavior with an explicit remaining acceptance gap), `approved`
(Luca approved; not yet committed), `review` (implemented, waiting for Luca's
check), `next`, `planned`, `blocked` (the stated dependency must be resolved).
The "GA" column names the work package the task belongs to. A completed narrow
task does not close its broader milestone or GA package.

## Test bench

Decided by Luca on 21 November 2026: JX is proven first against a purpose-built
test bench, not against the existing products. The products come later, as pilots.

- **A case** is one React behavior in a small scenario. It is written twice: a
  reference in plain React (framework-owned JavaScript, allowed as an interop
  fixture) and the JX version in Java and `.jx`. One Chrome spec states the
  observable outcome and runs unchanged against both.
- **The reference must pass.** That proves the spec describes real React behavior.
  The JX result is `pass`, `fail` or `unsupported`.
- **Isolation:** each case mounts alone on a fresh page load with its own root and
  no shared application state or external backend. Lazy-mode checks control local
  chunk requests. Independent host and route builds keep case code out of the
  initial lazy-mode download.
- **Coverage is enumerated, not guessed.** The list of stable exports of the pinned
  `react` and `react-dom` packages is generated; each export must map to a case or
  to an exclusion with a reason. A React upgrade that adds an export fails the check
  until it is mapped.
- **Two axes:** feature cases, and rendering modes (client-rendered application,
  static HTML, server render with hydration, lazy route). A case declares the modes
  that apply to it.
- **Combinations:** literal "all permutations" is unbounded. The bench has every
  feature alone, every feature in every applicable mode, and a set of combined
  scenarios chosen so that each pair of features meets at least once. More
  combinations are added when a defect shows one was missing.
- **Every feature task turns bench cells from `unsupported` or `fail` to `pass`**,
  in addition to its own tests. The `unsupported` cells are the remaining work, in
  one table.

## React parity track

Decided by Luca on 21 November 2026: JX is to support **all** React features, not a
chosen subset. The measure is the [feature list](evidence/conformance.tsv): 120
rows as of 23 November 2026, covering every export of the pinned `react` and `react-dom` packages
plus markup and DOM features. Each row that is not yet `supported` or `equivalent`
names the task below that closes it; `CheckBaseline` fails if one does not.

Target at the end of the track: no row `unsupported`, no row `untested`. Only two
rows stay excluded, because they are not React features an application can use: the
`react-dom` build marker and the React Compiler (a build tool for JavaScript
sources). Any other exclusion needs Luca's approval, recorded in the row.

Every R task follows the same pattern, as one change: write the reference case
(it must pass), implement the feature in JX with a documented Java API and a
consumer example, turn the JX column to `pass`, and update the row. A feature whose
React shape cannot exist in Java (untyped spread, element introspection) gets a
typed equivalent with the same observable outcome, marked `equivalent`.

| Task | Milestone | Change | Rows closed | Status |
| --- | --- | --- | --- | --- |
| R-01 | M1 | Cases for untested markup features: custom hooks, function and render props, run-time component choice, dotted component names. A case that fails becomes a defect fix in the same task | 4 untested | ✅ done |
| R-02 | M1 | Cases for untested DOM features: radio, select, textarea, uncontrolled inputs, capture-phase handlers, custom elements, document metadata, resource elements, root error callbacks | 7 untested | ✅ done |
| R-03 | M1 | Reference-only cases for every unsupported row; the JX column reports `unsupported`; results written to `evidence/conformance-results.tsv`; Verify fails on any regression | none; gives every later task a waiting test | ✅ done |
| R-04 | M1 | Typed event model: mouse, keyboard with modifiers, focus, pointer, touch, wheel, composition, clipboard, drag, media and animation events, with `currentTarget` | 1 unsupported, 3 untested | ✅ done |
| R-05 | M1 | Mutation runner over the runtime bridge, core hook API and markup lowering; survivors become new cases until none remains | none; proves the bench detects defects | ✅ done |
| R-06 | M2 | `memo`, with custom comparison | 1 | ✅ done |
| R-07 | M2 | Refs: ref as a component prop, callback refs with cleanup, `useImperativeHandle`, the `forwardRef` and `createRef` equivalents | 5 | ✅ done |
| R-08 | M2 | `useInsertionEffect`, `useEffectEvent`, `useDebugValue`, `Profiler`; reducer lazy initializer; nested `StrictMode` | 4 | ✅ done |
| R-09 | M2 | Optional and default props, typed prop forwarding in place of spread, generic components | 3 | ✅ done |
| R-10 | M2 | `createPortal`, `flushSync` | 2 | ✅ done |
| R-11 | M2 | Typed equivalents of `Children`, `cloneElement` and `isValidElement` | 3 | ✅ done |
| R-32 | M2 | Child positions: a null or boolean child and a nested list each keep one position among their siblings, so a later sibling keeps its state. Found by the `child-slots` case on 21 November 2026; `Children.count` then counts empty positions as React does | 1 | ✅ done |
| R-12 | M3 | `useTransition`, `startTransition`, `useDeferredValue` | 3 | ✅ done |
| R-13 | M3 | `Suspense` and `use` for promises and context; `Promise` and async/await in Java | 2 | ✅ done |
| R-14 | M3 | Promise-based `lazy` component factory; separate chunk loading remains M1-22/M1-23 | 1 | ✅ done |
| R-15 | M3 | `Activity`; `ViewTransition` and `addTransitionType` | 3 | ✅ done |
| R-16 | M3 | Actions: form action functions, `useActionState`, `useFormStatus`, `useOptimistic`, `requestFormReset`, and the deprecated `useFormState` alias | 6 | ✅ done |
| R-17 | M3 | Raw HTML through an explicit, named API equal to `dangerouslySetInnerHTML` | 1 | ✅ done |
| R-18 | M3 | Resource hints: `preconnect`, `prefetchDNS`, `preinit`, `preinitModule`, `preload`, `preloadModule` | 6 | ✅ done |
| R-19 | M3 | Root options: caught, uncaught and recoverable error callbacks, identifier prefix | part of `createRoot` | ✅ done |
| R-20 | M4 | `renderToString`, `renderToStaticMarkup`, on the server-render proof from M1 | 2 | ✅ done |
| R-21 | M4 | `hydrateRoot`; server snapshot for external stores; `useId` across server and client | 1 | ✅ done |
| R-22 | M5 | Streaming: `renderToPipeableStream`, `renderToReadableStream`, with Suspense boundaries | 2 | ✅ done |
| R-23 | M5 | Static rendering: `prerender`, `prerenderToNodeStream` | 2 | ✅ done |
| R-24 | M6 | Resume: `resume`, `resumeToPipeableStream`, `resumeAndPrerender`, `resumeAndPrerenderToNodeStream` | 4 | ✅ done |
| R-25 | M4–M6 | Generated coverage of every HTML and SVG element, attribute and event name React accepts | deepens the `dom:` rows | ✅ done |
| R-26 | M6 | Every case run in every rendering mode that applies: client, static HTML, server render with hydration, lazy route | adds the mode axis | ✅ done: Luca's try-it confirms 715 passes and 133 inapplicable cells; Verify green at eb5a18a, 24 November |
| R-27 | M6 | Combined scenarios so that every pair of features meets at least once; split into R-27-01 onward below | adds pair coverage | 🟡 partial: 54 / 6,328 active pairs have local evidence after R-29 utility activation; 6,274 remain open, plus 575 deferred-feature pairs; utility cases add no pair credit |
| R-28 | M7 | Class components authored in Java: `Component` lifecycle and `PureComponent` | 2 | 🟡 partial: runtime R-28-01 and class authoring R-28-02a merged; native editor qualification and remaining lifecycle/context/options still open |
| R-29 | M7 | Remaining small surface; split into R-29-01 through R-29-04 below | 9 | 🟡 partial: eight rows merged in PR52 after green local gate and both Verify checks; four scoped mutants killed; cache refresh and deep owner diagnostics remain open |
| R-29-01 | M7 | Runtime version getters, development owner stacks and the legacy batching callback/return contract | 7 | ✅ done for the bounded utility contract: PR52 merged as 8fdbed0 with green gate/Verify; deep Java diagnostic chains remain explicitly assigned to R-29-04 |
| R-29-02 | M7 | Awaited asynchronous `act` for Java test consumers, including flushing, nesting, failure recovery and real production-build rejection | 1 | ✅ done for the asynchronous contract: PR52 merged with green gate/Verify; flush-bypass mutant killed after baseline passed; no synchronous or overlapping-scope parity claim |
| R-29-03 | M8–M10 | `unstable_useCacheRefresh` with an exercised server cache owner and invalidation contract; depends on R-30 | 1 | ⬜ planned; cache/signal proof merged in PR51, but refresh ownership and invalidation still need implementation and evidence |
| R-29-04 | M7 | Preserve useful development owner chains through deep Java/TeaVM element conversion without React-private stack mutation or global Error hooks; accepted limit or implementation required by R-31 | diagnostic depth | ⬜ planned: first R-29 run exposed truncated pure-Java owner chains |
| R-30 | M8–M10 | React Server Components: `cache`, `cacheSignal`, the client and server directives and the server-component protocol, behind a decision record after a feasibility proof | 4 | 🟡 partial — Flight PR49 and typed cache/signal/Java continuation context PR51 are merged with green local gates and both Verify checks; combined proof 28 Node + 4 Chrome. [ADR 0007](decisions/0007-react-server-components.md) tracks open directives, general references, server functions and production qualification |
| R-31 | M10 | Parity gate: zero `unsupported`, zero `untested`, only approved exclusions, mutation survivors zero, every case green in every applicable mode; all option-level limits implemented or explicitly accepted | closes the track | ⬜ planned |

R-31 functional component contract repair, 25 November 2026: 🟡 implemented
locally, integration pending. `ComponentType.render` now permits intentional null
output while React retains the mounted hook/effect owner; thrown errors still
propagate. Default memo comparison now agrees with native scalar values across
compatible Java boxed types, retains signed-zero distinctions and keeps opaque
objects by identity. Inexact native longs retain exact Java Long equality. The
new JVM regressions first reproduced both defects (one failure, one error, one
pass), then all three passed. The reactor package and 16 focused differential
checks pass across client/hydrate/static/lazy. Integrated over main `a7a3f62`, the
package passes in 21.820 seconds and the complete bench passes 849 checks with
151 inapplicable cells across 125 cases: client 123 React / 122 JX, hydration 95,
static HTML 101 and lazy 106 per side. Only the two new case rows are added to the
recorded results; interaction evidence stays 54/5460.
Both scoped mutants are killed after the unmutated baseline passes:
`component-null-rejected` by `empty-render`, and `memo-boxed-type-equality` by
`memo-scalars`; zero survivors or unusable mutations. The scratch evidence is
`jx-mutation-14936589724776533921`, with source `bab19c3`. Final integration and
publication completed in PR #54, as recorded below. No interaction-
pair credit or general R-31 completion is claimed.

Final integration over PR53 (`198c0b8`) preserves the tested functional source
and consumers byte for byte. The package passes 40 core, 48 compiler and 15 Maven
tests. The complete bench passes 871 checks with 169 inapplicable cells across
130 cases: client 128 React / 127 JX, hydration 97, static HTML 103 and lazy 108
per side. Both recorded result tables remain unchanged after the rebase; pair
evidence stays 54/6328. Source is `49c4725`. The two scoped mutants were qualified
on the identical prior source; no full-catalog mutation result is implied.
PR [#54](https://github.com/lmrrcc/jx/pull/54) merged as `adcf22b` after the
15-step local gate passed in 383 seconds and both
[PR Verify](https://github.com/lmrrcc/jx/actions/runs/45605539203) and
[push Verify](https://github.com/lmrrcc/jx/actions/runs/45605222192) passed on
`c6a6dba`.

R-28-SCALAR prerequisite, 25 November 2026: ✅ merged in
[PR #46](https://github.com/lmrrcc/jx/pull/46) as `03d9190`. Class-runtime work
exposed an existing numeric bridge defect: TeaVM's Java-object conversion can
merge signed zeros before React compares them. The isolated repair preserves the
native scalar path and native dependency arrays. The initial three browser
regressions failed before the repair and passed afterward. This standalone
prerequisite also passes 36 runtime-package JVM tests and all 16 Chrome runtime
checks, including strengthened commit/effect assertions. The full 14-step local
push gate and both Verify checks passed:
[push CI](https://github.com/lmrrcc/jx/actions/runs/45553740461) and
[PR CI](https://github.com/lmrrcc/jx/actions/runs/45553791574). See the
[runtime contract and compiling consumer](runtime.md#signed-zero-boundary-regression).
It enables no class-component API and adds no feature-pair coverage; R-28 remains
open.

R-29 contracts, 25 November 2026: version getters read the actual installed React
exports, rather than duplicating a build-time constant. Browser version methods
must not import server-only entry points; server/static versions stay on their
corresponding adapters. `ReactDiagnostics.captureOwnerStack()` returns React's
development owner stack, or null outside an owner/when the production export is
absent. `React.unstableBatchedUpdates(Supplier<T>)` invokes React's legacy export,
preserves the Java return value and propagates callback failures; modern roots
already batch automatically. `ReactTesting.act(Supplier<Promise<T>>)` is an
asynchronous, always-awaited test boundary. It delegates flushing to React,
assimilates the returned thenable immediately and requires the host to configure
the React test environment explicitly. It rejects in production. Synchronous
`act` overloads and Server Components cache refresh are not claimed by this slice.
Completion requires compiling Java consumers and differential assertions for
actual callback return/failure, async DOM/effect flushing, nested awaited scopes,
owner availability and actual development/production React bundles.

The first R-29 run exposed a diagnostic limitation: React bounds each element's
creation stack to ten frames, and its owner formatter discards a stack missing
the React rendering frame. A deep TeaVM conversion chain can therefore produce
an empty owner-stack string during an active development render/effect. Empty is
not null. The adapter preserves that raw result, and an explicit interoperability
fixture checks a real named React owner of a Java component. This does not qualify
full Java owner-chain diagnostics; R-29-04/R-31 remains open. No private React
stack field, fabricated frame or global Error hook is used to hide this limit.
The [owner-stack investigation](evidence/owner-stack-investigation.md) records the
installed-source evidence, why source maps cannot restore discarded frames and
the public-API renderer boundary that a future correction must qualify.

R-29 focused evidence, 25 November 2026: Maven package with compiling consumers
passes (21.129 seconds), then the four new case IDs pass 14 checks with 18
inapplicable cells. Client: 4/4 reference and 4/4 JX. Hydrate, static HTML and lazy:
1/1 per side. The production case bundles the actual production React packages
and verifies null diagnostics and `act` rejection without invoking the callback.
The development `act` case checks completion-time DOM/effect snapshots, including
an effect-triggered second update, a pending gate, Java result identity, awaited
nested scopes, synchronous/asynchronous rejection, subsequent recovery and unmount
cleanup. No full conformance table, mutation campaign or Verify result is claimed
from this focused run. Eight newly supported/equivalent rows expand the current
feature universe from 104 to 112, or 6,216 candidate pairs; they add no R-27 pair
witnesses by merely sharing a fixture.

R-29 full bench, 25 November 2026, on base `1ba8c1d`: **801 passed, 151 skipped**
across 119 case IDs (3.2 minutes). Client: 117 reference / 116 JX; hydrate 89,
static 95 and lazy 100 per side. All applicable cells pass and the case table is
recorded; the unchanged 42 pair witnesses now cover 42/6,216 candidates, leaving
6,174 open on this branch. Production manifests contain actual React and React
DOM production modules and no development modules; production browser versions
match the manifest pins. Four new curated mutants are registered for missing act
flushing, owner stacks, batching return values and runtime versions, but have not
been executed. This is not the local push gate or Verify, and R-29 remains partial.

R-29 integrated evidence, 25 November 2026, code `ab30bef` on merged Flight
`5fa53c0`: Maven package passes in 22.310 seconds; the four utility cases pass
14 focused checks with 18 inapplicable cells. The complete bench passes **839
checks, 169 skipped, across 126 case IDs** (4.5 minutes): client 124 reference /
123 JX, hydrate 93, static 99 and lazy 104 per side. All 54 existing interaction
witnesses pass; the eight utility rows expand the active universe to 113 features
and 6,328 candidate pairs, leaving 6,274 without complete evidence. No new pair
is credited from merely sharing a utility fixture. The four scoped utility
mutants subsequently passed their own qualification: the unmutated scratch bench
passed, then **4 killed, 0 survived, 0 unusable**. `act-utilities` killed
`act-flush-bypassed`; `runtime-utilities` killed `owner-stack-dropped`;
`runtime-utilities` and its production case killed `batch-return-dropped`;
those two plus `server-runtime-versions` killed `runtime-version-stale`.
The source revision was `c65598a` (`ab30bef` utility code), retained in scratch
`jx-mutation-3915892923157282960`; the rebased utility source `52eb29e` is
equivalent. The catalog has 120 entries after including merged class authoring;
the historical full campaign remains 100 and is not rerun or extended by this
scoped campaign. Publication gates remain pending.

After integrating merged `.jx` class authoring (`3e1db97`), the complete R-29
bench passes **847 checks, 169 skipped, across 127 case IDs** (3.9 minutes):
client 125 reference / 124 JX, hydrate 94, static 100 and lazy 105 per side.
Maven package passes 34 core, 48 compiler and 15 Maven-plugin tests in 22.539
seconds. All 54 interaction witnesses remain green among 6,328 active candidate
pairs. This is source `52eb29e`; scoped mutation source equivalence is recorded
above. These checks precede the full push gate and Verify; R-29 remains partial.

Final integration preserves the merged request-cache/continuation changes from
[PR #51](https://github.com/lmrrcc/jx/pull/51), merge `a7a3f62`, whose 15-step gate
passed in 287 seconds and whose [push Verify](https://github.com/lmrrcc/jx/actions/runs/45587877665)
and [PR Verify](https://github.com/lmrrcc/jx/actions/runs/45588054123) are green.
The rebased R-29 utility source is `79b0d28`; utility assertions and the four
mutant targets are unchanged. The final tested head `f0b5a5b` passed the 15-step
local gate in 285 seconds and both Verify checks before [PR #52](https://github.com/lmrrcc/jx/pull/52)
merged as `8fdbed0`. R-29 remains partial because cache refresh and deep owner
diagnostics have their own unfinished slices.

Audit, 23 November 2026: R-16's primary action APIs and browser cases are delivered,
but `useFormState` has no Java API or case and remains excluded in the feature
register. Add the alias and a differential consumer case, or obtain an explicit
scope decision; an exclusion is not completion under the all-features goal.
Resolved the same day: `Hooks.useFormState` and `Hooks.useFormStateAsync` are
deprecated aliases of the action-state hooks, with the differential case
`form-state`; the feature row is `equivalent`.
Other rows marked supported have recorded limits: `useActionState` lacks permalink,
ViewTransition lacks callbacks, resource preloads lack
image source-set/sizes, and external stores lack server snapshots (R-21). Include
these in R-31 acceptance; export-name coverage alone is insufficient.

R-31-FORM-STATUS, 25 November 2026: 🟨 implemented locally. `FormStatus.action()`
preserves the submitted Java function, a button override or the action-state
dispatcher; a native React function returns a stable Java handle that can be passed
back unchanged. `actionUrl()` preserves the URL alternative observed in the pinned
runtime during an `onSubmit` transition. The expanded existing `form-status` case
passes all eight React/JX projects, including repeated submissions after a parent
render, native React interoperability/replay, no parent form and the URL path with
no navigation or automatic reset. The package and structural checks pass. Two
targeted mutants are registered but not yet run; integrated full bench, push gate
and Verify remain pending. This adds no case ID or feature-pair claim. The original
three-argument constructor remains available; record patterns/equality now include
the two new action alternatives, as documented in [the language guide](language.md).

R-01 result, 21 November 2026: all four cases passed on JX without a defect; the
rows are now `supported` (three) and `equivalent` (dotted names).

R-02 result, 21 November 2026: five of the seven cases passed at once. Document
metadata and hoisted styles failed because JX wrapped every text child in a
fragment, which `title` and `style` reject; text children are now plain strings,
and all seven rows are `supported`.

R-03 result, 21 November 2026: 41 reference-only cases cover all unsupported rows,
including the ten server and static rendering functions, which run in Node. The
reference passes every one. `docs/evidence/conformance-results.tsv` records the
table, and the bench fails when a run differs from it.

R-04 result, 21 November 2026: `jx.Event` gained typed accessors for every event
family and `jx.EventTarget` was added. Four event cases pass on both sides; no row
is `untested` any more, and 48 rows are `unsupported`.

R-05 result, 21 November 2026: 34 curated mutants; the first run killed 33. The
survivor (layout effects ignoring their dependencies) showed a missing case, which
was added as `layout-effect-dependencies`; rerun alone, that mutant is now killed.
Results are in `docs/evidence/mutation-results.tsv`. The mutants are hand-written,
not generated, so this measures the listed defects only.

R-26 acceptance, 24 November 2026: all four rendering modes are wired into the bench.
The complete local run passes: client 104 reference / 103 JX, hydrate 79 per side,
static HTML 85 per side, and lazy route 90 per side (715 checks, 133 structurally
inapplicable cells across 106 case IDs). The lazy totals
include the two new loading and failure/retry scenarios. The host/route static
import graph and TeaVM source maps must prove that cases are absent from the
initial download, alongside real request timing, error UI and explicit retry.
DOM element, attribute and event comparisons use the same mode on both sides.
Root-owning scenarios and the unknown route have explicit lazy exclusions;
`error-reporting` retains its uncaught root-error policy. Details and commands are
in the [bench guide](../tests/conformance/README.md) and
[ADR 0005](decisions/0005-lazy-module-boundary.md).

Try it after the Maven build: `npm run test:bench`. Run the two new curated
mutations separately, with no concurrent build or browser suite:
`java tools/MutateBench.java --only lazy-route-eager,lazy-host-includes-cases`.
The scoped run on 24 November killed both mutants: early activation fails the
lazy cases; an eager case registry fails `lazy-route-loading`'s source-map check.
Neither mutant survived or failed to compile. This does not replace the complete
mutation evidence table or claim a full 102-mutant rerun. Luca's try-it run reports
715 passed, 133 skipped and an exact match with the recorded table. Together with
[green Verify at eb5a18a](https://github.com/lmrrcc/jx/actions/runs/45372691183),
this closes R-26.

### R-28 implementation slices

Split before implementation on 24 November 2026. The [class runtime decision](decisions/0006-java-class-components.md)
requires actual React classes with typed Java peers, native queues and shallow
state merges. Functional components remain the foundation; `jx.Component` remains
the annotation. Runtime support does not imply `.jx` class authoring, complete
lifecycle parity or preserved class state across hot replacement.

| Task | Deliverable | Status |
| --- | --- | --- |
| R-28-01 | Explicit Java descriptors and schemas; actual React Component/PureComponent; typed partial updates, basic lifecycle/snapshots/refs and compiling consumer with browser comparison | ✅ done: PR [#48](https://github.com/lmrrcc/jx/pull/48), merged `ccfcb2d`; 34 core, 41 compiler, 15 Maven tests and 825 integrated bench checks; three scoped mutants killed; local gate 14/14 and both Verify checks passed. Native IDE and broader class qualification remain separate tasks |
| R-28-02 | `.jx` class authoring, generated field projections and patches, diagnostics and native editor projections | 🟡 in progress: split into compiler/consumer slice 02a and editor qualification 02b; no completion credit yet |
| R-28-02a | Direct native class declarations in `.jx`, generated typed schemas and immutable state-patch builder, class tags/refs, mapped diagnostics and compiling/browser consumers | ✅ merged PR #50 at 3e1db97: 34 core, 48 compiler, 15 Maven and 10 LSP tests pass; generated class consumer passes 8/8 projects; integrated bench 833 checks/151 skipped/123 cases and unchanged 54/5460 pairs; snapshot mutant killed after baseline passed; 15-step local gate and both Verify checks passed |
| R-28-02b | Qualify class-aware Java completion, navigation, rename and inspections in both native IDE adapters, including incomplete editor source | ⬜ planned |
| R-28-03 | Context, derived state/error lifecycle, error information, SSR/hydration/lazy qualification and remaining class API options; split below | 🟡 in progress: native context merged in PR53; derived props in review; error lifecycle and compiler declaration sugar remain separate slices |
| R-28-03a | Native contextType through additive typed factories; constructor/current context, Pure/SCU bypass, defaults/null, retained state and Activity lifecycle | ✅ done: PR [#53](https://github.com/lmrrcc/jx/pull/53), merged `198c0b8`; package 37 core/48 compiler/15 Maven tests; 855 checks/169 skipped/128 cases, unchanged 54/6328 pairs; captured-context mutant killed after baseline passed, zero survived/unusable; local gate 15/15 in 308 s and both Verify checks passed on `30a8911`; native instance retention preserved, no disposal guarantee |
| R-28-03b | Pure derived-state-from-props with typed partial patches, null updates and native render timing | 🟡 review: integrated package 42 core/48 compiler/15 Maven tests; full bench over PR54 passes 879 checks/169 skipped/131 cases, all 40 class cells pass, unchanged 54/6328 pairs; constructor/setState/SCU/Pure/partial/null/empty behavior tested; dropped-derivation mutant killed after a passing baseline, zero survived/unusable; final gate/Verify pending |
| R-28-03c | Derived error state plus committed didCatch, checked Java error/component-stack information and recovery | ⬜ planned |
| R-28-03d | Compiler context/derived-state declaration sugar, typed third nextContext argument for should-update, and remaining class option qualification | ⬜ planned |
| R-28-04 | Committed class checkpoints, concurrent/discard behavior, option matrix and mutation qualification | ⬜ planned |

R-28-03a publication evidence: [PR #53](https://github.com/lmrrcc/jx/pull/53)
merged after its 15-step local gate passed in 308 seconds and both
[push Verify](https://github.com/lmrrcc/jx/actions/runs/45598507275) and
[PR Verify](https://github.com/lmrrcc/jx/actions/runs/45598573765) passed on
`30a8911`. These checks qualify the context slice, not the remaining class options.

Class hot replacement explicitly resets state in the first slice. Preservation
requires a commit observer that covers skipped renders; `componentDidUpdate`
alone is insufficient. Legacy lifecycle/default-prop/null-state behavior and
generic schemas remain open until implemented or explicitly reviewed for R-31.

The feature ledger marks both native class APIs `supported` with those explicit
limits; this describes callable runtime support, not completion of all R-28
options. `equivalent` described the previous error-boundary-only Java route and
would be misleading for the new actual React class bridge. Activating the
previously deferred PureComponent row increases the active universe from 104 to
105 features, or 5,460 candidate pairs. The two new executed witnesses prove
PureComponent with keys and instance refs; 54 pairs now have complete local
evidence after integration with the server/resource witnesses. All three runtime
class mutants were killed after the unmutated baseline passed. Repeat in isolation:
`java tools/MutateBench.java --only class-pure-renders,class-snapshot-lost,class-ref-detach-lost`.

### R-27 implementation slices

Split before implementation on 24 November 2026 under working rule 5. With the
R-28-01 runtime slice, the ledger contains 105 supported/equivalent feature IDs, giving 5,460 candidate unordered
pairs. Excluding only the two permanent non-API rows (`react-dom:browser` and
`framework:react-compiler`), the eventual universe is 118 features / 6,903 pairs;
1,443 additional pairs depend on the 13 deferred features. Alias rows remain
distinct until an explicit applicability decision is reviewed. These counts are
candidate relationships, not a claim that every pair has a meaningful interaction.

A pair earns evidence only through a named, executed assertion of an interaction
on both React and JX. Co-occurrence in a fixture or feature table earns no credit.
Static HTML equality does not establish an interactive lifecycle assertion.
Unlisted pairs remain uncovered; a new feature automatically expands the universe.
No broad area-level exclusion or reduction to feature families closes this task.

| Task | Deliverable | Status |
| --- | --- | --- |
| R-27-01 | Pair register and gate; real keyed/memo/context and Activity/store lifecycle scenarios; explicit assertion evidence and scoped mutation checks | ✅ merged PR #38: 19 pair witnesses, 731 bench passes, both scoped mutants killed and green Verify; no additional human qualification claimed |
| R-27-02 | Extend identity, refs, composition, DOM and event interactions from the remaining pair inventory; delivery slices below | 🟡 partial: R-27-02a, R-27-02b and R-27-02c merged; remaining pair inventory stays open |
| R-27-02a | Children mapping and flattening preserve keyed identity; explicit fragments stay opaque; single-child validation matches React | ✅ merged PR #39 at 413558b: 755 full bench and 12 runtime checks; six new pairs; three scoped mutants killed; green Verify |
| R-27-02b | Keyed callback refs and imperative handles: replacement, fresh props, cleanup, Strict Mode and layout ordering | ✅ merged PR #40 at 15c0c6e: 771 full bench passes; seven new pairs in two scenarios; scoped mutant killed; green Verify |
| R-27-02c | Initially closed portal: context, capture/bubble, propagation, payloads and focus after hydration | ✅ merged PR #41 at a0f5460: 779 full bench and 8 focused checks; six new pairs; scoped mutant killed; green Verify |
| R-27-03 | Extend async, form action, optimistic, transition and external-store interactions, including controlled failure/recovery; delivery slices below | ✅ delivery slices merged: Promise prerequisite and 03a–03c; remaining pair coverage stays in R-27-05 |
| R-27-03-PROMISE | Prerequisite: isolate Promise observer delivery so a throwing observer cannot leave React's native thenable pending | ✅ merged PR #42 at f2b64cb: red/green JVM and Chrome evidence; nine Promise tests, 36 runtime-package JVM tests and 13 runtime Chrome checks; local gate and Verify passed |
| R-27-03a | Combined form actions with submission state, reset and controlled failure/recovery | ✅ merged PR #43 at 1ba8c1d: 787 full bench and 8 focused checks; four new pairs; scoped mutant killed; local gate and Verify passed |
| R-27-03b | Optimistic state over immutable external snapshots: remote rebase, rejected-request rollback and retry without duplicate commits | ✅ merged PR #44 at 3e24edb: 795 full bench and 8 focused checks; three new pairs; explicit close during both awaits releases requests and subscriptions; scoped mutant killed; local gate and Verify passed |
| R-27-03c | Transition interactions with independent updates and controlled asynchronous completion | ✅ merged PR #45 at 812a27a: 803 full bench and 8 focused checks; three new pairs; completed-result owner race and unmount isolation proved; scoped mutant killed after baseline passed; local gate and Verify passed |
| R-27-04 | Extend SSR, hydration, streaming and resource interactions with mode-specific assertions | ✅ merged PR #47 at 7759933: 809 full bench checks, four new pairs, real early Writable bytes and retained hydration nodes; fatal-shell readiness regression corrected; three scoped mutants killed after baseline passed; local gate and both Verify checks passed |
| R-27-05 | Add deferred-feature interactions after R-28–R-30; review every remaining pair's applicability and close coverage gaps | ⬜ planned |

These are delivery slices, not a disjoint assignment of every pair. Each follow-up
must select explicit remaining pair IDs and remain in progress while its inventory
is open. R-27 closes only when every application-feature pair has evidence or an
approved, reasoned not-applicable decision; none is granted by this initial split.

R-27-02 was split before implementation on 24 November. R-27-02a selects
`react:Children` with `jsx:key`, `react:useState`, `jsx:list-rendering`,
`jsx:children`, `react:Fragment` and `jsx:child-values`. Its reference scenarios
must demonstrate key preservation through wrappers and nested collections,
opaque explicit fragments, and rejection of invalid `Children.only` inputs.
All three differential reference cases passed while JX failed before the fixes.
The compiler now preserves implicit children shapes with `childrenValue`; mapped
keys use `View.Keyed` without an extra React boundary; fixed authored siblings
retain validation metadata while dynamic list checks remain enabled. Native
elements and portal nodes have distinct element-validity contracts, with numeric
key vectors exercised on both JVM and TeaVM.

R-27-02a evidence: 28 core, 41 compiler and 15 Maven-plugin tests passed; four
focused cases passed in all eight projects (32 passes). A separate native-wrapper
hot-reload test reproduced checkpoint paths following positions instead of
composed keys; after repair, all 12 runtime checks pass. The full bench reports
755 passed, 133 skipped across 111 case IDs: client 109 reference / 108 JX,
hydrate 84 per side, static 90 per side and lazy 95 per side. The six new pairs
have complete declared-mode evidence, giving 25 / 5,356 and 5,331 remaining.
The three scoped curated mutants were killed. [PR #39](https://github.com/lmrrcc/jx/pull/39)
merged as `413558b636679dd5926f4d7bf1c987ea707ed95c`, with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45443187836) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45442627237).
Its 105-entry catalog was not a full-run result; the historical full campaign still covers 100.
Details and compiling
consumers are in the [R-27-02a evidence record](ga-react-interactions.md#r-27-02a-children-contracts).

R-27-02b local evidence: 771 passed, 133 skipped across 113 case IDs. Client passes
111 reference / 110 JX, hydration 86 per side, static HTML 92 per side and lazy
97 per side. The seven ref pairs have complete client/hydrate/lazy witnesses,
raising evidence to 32 / 5,356, with 5,324 active pairs open. The unmutated scratch
baseline passed; `imperative-stale-props` was killed by `combined-imperative-handles`
(1 killed, 0 survived, 0 unusable). That slice's catalog had 106 entries, not a fresh
full 106-mutant campaign. [PR #40](https://github.com/lmrrcc/jx/pull/40) merged as
`15c0c6eee36f506910e854c568547ebc6d3758ea`, with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45452120669) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45452801956). These Chrome automated checks do
not establish additional human or cross-browser qualification.

R-27-02c local evidence: 779 passed, 133 skipped across 114 case IDs; client 112
reference / 111 JX, hydration 87 per side, static HTML 93 per side and lazy 98 per
side. All eight focused checks pass. Six new portal pairs raise complete evidence
to 38 / 5,356, with 5,318 active pairs open. Real ElementHandles, focus, ref cleanup
and ordered event payloads establish behavior across the logical React ancestry.
The portal begins closed for SSR; it opens only in the browser after hydration,
so this is not qualification of server-rendered portals. The unmutated scratch
baseline passed, then `combined-portal-lifecycle` killed `portal-context-stale`
(1 killed, 0 survived, 0 unusable). This slice brought the catalog to 107 entries.
[PR #41](https://github.com/lmrrcc/jx/pull/41) merged as `a0f5460980c6b288f3ac2fa2c4c73c0b774f0563`,
with [green CI](https://github.com/lmrrcc/jx/actions/runs/45461059543) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45460586560). No fresh full mutation
campaign, human qualification or cross-browser qualification is implied. Details
are in the [portal evidence record](ga-react-interactions.md#r-27-02c-portal-lifecycle-context-and-events).

R-27-03 is split into 03a actions, 03b optimistic/store behavior and 03c transition
interactions before implementation. Each slice must select explicit remaining
pair IDs and demonstrate them on both implementations; staged fixture preparation
does not close a task or earn interaction credit.

R-27-03a local evidence: 787 passed, 133 skipped across 115 case IDs; client 113
reference / 112 JX, hydration 88, static HTML 94 and lazy 99 per side. All eight
focused checks pass. Four action-state pairs cover form actions, descendant form
status, Java async functions and the existing error-boundary equivalent. Queue
rejection cancels successors; retry after rejection, retry while pending and
unmount/remount preserve independent owners. Cancellation is fixture-local and
does not complete the G0 request-ownership API or R-28 class components.
Local evidence reaches 42 / 5,356, with 5,314 active pairs open, including the
38-pair portal baseline merged in PR #41. The unmutated scratch baseline passed,
then `combined-action-queue` killed `action-queue-previous-lost` (1 killed,
0 survived, 0 unusable). [PR #43](https://github.com/lmrrcc/jx/pull/43) merged as
`1ba8c1daab8f5587b37f9cf9dd2fc7e369205aaa`, with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45480919951) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45480798051). Its catalog size was 108;
the historical full mutation campaign remains 100. Details and the scoped command
are in the [action evidence record](ga-react-interactions.md#r-27-03a-queued-form-actions-and-recovery).

R-27-03b local evidence: 795 passed, 133 skipped across 116 case IDs; client 114
reference / 113 JX, hydration 89, static HTML 95 and lazy 100 per side. All eight
focused checks pass. Three witnesses combine optimistic state with external
snapshots, transitions and Java async functions. Remote updates rebase the
pending view; rejection rolls back only the local addition; retry commits once
while a second await keeps the transition pending. Closing during either await
settles both fixture-owned gates and releases actual requests and subscriptions.
This explicit fixture disposal does not deliver the G0 cancellation API.
Local evidence reaches 45 / 5,356, with 5,311 active pairs open. The unmutated
scratch baseline passed, then `combined-optimistic-store` killed
`optimistic-duplicate-overlay` (1 killed, 0 survived, 0 unusable).
[PR #44](https://github.com/lmrrcc/jx/pull/44) merged as
`3e24edb1b9ce74575b4ab4bb5ac873147d8b773b`, with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45548420721) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45548370606).
That slice's catalog has 109 entries; the historical full campaign remains 100.
Details and the scoped command are in the
[optimistic/store evidence record](ga-react-interactions.md#r-27-03b-optimistic-snapshots-rollback-and-request-disposal).

R-27-03c local evidence: 803 passed, 133 skipped across 117 case IDs; client 115
reference / 114 JX, hydration 90, static HTML 96 and lazy 101 per side. All eight
focused checks pass. Three new witnesses cover urgent controlled input during a
transition, ref-owned replacement/cancellation and async effect disposal.
An explicitly ordered race settles the old consumer successfully, changes its
owner before its awaiting continuation resumes, and observes the unchanged UI
after the old transition ends. Only then does a separate transition await the
replacement, avoiding entanglement that could conceal stale publication.
Unmount/remount isolates old work from a fresh instance. Ownership and consumer
cancellation are fixture-local; transport may finish later and G0 remains open.
Evidence reaches 48 / 5,356, with 5,308 active pairs open. The existing
async/transition witness earns no duplicate credit. After the shared scratch
baseline passed, `combined-transition-cancel` killed `transition-owner-guard-lost`
(1 killed, 0 survived, 0 unusable). The exclusive run used `abfb24a`, with identical
transition source and spec to rebased `d89e23f`, in scratch
`jx-mutation-1181316345297598843`. The full local gate and both Verify checks
passed before PR #45 merged as `812a27a`. This slice's catalog has
110 entries; the shared run's 112 includes later SSR cases. Both remain separate
from the historical 100-mutant full campaign. See the
[transition evidence record](ga-react-interactions.md#r-27-03c-urgent-updates-operation-ownership-and-completion-races).

R-27-03-PROMISE repairs a defect found while preparing those scenarios. Previously,
all waiting observers ran inside one microtask; an earlier observer throwing
prevented delivery to the native thenable used by React. The JVM regression failed
on the still-pending bridge observer, and the Chrome regression stayed on the
Suspense fallback after resolving the Java promise. Separate observer microtasks
preserve registration order and visible error reporting while allowing subsequent
observers to run. The browser now resumes without an unrelated parent update.

Local checks on 24 November: `PromiseTest` passes nine tests; the runtime package
with tests enabled passes 36 JVM tests (29 core, including those nine, and seven
Elm); the focused Chrome regression passes, and `npm run test:runtime` passes all
13 checks. See the [runtime contract and commands](runtime.md#promise-observer-isolation).
The full local push gate and CI Verify passed. [PR #42](https://github.com/lmrrcc/jx/pull/42)
merged as `f2b64cbb92d2c77476840c77958a0eefdc87af8a`, with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45467245368) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45466911060).
It adds no feature-pair witness; R-27-03a, 03b and 03c are merged.

R-27-01 local evidence, 24 November 2026: the complete bench reports 731 passed and
133 skipped cells: client 106 reference / 105 JX, hydrate 81 per side, static 87 per
side and lazy 92 per side. Nineteen pairs have an executed assertion witness in
every declared mode on both implementations; 5,337 active pairs remain uncovered.
Static HTML checks do not credit interactive witnesses. The
[interaction evidence record](ga-react-interactions.md) documents the marker
contract, exact scenarios, mode limits and commands. R-27-01's tested head
`56e247d` merged as `f6018a4` in [PR #38](https://github.com/lmrrcc/jx/pull/38), with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45435501261) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45435438849). This does not
claim additional human or cross-browser qualification. Its 104-entry curated
mutant catalog is not a fresh full-run result; R-27-01's two
new mutants were killed in a scoped run after its unmutated baseline passed.
There were no survivors or unusable mutants.

Expected count of `unsupported` rows after each milestone: M1 48, M2 32, M3 11,
M6 0. These are planning figures. R-30 is the largest single item and the one most
likely to move: it needs a server protocol that the current architecture does not
have, and the earlier roadmap kept it outside 1.0.

Platform work (contracts, Spring, editors, packaging) continues in the milestone
tables below. Within a milestone, R tasks and M tasks are done one at a time in the
order Luca picks at each review; the default is R tasks first.

## M1 — G0: feasibility and measured baseline (19 Nov–18 Dec 2026)

Exit gate: the five proofs (component JAR, exact shared contract, authenticated
packaged Spring prototype, lazy-runtime boundary, React SSR to hydration) each run
as a test, with a recorded decision.

| Task | GA | Change | Try it | Status |
| --- | --- | --- | --- | --- |
| M1-01 | 01 | Green baseline, version manifest, capability ledger, `CheckBaseline` | `java tools/CheckBaseline.java` | ✅ done |
| M1-02 | 02 | Portfolio and journey registers, old-UI baseline, pilot decisions | `java tools/CheckBaseline.java` | ✅ done |
| M1-03 | 18 | Test bench skeleton: a reference React app and a JX app that mount one case per page load, one shared Chrome spec run against both; first cases: state, effect cleanup, keyed list | `npm run test:bench` | ✅ done |
| M1-04 | 18 | React API inventory generated from the pinned `react` and `react-dom` packages; `conformance.tsv` maps every export to a case or a reasoned exclusion; `CheckBaseline` fails on an unmapped export | `java tools/CheckBaseline.java` | ✅ done |
| M1-05 | 18 | Bench cases for everything JX implements today: all current hooks, context, refs, error boundaries, events, controlled inputs, Strict Mode, attributes, text and child values. 32 cases; fixed a key warning for context providers with several children | `npm run test:bench` | ✅ done |
| M1-10 | 05 | Compiler writes a versioned component metadata descriptor into each module's output | `./mvnw -pl jx-compiler test` | ⬜ planned |
| M1-11 | 05 | Compiler resolves components from descriptors in classpath JARs, without their sources | `./mvnw -pl jx-compiler test` | ⬜ planned |
| M1-12 | 05 | `examples/component-library` (producer JAR) and `examples/library-consumer` (separate app) with a Chrome test | `npm run test:library` | ⬜ planned |
| M1-13 | 05 | Incompatible or missing descriptor version fails the build with a diagnostic at the `.jx` usage | `./mvnw -pl jx-maven-plugin test` | ⬜ planned |
| M1-14 | 03 | One cancellation type in `jx-core`, used by both `jx-browser` HTTP and `jx-elm` commands | `npm run test:runtime` | ⬜ planned |
| M1-15 | 03 | `useResource` hook: loading, value and error states, abort on unmount or key change, stale replies dropped | `npm run test:runtime` | ⬜ planned |
| M1-16 | 03 | Router loader built on the same model; ADR 0003 records the async ownership and Suspense decision | `npm run test:runtime` | ⬜ planned |
| M1-17 | 04 | `examples/todo-contract`: one Todo value and operation paths compiled into both the Spring API and the browser app | `npm run test:todo` | ⬜ planned |
| M1-18 | 04 | Exact-value vectors (64-bit ids, decimals, null and missing) run on the JVM and in Chrome against the real Spring endpoint | `npm run test:contract` | ⬜ planned |
| M1-19 | 04 | Generated browser codec for the one contract value replaces the hand-written one | `npm run test:todo` | ⬜ planned |
| M1-20 | 13 | The todo API packaged as one executable JAR that serves the built UI; started from an empty directory in a test | `npm run test:packaged` | ⬜ planned |
| M1-21 | 13 | Session login and CSRF on the todo API; development proxy forwards cookies to its one allowed backend | `npm run test:auth` | ⬜ planned |
| M1-22 | 06 | Fixture with a second TeaVM module loaded on demand; test records what crosses: record, context, callback | `npm run test:lazy` | ✅ done |
| M1-23 | 06 | Chosen boundary (shared runtime or serialized ABI) with chunk-failure and retry tests; ADR 0005 | `npm run test:lazy` | ✅ done |
| M1-24 | 07 | Node host renders a JX component to HTML with React's server renderer | `npm run test:ssr` | ✅ done |
| M1-25 | 07 | Browser hydrates that HTML without replacing the tree | `npm run test:ssr` | ✅ done |
| M1-26 | 07 | Protected data route: two simultaneous identities, immutable snapshot, abort, no second fetch | `npm run test:ssr` | ⬜ planned |
| M1-27 | 37 | Packaged MV3 extension renders a JX popup under its CSP and calls a WASM function; popup close and reopen | `npm run test:extension` | ⬜ planned |
| M1-28 | 08 | 100-component scale fixture and an editor latency benchmark in the IntelliJ tests | `./gradlew test` in `tooling/intellij` | ⬜ planned |
| M1-29 | 08 | Source-preserving structural edit proof (component rename across closed files), or the recorded limit | `./gradlew test` in `tooling/intellij` | ⬜ planned |
| M1-30 | 09 | Artifacts installed to a file repository; an outside consumer builds with only that repository | `npm run test:consumer` | ⬜ planned |
| M1-31 | — | G0 review: ledger updated, go/change/stop recorded per proof, M2–M3 re-cut | read the review record | ⬜ planned |

## M2 — G1: shared application skeleton (19 Dec–18 Jan)

Exit gate: published-local contract and component modules, generated async client,
Boot JAR serving UI and API, session and CSRF journey in development and production.

| Task | GA | Change | Try it | Status |
| --- | --- | --- | --- | --- |
| M2-01 | 10 | Contract annotations and the intermediate model, with validation errors mapped to source | `./mvnw -pl jx-contract test` | ⬜ planned |
| M2-02 | 10 | Browser codec generator for records, enums, lists, optional and exact numbers | `npm run test:contract` | ⬜ planned |
| M2-03 | 10 | Asynchronous browser client generator with typed errors and cancellation | `npm run test:contract` | ⬜ planned |
| M2-04 | 10 | Spring server boundary from the same contract; conformance test against real HTTP | `./mvnw -pl examples/todo-api test` | ⬜ planned |
| M2-05 | 10 | Wire schema file; an incompatible contract change fails the build | `./mvnw -pl jx-contract test` | ⬜ planned |
| M2-06 | 12 | `jx-spring-boot-starter`: serves packaged assets, SPA fallback that never answers `/api` or missing assets | `./mvnw -pl jx-spring-boot-starter test` | ⬜ planned |
| M2-07 | 12 | Starter backs off to user configuration; BOM module | same | ⬜ planned |
| M2-08 | 12 | Application template; a generated project builds and runs from an empty directory | `npm run test:template` | ⬜ planned |
| M2-09 | 13 | Same-origin development: the backend fronts the dev server; owned process lifecycle | `npm run test:auth` | ⬜ planned |
| M2-10 | 13 | Session expiry, logout and CSRF failure journeys, in development and from the packaged JAR | `npm run test:auth` | ⬜ planned |
| M2-11 | 11 | Differential harness: the same vectors run on the JVM and in TeaVM | `npm run test:profile` | ⬜ planned |
| M2-12 | 11 | First certified set: `BigDecimal`, `long`, selected `java.time` | `npm run test:profile` | ⬜ planned |
| M2-13 | 11 | Dependency scanner names the first unsupported reachable method and its call path | `./mvnw -pl jx-maven-plugin test` | ⬜ planned |
| M2-15 | — | G1 review | read the review record | ⬜ planned |

## M3 — Alpha 1: language and reusable components (19 Jan–18 Feb)

| Task | GA | Change | Status |
| --- | --- | --- | --- |
| M3-04 | 14 | Markup recovery for incomplete edits; fuzz test | ⬜ planned |
| M3-05 | 15 | HTML and SVG attribute schema; wrong types rejected at source | ⬜ planned |
| M3-06 | 16 | Nested routes and layouts | ⬜ planned |
| M3-07 | 23 | Composed source maps: browser stack traces point to `.jx` | ⬜ planned |
| M3-08 | 09 | Signed test packages through the candidate distribution path | ⬜ planned |
| M3-09 | 29 | Java 21 and Kotlin Gradle Boot service consumes a JX contract and assets | ⬜ planned |

## M4 — Alpha 2: complete application workflow (19 Feb–18 Mar)

| Task | GA | Change | Status |
| --- | --- | --- | --- |
| M4-01 | 16 | Route loaders and actions on the resource cache; invalidation | ⬜ planned |
| M4-02 | 16 | Optimistic update with rollback; stale and out-of-order replies | ⬜ planned |
| M4-03 | 17 | Form model with field arrays, validation and server errors | ⬜ planned |
| M4-04 | 19 | Request headers and binary bodies | ⬜ planned |
| M4-05 | 19 | Upload with progress; download | ⬜ planned |
| M4-06 | 19 | SSE and WebSocket lifecycle with bounds and reconnect | ⬜ planned |
| M4-07 | 21 | Reference product: workspace, roles, list and detail | ⬜ planned |
| M4-08 | 21 | Reference product: two-tenant isolation and session-expiry tests | ⬜ planned |
| M4-09 | 27 | Generated checkpoint codecs for hot updates | ⬜ planned |

## M5 — Alpha 3: React ecosystem and rich input (19 Mar–18 Apr)

| Task | GA | Change | Status |
| --- | --- | --- | --- |
| M5-05 | 15 | Safe rich-content rendering with one CSR and SSR policy | ⬜ planned |
| M5-06 | 20 | Binding SDK and a first headless-control binding | ⬜ planned |
| M5-07 | 20 | Grid or virtualized table binding | ⬜ planned |
| M5-08 | 20 | Chart and Markdown/code bindings | ⬜ planned |
| M5-09 | 17 | Localization, RTL and accessible form primitives | ⬜ planned |

## M6 — G2: website rendering alpha (19 Apr–18 May)

| Task | GA | Change | Status |
| --- | --- | --- | --- |
| M6-01 | 22 | Static generation: HTML and CSS with no client runtime for content pages | ⬜ planned |
| M6-02 | 22 | Opt-in hydration per page; metadata, canonical and sitemap | ⬜ planned |
| M6-03 | 22 | Request-time SSR host with isolation, limits and timeouts | ⬜ planned |
| M6-04 | 22 | Hashed assets, route chunks and cache headers | ⬜ planned |
| M6-05 | 18 | Lazy routes on the M1 boundary | ⬜ planned |
| M6-06 | 30 | Website pilot, first pages: `lmrr.cc` listings in JX beside the existing site | ⬜ planned |
| M6-07 | 28 | Performance budgets evaluated in CI against the portfolio baseline | ⬜ planned |

## M7 — Beta 1: daily developer platform (19 May–18 Jun)

| Task | GA | Change | Status |
| --- | --- | --- | --- |
| M7-01 | 23 | `.jx` breakpoints and stepping in the browser | ⬜ planned |
| M7-02 | 24 | IntelliJ run and debug parity; safe closed-file edits | ⬜ planned |
| M7-03 | 25 | VS Code class and file rename, formatting, closed-file references | ⬜ planned |
| M7-04 | 27 | 100-update hot-reload soak without leaks or replay | ⬜ planned |
| M7-05 | 20 | Media and payment-element bindings | ⬜ planned |
| M7-06 | 30 | Website pilots: Stacktrace and luca4j pages, with rollback | ⬜ planned |
| M7-07 | 31 | Stack Sensei pilot: first read-only route as a separate implementation beside the existing frontend | ⬜ planned |

## M8 — Beta 2: portfolio readiness (19 Jun–18 Jul)

| Task | GA | Change | Status |
| --- | --- | --- | --- |
| M8-01 | 26 | TurboJ native `.jx` integration | ⬜ planned |
| M8-02 | 38 | Browser storage with schema migration and quota recovery | ⬜ planned |
| M8-03 | 38 | Opt-in service-worker profile without unsafe offline replay | ⬜ planned |
| M8-04 | 33 | Extension profile: storage, messaging and worker lifecycle | ⬜ planned |
| M8-05 | 31 | Stack Sensei pilot: authenticated route with one-to-one journey tests | ⬜ planned |
| M8-06 | 32 | Media permission, cleanup and streaming qualification slice | ⬜ planned |

## M9 — Beta 3: extension and migration hardening (19 Jul–18 Aug)

| Task | GA | Change | Status |
| --- | --- | --- | --- |
| M9-01 | 33 | Gandalf read-only popup screen inside the existing package | ⬜ planned |
| M9-02 | 32 | Exact-value and payment-recovery qualification in a provider sandbox | ⬜ planned |
| M9-03 | 34 | Version-skew tests: old tab against new backend | ⬜ planned |
| M9-04 | 34 | Retained-asset rollback rehearsal | ⬜ planned |
| M9-05 | 28 | Firefox and WebKit in CI; Windows build | ⬜ planned |

## M10–M12 — RC entry, stabilization, GA decision (19 Aug–18 Nov 2027)

| Task | GA | Change | Status |
| --- | --- | --- | --- |
| M10-02 | 34 | Public API, wire and metadata compatibility checks enforced in CI | ⬜ planned |
| M10-03 | 28 | Security, performance and accessibility review fixes | ⬜ planned |
| M11-01 | 35 | Clean-machine install and upgrade from published candidates | ⬜ planned |
| M11-02 | 35 | 30-day pilot observation; recovery and version-skew drills | ⬜ planned |
| M12-01 | 36 | GA evidence manifest and go/no-go for application and website profiles | ⬜ planned |
| M12-02 | 39 | Portfolio qualification sign-off for media, finance and extension | ⬜ planned |

## Changes from the register order

- The test bench comes first (M1-03 to M1-05), followed by the React parity
  track. Together they replace the React slices that the register spread over
  GA-14, GA-15, GA-18 and GA-22, and extend them to every React feature.
- GA-05 (component JAR) follows the bench, ahead of GA-03 and GA-04. It depends
  only on the baseline.
- The thin authenticated packaged Spring proof, a G0 requirement, is taken from
  GA-13 into M1 as two tasks; the rest of GA-13 stays in M2.
- Design-only steps (async ownership, lazy boundary) are folded into the tasks that
  implement them.
- The existing products are not the starting point. The pilots Luca chose on
  21 November 2026 stay at M6-06, M7-06, M7-07 and M8-05, after the bench is green
  for the features they need. The Stack Sensei tasks are separate implementations that do not modify the
  existing frontend.

## Language and developer experience

Requested by Luca on 21 November 2026. These follow the parity track; each is one
change with compiler or editor tests, a bench case written in the new form, and
the old form kept working. Order and milestone are set at a later review.

| Task | Change | Notes | Status |
| --- | --- | --- | --- |
| DX-01 | **Components without the class envelope.** A `.jx` file may declare `public static View Name(...)` at top level; the compiler generates the `@Component` class, its `JX` descriptor and, from the parameters, the props record | To be implemented in the `.jx` compiler, so it does not depend on Java's compact source files. To settle in the task: one component per file or several, how props are declared (parameters or a record), and how Java code refers to the generated class | ⬜ planned |
| DX-02 | **Object-literal syntax in markup.** `style={{color: "rgb(255, 0, 0)", opacity: 0.5}}` lowers to the typed `Style` API; the same literal form is considered for other typed values | String values need quotes: an unquoted `rgb(255, 0, 0)` cannot be told apart from a Java call. Keys are checked against the target type; an unknown key is a compile error at the `.jx` position | ⬜ planned |
| DX-03 | **`var` and static imports as the documented style.** `var right = useState(0);` with `import static jx.Hooks.*` | Already compiles today (checked on a bench case on 21 November 2026). The task is to cover it with compiler and bench tests, confirm the hook-placement checks and both editors handle unqualified hook calls, and switch the examples and guide to this form | ⬜ planned |
| DX-04 | **`console.log`.** A `jx.browser.Console` API whose static import gives `console.log(...)`, `warn`, `error` and `debug` in ordinary Java | `import static jx.browser.Console.console;` makes `console.log("x")` valid Java with no language change. Values are formatted on the Java side; the bench keeps its own log for assertions | ⬜ planned |
| DX-05 | **Documentation on hover in IntelliJ, then VS Code.** Hovering a component tag shows the component's Javadoc and its props with types; hovering a prop shows the record component's documentation; hovering an HTML attribute or event such as `onClick` shows its meaning and the handler type; hovering inside Java expressions keeps the native Java documentation | Component and prop documentation comes from the same compiler metadata that completion uses. Attribute and event documentation needs the DOM schema planned for the parity track (R-25), so that part lands with or after it | ⬜ planned |

## Test tooling

Requested by Luca on 21 November 2026.

| Task | Change | Notes | Status |
| --- | --- | --- | --- |
| TT-01 | **Cherry Test configuration.** A `cherry-test.yaml` at the repository root so one run covers unit tests (Maven modules), integration tests (Spring example, Maven plugin, language server), functional browser tests (bench, runtime, counter, todo, hot reload) and produces one browsable report | Uses Cherry Test's `maven` and `playwright` adapters. The Playwright configurations must write their JSON results and screenshots where the adapter expects them; that wiring is settled by trial runs in the task. Done on 22 November 2026: unit tests of five Maven modules with JaCoCo coverage and CRAP scores (baseline in the [tooling guide](tooling.md)). Open: integration and browser test kinds | 🟡 partial |
| TT-02 | **Visual tests.** Screenshot comparisons for the examples and for bench cases whose result is visual (style, SVG, hidden activity, hoisted styles), run as Cherry Test's `visual` kind, with expected, actual and difference images in the report | Baseline images are recorded per platform; the first set is macOS Chrome, and CI needs its own Linux set | ⬜ planned |
| TT-03 | **A bench gallery page.** A page that lists every bench case with links to open it on the JX side and on the React reference side, served by the existing bench server | For looking at cases by hand; it adds no assertions | ⬜ planned |
| TT-04 | **PIT mutation testing through Cherry Test.** Changed Java files are mutated by PIT on every Cherry Test run, with the result in the same report | The installed Cherry Test 0.3.16 has no built-in PIT; it needs the newer Cherry Test. Updating the installed tool is Luca's decision. After the update, check that the existing configuration starts PIT and record the first score | ⛔ blocked on the Cherry Test update |
| TT-05 | **Cherry Test without a `mvn` on the `PATH`.** A plain `cherry-test` run works in a fresh clone | Cherry Test looks for `mvnw` in each module's directory, and JX has one wrapper at the root. Either Cherry Test looks for the wrapper in parent directories (a change in Cherry Test), or the JX setup documents and checks an installed Maven | ⬜ planned |
| TT-06 | **Quality thresholds in the gate.** A run fails when coverage of changed code, a CRAP score or the PIT score is worse than an agreed limit | Today the figures are reported and nothing fails. Needs agreed limits first: a CRAP limit for new or changed methods, a coverage floor for changed lines, and a PIT threshold once TT-04 runs. Browser-side code is measured by the bench, not by these figures | ⬜ planned; limits to be agreed |
