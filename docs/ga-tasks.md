# GA implementation sequence

Revision 11, 24 November 2026. Parent: [GA roadmap](ga-roadmap.md). This file turns
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
Verify, establishing 38 executed pair witnesses. The accepted portal bench passes
779 checks, while 5,318 current pairs remain without complete evidence. Its scoped
`portal-context-stale` mutant was killed. The Promise observer prerequisite is in
review, with nine Promise tests, 36 runtime-package JVM tests and 13 Chrome runtime
checks passing locally; its full local gate and CI Verify remain pending. It adds
no pair witness. R-27 remains partial; its slice status and the
[interaction evidence record](ga-react-interactions.md) distinguish local results
from outstanding acceptance.
M1-26 (protected data route) is the
remaining server-render proof. See the [dated status review](status.md#progress-review--23-november-2026)
for remaining platform work and evidence limits. G0 has not passed.

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
| R-27 | M6 | Combined scenarios so that every pair of features meets at least once; split into R-27-01 onward below | adds pair coverage | 🟡 partial: R-27-02c merged with evidence for 38 / 5,356 active pairs; 5,318 remain open, plus deferred-feature pairs; Promise prerequisite in review adds no pairs |
| R-28 | M7 | Class components authored in Java: `Component` lifecycle and `PureComponent` | 2 | ⬜ planned |
| R-29 | M7 | Remaining small surface: `act` for JX tests, `captureOwnerStack`, version constants, `unstable_batchedUpdates`, `unstable_useCacheRefresh` | 9 | ⬜ planned |
| R-30 | M8–M10 | React Server Components: `cache`, `cacheSignal`, the client and server directives and the server-component protocol, behind a decision record after a feasibility proof | 4 | ⬜ planned |
| R-31 | M10 | Parity gate: zero `unsupported`, zero `untested`, only approved exclusions, mutation survivors zero, every case green in every applicable mode; all option-level limits implemented or explicitly accepted | closes the track | ⬜ planned |

Audit, 23 November 2026: R-16's primary action APIs and browser cases are delivered,
but `useFormState` has no Java API or case and remains excluded in the feature
register. Add the alias and a differential consumer case, or obtain an explicit
scope decision; an exclusion is not completion under the all-features goal.
Resolved the same day: `Hooks.useFormState` and `Hooks.useFormStateAsync` are
deprecated aliases of the action-state hooks, with the differential case
`form-state`; the feature row is `equivalent`.
Other rows marked supported have recorded limits: `useActionState` lacks permalink,
`FormStatus` lacks action, ViewTransition lacks callbacks, resource preloads lack
image source-set/sizes, and external stores lack server snapshots (R-21). Include
these in R-31 acceptance; export-name coverage alone is insufficient.

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

### R-27 implementation slices

Split before implementation on 24 November 2026 under working rule 5. The ledger
contains 104 supported/equivalent feature IDs, giving 5,356 candidate unordered
pairs. Excluding only the two permanent non-API rows (`react-dom:browser` and
`framework:react-compiler`), the eventual universe is 118 features / 6,903 pairs;
1,547 additional pairs depend on the 14 deferred features. Alias rows remain
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
| R-27-02c | Initially closed portal: context, capture/bubble, propagation, payloads and focus after hydration | ✅ merged PR #41 at a0f5460: 779 full bench and 8 focused checks pass; six new pairs; scoped mutant killed; green Verify |
| R-27-03 | Extend async, form action, optimistic, transition and external-store interactions, including controlled failure/recovery; delivery slices below | ⬜ planned |
| R-27-03-PROMISE | Prerequisite: isolate Promise observer delivery so a throwing observer cannot leave React's native thenable pending | 🟡 review: JVM and Chrome failures reproduced before repair; nine Promise tests, 36 runtime-package JVM tests and 13 Chrome runtime checks pass locally; full local gate and CI Verify pending |
| R-27-03a | Combined form actions with submission state, reset and controlled failure/recovery | ⬜ planned |
| R-27-03b | Optimistic state over immutable external snapshots: remote rebase, rejected-request rollback and retry without duplicate commits | ⬜ planned |
| R-27-03c | Transition interactions with independent updates and controlled asynchronous completion | ⬜ planned |
| R-27-04 | Extend SSR, hydration, streaming and resource interactions with mode-specific assertions | ⬜ planned |
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
(1 killed, 0 survived, 0 unusable). [PR #41](https://github.com/lmrrcc/jx/pull/41)
merged as `a0f5460980c6b288f3ac2fa2c4c73c0b774f0563`, with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45461059543) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45460586560).
The catalog now has 107 entries. No fresh full mutation
campaign, human qualification or cross-browser qualification is implied. Details
are in the [portal evidence record](ga-react-interactions.md#r-27-02c-portal-lifecycle-context-and-events).

R-27-03 is split into 03a actions, 03b optimistic/store behavior and 03c transition
interactions before implementation. Each slice must select explicit remaining
pair IDs and demonstrate them on both implementations; staged fixture preparation
does not close a task or earn interaction credit.

R-27-03-PROMISE, code revision `7ed3f2c`, repairs a defect found while preparing
those scenarios. Previously,
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
This prerequisite is in review with the full local push gate and CI Verify still
pending. It adds no feature-pair witness; R-27-03a, 03b and 03c remain planned.

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
