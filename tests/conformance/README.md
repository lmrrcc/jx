# React conformance bench

Each case is a focused or combined React behavior, implemented twice and checked by one spec:

| Part | Location |
| --- | --- |
| Reference in plain React | `reference/cases/<case-id>.jsx`, registered in `reference/cases.jsx` |
| JX implementation | `src/main/jx/jx/conformance/cases/`, registered in `src/main/java/jx/conformance/Main.java` |
| Shared spec | `specs/*.spec.js`, run against both implementations in each applicable rendering mode |

Rules:

- A case mounts alone on a fresh page: `?case=<case-id>`, under Strict Mode unless
  it explicitly tests a different root policy. Cases share no application state
  or external backend. The lazy-mode tests observe and control local chunk requests.
- Both implementations expose the same observable contract: roles, accessible
  names, `data-*` attributes, and entries appended to the page's `__benchLog`.
  Behavior specs assert on that contract. Separate lazy-loading checks also inspect
  the build graph and source maps to establish that case code is absent initially.
- The reference must pass. A spec that fails on the reference is a wrong spec.
- Any uncaught page error or console error fails the test on either side, except
  when the scenario explicitly expects error reports, such as download failure
  or the root's uncaught-error policy.
- The reference files are framework-owned JavaScript fixtures. Application logic
  in JX stays in Java and `.jx`.

The run prints every test and ends with one row per case showing reference and JX
results for all four modes.

Run from the repository root after the Maven build:

```sh
npm run test:bench
```

R-29 adds `runtime-utilities` (loaded version exports, owner stacks, legacy batching),
`server-runtime-versions` and the root-owning `act-utilities` Java consumer.
`runtime-utilities-production` loads separately bundled production React packages;
it does not replace exports or simulate production. `npm run build:bench-production`
builds those assets and is included in the full bench command. The `act` host sets
and restores `IS_REACT_ACT_ENVIRONMENT` explicitly. Its assertions inspect snapshots
taken when `act` resolves, including a second update scheduled by an effect, so
browser polling cannot mask an early completion. The focused run on 25 November
passes 14 checks with 18 inapplicable cells: client 4/4 per side and one case per
side in each other mode. The integrated full run records 847 passes and 169
inapplicable cells across 127 case IDs: client 125 reference / 124 JX, hydrate 94,
static 100 and lazy 105 per side on merged class-authoring base `3e1db97` (code `52eb29e`).
All 54 interaction witnesses pass among 6,328 active candidate pairs; utility
cases add no automatic pair credit. Four scoped utility mutants were killed after
the unmutated baseline passed, with no survivors or unusable mutants. The utility
slice merged as PR #52 (`8fdbed0`) after the 15-step gate and both Verify checks
passed on `f0b5a5b`. The production case checks the
esbuild input manifests for actual production React modules and no development
modules, as well as the browser's version values. Deep pure-Java
owner chains remain an explicit R-29-04/R-31 diagnostic gap; the owner-name witness
uses a real native React owner creating the Java consumer.

## Rendering modes

| Mode | Playwright projects | Check |
| --- | --- | --- |
| Client | `reference`, `jx` | Mount the case in the browser and run its behavior spec |
| Hydrate | `reference-hydrate`, `jx-hydrate` | Render the same implementation in Node, hydrate that HTML, then run the behavior spec; hydration mismatch reports fail the test |
| Static HTML | `reference-static`, `jx-static` | Render without browser scripts; JX HTML must equal React HTML character for character |
| Lazy route | `reference-lazy`, `jx-lazy` | Open an initially unloaded route through a user event, await its committed render, then run the behavior spec |

`modes.tsv` records each case/mode exclusion and its reason. The lazy mode excludes
`hydrate-root`, `hydrate-server` and `root-options`, which construct their own roots,
and `unknown-case`, which has no component to load. Node-only server cases run in
the client projects only. The two download scenarios in `specs/lazy.spec.js` run
only in the lazy projects; these structural differences appear as skipped cells,
not unsupported features. `strict-nested` keeps its intentionally non-Strict host.

### Lazy host and case boundary

JX builds two independent TeaVM programs: `LazyHost` contains the activation UI,
while `LazyRoute` contains `Main`'s registry and all case implementations. The
reference has the same split in `reference/lazy-host.jsx` and `lazy-route.jsx`.
Each side's two entry points are bundled together, sharing one React runtime.
This proves loading the case bundle on demand; it does not split each individual
case into its own chunk or add Maven plugin support for arbitrary lazy modules.
See [ADR 0005](../../docs/decisions/0005-lazy-module-boundary.md) for the serialized
Java-runtime boundary. The bench passes empty props; a case and its contexts stay
inside the loaded runtime.

