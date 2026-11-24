# React conformance bench

Each case is one React behavior, implemented twice and checked by one spec:

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

Every full run compares its table with `docs/evidence/conformance-results.tsv` and
fails on any difference, in either direction. After an intended change, rerun with
`BENCH_UPDATE=1 npm run test:bench` and review the file's diff.
Only a full run can update that table: a filtered run leaves it untouched, and
combining a partial run with `BENCH_UPDATE=1` fails. `npm run test:bench` also sets
`BENCH_REQUIRE_COMPLETE=1`: losing a project or a recorded case fails Verify.
For focused development checks use `npx playwright test -c
tests/conformance/playwright.config.js --project=jx-lazy -g state-counter` after
building the bench; this does not replace full-matrix evidence. Consult the recorded table for
counts; implementation and local results do not replace Luca's try-it review and
the green Verify workflow required to accept R-26.
The bench caps concurrency at two workers because each may run both Chrome and
a Node-hosted TeaVM application. This avoids tying memory pressure to CPU count;
test timeouts and assertions remain unchanged.

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
