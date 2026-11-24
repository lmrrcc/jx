# React conformance bench

Each case is one React behavior, implemented twice and checked by one spec:

| Part | Location |
| --- | --- |
| Reference in plain React | `reference/cases/<case-id>.jsx`, registered in `reference/entry.jsx` |
| JX implementation | `src/main/jx/jx/conformance/cases/`, registered in `src/main/java/jx/conformance/Main.java` |
| Shared spec | `specs/*.spec.js`, run by Playwright as the `reference` and `jx` projects |

Rules:

- A case mounts alone under Strict Mode on a fresh page: `?case=<case-id>`. It uses
  no network and no state shared with another case.
- Both implementations expose the same observable contract: roles, accessible
  names, `data-*` attributes, and entries appended to the page's `__benchLog`.
  Specs assert on that contract only, never on implementation details.
- The reference must pass. A spec that fails on the reference is a wrong spec.
- Any uncaught page error or console error fails the test on either side.
- The reference files are framework-owned JavaScript fixtures. Application logic
  in JX stays in Java and `.jx`.

The run prints every test and ends with one row per case showing the reference
and JX result.

Run from the repository root after the Maven build:

```sh
npm run test:bench
```

## Rendering modes

Every case also runs in **hydrate mode** (projects `reference-hydrate` and
`jx-hydrate`): the same implementation renders the case to HTML in Node, the page
hydrates that markup, and the unchanged spec runs against it. A hydration mismatch
is a console error and fails the test. A case that does not apply to a mode is
listed in `modes.tsv` with the reason, decided by what React itself does there;
server cases have no page and run in client mode only.

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

## Mutation check

`tests/conformance/mutants.tsv` lists deliberate defects: a file, the exact text to
find, its replacement and what breaks. `java tools/MutateBench.java` applies each
one in a scratch copy, rebuilds and runs the JX side of the bench. A mutant that
the bench does not catch is a missing case. Results go to
`docs/evidence/mutation-results.tsv`. The run takes about half a minute per mutant
and uses its own port, so the bench can run beside it.

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