`lazy-route-loading` follows every static output import in the esbuild metadata,
including shared chunks: no case input may be reachable from `host.js`, and case
code must be reachable from `route.js`. Since esbuild sees a TeaVM compilation as
one input, it also reads TeaVM's source maps. The host may contain only
`LazyHost.java`, `LazyHostPage.java` and `Bench.java` from `jx.conformance`; the
route must include the registry and `cases/StateCounter.java`.

The browser check sees zero route requests before activation, holds the first
request to prove the loading UI appears without a committed case, then releases
it and exercises the counter. `lazy-route-retry` aborts the first download, checks
the error UI, and requires an explicit retry to issue a second real request.
The recovered counter works and the original root container remains connected.
Generated DOM element, attribute and event comparisons open the reference in the
same mode, including its lazy host or its Node-rendered HTML for hydration.

The route normally has an error boundary. `error-reporting` deliberately omits
that boundary on both sides: its render error must reach the root and unmount it,
as its existing spec requires. This is an explicit scenario policy, not an ignored
failure.

## Features JX does not have yet

A case for an unsupported feature is declared with `referenceOnly(...)`, or with
`serverOnly(...)` for React's server APIs, which run in Node from
`reference/server.jsx`. The reference must pass. On the JX side the test asserts
that the case is still absent and the summary shows `unsupported`. Implementing
the feature means adding the JX case, changing the declaration to `test(...)` and
updating the feature list; the checker rejects a mismatch between the two.

## Recorded results

