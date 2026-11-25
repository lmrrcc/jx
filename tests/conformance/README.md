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

The complete R-27-03b local run passes 795 checks, with 133 skipped
cells across 116 case IDs: client 114 reference / 113 JX, hydrate 89 per side,
static 95 per side and lazy 100 per side. Forty-five pairs have complete local evidence
in their declared modes; 5,311 of the
5,356 current candidates remain open. The eventual universe includes 118 features
and 6,903 candidate pairs. **R-27 remains partial.** See the
[interaction evidence record](../../docs/ga-react-interactions.md) for the two
initial combined scenarios, children-composition regressions, seven ref pairs,
six portal pairs, four action pairs, three optimistic/store pairs and acceptance status. R-27-02a merged in [PR #39](https://github.com/lmrrcc/jx/pull/39)
at `413558b636679dd5926f4d7bf1c987ea707ed95c`, after its 755-check bench and green
Verify. R-27-02b merged in [PR #40](https://github.com/lmrrcc/jx/pull/40) at
`15c0c6eee36f506910e854c568547ebc6d3758ea`, with green
[CI](https://github.com/lmrrcc/jx/actions/runs/45452120669) and
[CI](https://github.com/lmrrcc/jx/actions/runs/45452801956).
R-27-02c's scoped mutant was killed; [PR #41](https://github.com/lmrrcc/jx/pull/41)
merged as `a0f5460` with green Verify. R-27-03a's scoped mutant was killed after
its unmutated baseline passed; [PR #43](https://github.com/lmrrcc/jx/pull/43)
merged as `1ba8c1d` with green Verify. R-27-03b's scoped mutant was killed after
its unmutated baseline passed; this slice's Verify is pending. Browser evidence is Chrome-only and
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
The cancellation behavior is fixture-local, not the G0 API; class components
remain R-28. R-27-03a is merged; R-27-03b optimistic/store is in review and
R-27-03c transition remains planned in this baseline.

The optimistic/store scenario passes all eight focused checks. Its three witnesses
combine optimistic state with external-store snapshots, transitions and Java async
functions. A remote publication rebases the pending overlay; rejection removes
only the local addition. Retry commits one local item while a second await keeps
the action pending, exposing duplicate replay. Closing during either await settles
the fixture's gates and releases its actual request and subscription; a fresh page
starts independently. Explicit fixture disposal does not qualify automatic
framework cancellation or complete the G0 API. Static mode compares initial HTML
without executing these interaction assertions.

The separate R-27-03-PROMISE prerequisite merged in
[PR #42](https://github.com/lmrrcc/jx/pull/42) as `f2b64cb`. A throwing
Promise observer previously prevented React's later bridge observer from receiving
settlement, leaving its reader suspended. JVM and Chrome regressions reproduced
the defect; nine Promise JVM tests (included in 36 runtime-package JVM tests) and
13 Chrome runtime checks pass, together with the full local gate and CI Verify.
These [runtime checks](../../docs/runtime.md#promise-observer-isolation) added no
case or feature-pair credit to the portal baseline of 779 passes and 38 pairs.

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

The current catalog has **109 entries**; the historical full result still covers 100. These
scoped campaigns do not establish a fresh full 109-mutant result.

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