Every full run compares its case table with `docs/evidence/conformance-results.tsv`
and its executed pair witnesses with `docs/evidence/interaction-results.tsv`.
It fails on any difference, in either direction. After an intended change, rerun with
`BENCH_UPDATE=1 npm run test:bench` and review the file's diff.
Only a full run can update that table: a filtered run leaves it untouched, and
combining a partial run with `BENCH_UPDATE=1` fails. `npm run test:bench` also sets
`BENCH_REQUIRE_COMPLETE=1`: losing a project or a recorded case fails Verify.
For focused development checks use `npx playwright test -c
tests/conformance/playwright.config.js --project=jx-lazy -g state-counter` after
building the bench; this does not replace full-matrix evidence. Consult the recorded
table for counts and the [task record](../../docs/ga-tasks.md#react-parity-track)
for R-26's accepted try-it and Verify results.
The bench caps concurrency at two workers because each may run both Chrome and
a Node-hosted TeaVM application. This avoids tying memory pressure to CPU count;
test timeouts and assertions remain unchanged.

## Interaction evidence

R-27-01 adds `interactions.tsv`: one named witness links two feature IDs, a case,
applicable modes and an observable interaction. Specs wrap the relevant assertions
in `await interaction(id, async () => { ... })`. The helper emits its annotation
only after that assertion step succeeds; a later case failure cancels its credit.
The reporter rejects missing or misplaced markers. Features merely appearing in
the same case earn no pair evidence.

The integrated class-context run over PR #52 passes 855 checks, with 169 skipped
cells across 128 case IDs: client 126 reference / 125 JX, hydrate 95 per side,
static 101 per side and lazy 106 per side. Fifty-four pairs have complete local
evidence in their declared modes; 6,274 of the 6,328 current candidates remain
open. The active universe includes 113 features after eight utility rows become
available. The eventual universe still includes 118 features and 6,903 candidate
pairs, including 575 deferred-feature pairs. **R-27 remains partial.** See the
[interaction evidence record](../../docs/ga-react-interactions.md) for the two
initial combined scenarios, children-composition regressions, seven ref pairs,
six portal pairs, four action pairs, three optimistic/store pairs, three transition
pairs, four server/resource pairs, two PureComponent key/ref pairs and acceptance
status. R-27-02a merged in [PR #39](https://github.com/lmrrcc/jx/pull/39)
at `413558b636679dd5926f4d7bf1c987ea707ed95c`, after its 755-check bench and green
Verify. R-27-02b merged in [PR #40](https://github.com/lmrrcc/jx/pull/40) at
`15c0c6eee36f506910e854c568547ebc6d3758ea`, with green
[CI](https://github.com/lmrrcc/jx/actions/runs/45452120669) and
[CI](https://github.com/lmrrcc/jx/actions/runs/45452801956).
R-27-02c's scoped mutant was killed; [PR #41](https://github.com/lmrrcc/jx/pull/41)
merged as `a0f5460` with green Verify. R-27-03a's scoped mutant was killed after
its unmutated baseline passed; [PR #43](https://github.com/lmrrcc/jx/pull/43)
merged as `1ba8c1d` with green Verify. R-27-03b's scoped mutant was killed after
its unmutated baseline passed; [PR #44](https://github.com/lmrrcc/jx/pull/44)
merged as `3e24edb` with both Verify checks green. R-27-03c's scoped mutant
was killed after its shared scratch baseline passed; [PR #45](https://github.com/lmrrcc/jx/pull/45)
merged as `812a27a` after its full local gate and both Verify checks passed.
SSR [PR #47](https://github.com/lmrrcc/jx/pull/47) merged as `7759933` with
[green push CI](https://github.com/lmrrcc/jx/actions/runs/45555926188) and
[green PR CI](https://github.com/lmrrcc/jx/actions/runs/45556004135).
Browser evidence is Chrome-only and
does not establish additional human or cross-browser qualification.

The ref scenarios use actual Playwright element handles to compare DOM identity,
real imperative-handle identity, and input value/focus operations. They cover
callback stability, layout attachment, Strict Mode cleanup, keyed removal,
current captured props and switching ref ownership. The portal scenario passes
all eight focused checks; its actual ElementHandles prove draft/node retention
across context updates and a fresh node after closing and reopening. It checks
capture/bubble order, target/currentTarget and default/propagation semantics,
focus and ref cleanup. The portal begins closed in the server HTML and opens only
after browser hydration; this does not prove server rendering of an open portal.
The action scenario also passes all eight focused checks. Its four witnesses
combine action state with form actions, form status, Java async functions and the
existing error-boundary equivalent. It checks captured submission payloads,
sequential previous-state composition, pending status and reset, rejection that
cancels successors, retry while pending and unmount/remount with a fresh owner.
The cancellation behavior is fixture-local, not the G0 API; this action fixture
does not qualify R-28 class behavior. R-27-03a, R-27-03b optimistic/store and
R-27-03c transition are merged.

The optimistic/store scenario passes all eight focused checks. Its three witnesses
combine optimistic state with external-store snapshots, transitions and Java async
functions. A remote publication rebases the pending overlay; rejection removes
only the local addition. Retry commits one local item while a second await keeps
the action pending, exposing duplicate replay. Closing during either await settles
the fixture's gates and releases its actual request and subscription; a fresh page
starts independently. Explicit fixture disposal does not qualify automatic
framework cancellation or complete the G0 API. Static mode compares initial HTML
without executing these interaction assertions.

The transition scenario also passes all eight focused checks. Its three witnesses
cover urgent controlled input, ref-owned operations and async effect disposal.
Replaced and canceled local consumers settle while their transports remain
pending; unmount/remount prevents old work from updating a fresh instance.
An ordered race successfully settles the old consumer, replaces its owner before
its continuation, and verifies the visible result after the old transition ends.
The replacement joins a separate transition only afterward, preventing action
entanglement from hiding an incorrectly published result. This is fixture-local
ownership, not a framework cancellation API. The existing async/transition pair
is not counted again.

The separate R-27-03-PROMISE prerequisite merged in
[PR #42](https://github.com/lmrrcc/jx/pull/42) as `f2b64cb`. A throwing
Promise observer previously prevented React's later bridge observer from receiving
settlement, leaving its reader suspended. JVM and Chrome regressions reproduced
the defect; nine Promise JVM tests (included in 36 runtime-package JVM tests) and
13 Chrome runtime checks pass, together with the full local gate and CI Verify.
These [runtime checks](../../docs/runtime.md#promise-observer-isolation) added no
case or feature-pair credit to the portal baseline of 779 passes and 38 pairs.

The `class-runtime` and `class-keyed` cases compare typed Java peers with actual
native `React.Component` and `React.PureComponent` instances. They check queued
functional updates, omitted versus null fields, skipped-render state access,
commit snapshots/callbacks, forceUpdate, null output without unmounting, and
native shallow comparison. Keyed Pure peers retain their class refs, DOM nodes
and state through both reorder directions without rendering unchanged props.
Both cases pass all eight projects; the two pair witnesses run only in client,
hydrate and lazy modes. This runtime slice uses explicit descriptors and Java
`Views`; direct `.jx` authoring is covered by the following compiler slice.
Native editor semantics and advanced lifecycle/context options remain open.
Hot replacement resets class state; compatible typed class
checkpoints are deferred. Three class mutants were killed after the isolated
baseline passed. [PR #48](https://github.com/lmrrcc/jx/pull/48) merged as `ccfcb2d`
after the local 14-step gate and both Verify checks passed. See
[ADR 0006](../../docs/decisions/0006-java-class-components.md).

The `class-authoring` case compiles annotated native classes from `.jx` through
generated typed schemas, partial patches and lifecycle descriptors. Its shared
spec checks queued updates and render snapshots, omitted versus null state,
pre-commit DOM snapshots and callback order, Pure bailout, class refs, remount
and unmount. It adds no interaction-pair credit. Compiler JVM tests compile the
real core API, and LSP protocol tests cover class/prop navigation and typed ref
completion. All eight focused projects pass, and the complete integrated run
passes 833 checks with 151 skipped across 123 cases. The authored subset accepts
direct, non-generic classes and nested record props/state; supported field types
and remaining syntax limits are in ADR 0006. The generated-snapshot mutant was
killed after the unmutated baseline passed. PR #50 merged as `3e1db97` after the
15-step local gate and both Verify checks passed. Native editor qualification
remains a separate acceptance task.

The separate `class-context` consumer exercises explicit typed native
`contextType` factories. Its eight focused projects pass: constructor/default/
nested/null values and provider identity, context changes through false SCU and
Pure bailouts, and real DOM/state retention across Activity hide/show. It retains
the actual instance in the hide event and updates it while hidden, including in
hydration mode; it does not assume a parent mount effect sees a deferred hydrated
child ref. Cleanup reads current context, and an intentionally retained instance
keeps native React's last context after unmount. This is focused Chrome evidence,
not a disposal guarantee or full class lifecycle parity. Static mode compares
initial HTML only. The complete integrated run over `8fdbed0` passes 855 checks,
169 skipped, across 128 cases, including all 32 cells for the four class cases.
Case evidence adds only `class-context`; pair evidence remains 54/6328.
`class-context-captured` was killed after a passing unmutated baseline:
1 killed, 0 survived, 0 unusable. Runtime and fixture source at `6c3aa63` is
identical after the rebase (`d897468`). The catalog has 121 entries, while the
historical full campaign remains 100. Run mutation campaigns exclusively:

```sh
java tools/MutateBench.java --only class-context-captured --keep
```

The final push gate and Verify are pending; the browser evidence is Chrome-only.

Static HTML equality does not execute interactive assertion blocks, so it does
not credit those witnesses. Hydration-owned and Node-only scenarios can carry
interaction evidence in the client projects without implying other mode coverage.
`npm run test:bench` runs the reporter regression checks and the full matrix.
Focused runs never replace complete case or interaction tables.

## Mutation check

`tests/conformance/mutants.tsv` lists deliberate defects: a file, the exact text to
find, its replacement and what breaks. `java tools/MutateBench.java` applies each
one in a scratch copy, rebuilds and runs all applicable JX rendering modes. A mutant that
the bench does not catch is a missing case. Results go to
`docs/evidence/mutation-results.tsv` for an unfiltered run. Run mutation checks
exclusively, without concurrent Maven builds or browser suites: a separate port
does not prevent CPU and memory contention from causing misleading timeouts.

R-26 adds two curated defects: `lazy-route-eager` activates the route before the
user, and `lazy-host-includes-cases` pulls the case registry into the initial TeaVM
program. To check just those defects after the unmutated baseline:

```sh
java tools/MutateBench.java --only lazy-route-eager,lazy-host-includes-cases
```

The runner checks its unmutated scratch build first. A scoped run reports only
the selected mutants and does not rewrite the full mutation evidence table.
Adding these two entries does not establish a new complete 102-mutant result.

R-27-01 adds `combined-unstable-key` and `combined-store-leak`, bringing the catalog
to 104 entries. Repeat their scoped check with:

```sh
java tools/MutateBench.java --only combined-unstable-key,combined-store-leak
```

The unmutated scratch baseline passed, then both mutants were killed by
`combined-keyed-context` and `combined-activity-store`, respectively, with no
survivors or unusable mutants. The historical full result still covers 100
mutants; the R-26 and R-27-01 scoped runs do not constitute a fresh full
104-mutant campaign.

R-27-02a added `children-source-key-lost` and adapted the existing children and
Strict Mode mutations. Its unmutated baseline passed and all three scoped
mutants were killed, bringing that slice's catalog to 105 entries.
R-27-02b adds `imperative-stale-props`; the unmutated baseline passed, then
`combined-imperative-handles` killed it: **1 killed, 0 survived, 0 unusable**.
Repeat its check exclusively:

```sh
java tools/MutateBench.java --only imperative-stale-props
```

R-27-02c adds `portal-context-stale`; its unmutated scratch baseline passed, then
`combined-portal-lifecycle` killed it: **1 killed, 0 survived, 0 unusable**. Repeat
the scoped check exclusively:

```sh
java tools/MutateBench.java --only portal-context-stale
```

R-27-03a adds `action-queue-previous-lost`, which discards the previous action
state when accumulating a queued submission. Its unmutated scratch baseline
passed, then `combined-action-queue` killed it: **1 killed, 0 survived,
0 unusable**. Repeat it exclusively:

```sh
java tools/MutateBench.java --only action-queue-previous-lost
```

R-27-03b adds `optimistic-duplicate-overlay`, which lets the pending reducer append
an optimistic copy of an item already committed to the external snapshot. Its
unmutated baseline passed, then `combined-optimistic-store` killed it while the
second completion gate was still pending: **1 killed, 0 survived, 0 unusable**.
Repeat it exclusively:

```sh
java tools/MutateBench.java --only optimistic-duplicate-overlay
```

R-27-03c adds `transition-owner-guard-lost`, which removes the owner check before
publication while retaining the canceled-outcome check. After the unmutated
scratch baseline passed, `combined-transition-cancel` killed it: **1 killed,
0 survived, 0 unusable**. This came from the exclusive shared transition/SSR run
at `abfb24a`, scratch `jx-mutation-1181316345297598843`; its transition source and
spec are identical to rebased `d89e23f`. The shared catalog has 112 entries because
it includes later SSR cases. Repeat just this transition check exclusively:

```sh
java tools/MutateBench.java --only transition-owner-guard-lost
```

R-27-04 adds `stream-writable-waits-for-all` and `stream-fatal-all-pending`, and
reuses `hint-preload-dropped` against the new resource-hydration case. All three
were killed after the unmutated baseline passed. The same exclusive run at
`abfb24a` included the transition mutant: **4 killed, 0 survived, 0 unusable**.
The full bench has passed with the new cases;
`test:ssr` now includes all streaming, prerender, resume and hydration checks.
See the [server interaction evidence](../../docs/ga-react-interactions.md#r-27-04-streaming-resource-hydration-and-fatal-readiness)
for exact assertions and remaining destination-failure limits.

R-28-01 adds `class-pure-renders`, `class-snapshot-lost` and
`class-ref-detach-lost`. After the unmutated baseline passed, the exclusive run
killed all three: the Pure mutation fails `class-keyed` and `class-runtime`,
while the snapshot and ref-detachment mutations fail `class-runtime`.
Result: **3 killed, 0 survived, 0 unusable**. Repeat the scoped campaign alone:

```sh
java tools/MutateBench.java --only class-pure-renders,class-snapshot-lost,class-ref-detach-lost
```

R-28-02a registers `class-authoring-snapshot-lost`, which removes the authored
snapshot lifecycle from the generated descriptor. After the unmutated baseline
passed, `class-authoring` killed the mutant: **1 killed, 0 survived, 0 unusable**.
Repeat in isolation:

```sh
java tools/MutateBench.java --only class-authoring-snapshot-lost
```

The current catalog has **116 entries**; the historical full result
still covers 100. Scoped campaigns do not establish a fresh full-catalog result.

## Feature list

`react-exports.txt` is generated from the installed packages by
`node tests/conformance/inventory.mjs --write`; a bench test fails when it is out
of date. `docs/evidence/conformance.tsv` gives every export, and every markup and
DOM feature that is not an export, one JX state:

| State | Meaning |
| --- | --- |
| `supported` | Same API shape in JX; the listed bench cases pass |
| `equivalent` | Different shape, same outcome; the listed bench cases pass |
| `untested` | Believed to work in JX, but no bench case proves it |
| `unsupported` | JX cannot do it yet |
| `excluded` | Deliberately out of scope, with the reason |

`java tools/CheckBaseline.java` fails when an export has no row, when a claimed
feature names no existing case, or when a case is listed against no feature.

To add a case: write the reference component, write the JX component, register
both under the same id, and add one test that opens the id with the `open` fixture
from `specs/bench.js`.
