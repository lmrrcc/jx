# GA baseline record

Evidence for [GA-01](../ga-01-baseline.md). This is a dated record of one run, not
a standing claim. Declared versions are in the [version manifest](versions.tsv);
capability status is in the [capability ledger](capabilities.tsv).

## Revision

- Base commit: `78c9836` on `main`.
- Plus uncommitted GA-01 changes: the restored counter example, the GA planning
  documents, this evidence directory, `tools/CheckBaseline.java`, and the workflow
  and documentation updates. They were committed as `3688d25`, which the ledger rows
  run on this tree now name.
- Date: 2026-11-21.

## Finding: the named baseline was red

Commit `78c9836` failed the Verify workflow
([run 44489447288](https://github.com/lmrrcc/jx/actions/runs/44489447288)) at
`npm run test:counter`; `test:todo` and `test:hot` did not run. Reproduced locally:
Maven `install` passed, then the counter test failed because two regions were named
"Second counter". The commit had left development edits in the counter example's
`App.jx`: a second `Counter` with the duplicate key `first`, a third counter, a
second toggle and an "Updated!" heading. The example is restored to its previous
content; the browser test is unchanged and passes.

The previous commit `61f5bd7` passed Verify
([run 44307093813](https://github.com/lmrrcc/jx/actions/runs/44307093813)).

## Environment of the local run

| Item | Value |
| --- | --- |
| Machine | Apple M5, 10 cores, 16 GiB, macOS 26.5.1 (25F80) |
| JDK | Corretto 25.0.4.1 |
| Node.js / npm | 20.20.2 / 10.8.2 |
| Browser | Google Chrome 157.0.8103.80 through the Playwright `chrome` channel |
| Maven | 3.9.11 through the wrapper; the distribution was already in `~/.m2/wrapper` |

Node.js 20 satisfies the root `package.json` engine but is older than the
documented "22 or later". CI uses Node.js 22.

Versions resolved by the Spring Boot 4.1.2 BOM in the todo API, observed in the
populated Maven repository: H2 2.4.240, Flyway 12.4.0, JUnit Jupiter 6.0.3.

## Clean-directory run

Tracked and new unignored files were exported with
`git ls-files -co --exclude-standard` into an empty directory. It had no
`node_modules`, no `target` directories and no Maven repository; `npm ci` and Maven
fetched everything (37 MiB and 167 MiB). The npm download cache and the Maven
wrapper distribution in the home directory were reused, so this is not a
network-cold or new-machine measurement.

| Command | Result | Seconds |
| --- | --- | --- |
| `java tools/CheckAgentSetup.java` | Failed once: this file was linked but not yet written. Passes on the final tree, see below | 0 |
| `java tools/CheckBaseline.java` | Pass: 46 pins, 48 repeated literals, 41 capability rows | 1 |
| `npm ci` | Pass | 1 |
| `./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository install` | Pass: 13 modules, 15 test classes, 74 tests, 0 failures | 41 |
| `npm run build:proof` | Pass | 0 |
| `npm run test:proof` | Pass: 1 test | 2 |
| `npm run build:runtime` | Pass | 0 |
| `npm run test:runtime` | Pass: 10 tests | 4 |
| `npm run test:counter` | Pass: 1 test | 5 |
| `npm run test:todo` | Pass: 3 tests | 4 |
| `npm run test:hot` | Pass: 2 tests | 42 |

Totals: 74 JVM tests and 17 Chrome tests passed. Times are single wall-clock
observations with other applications open; they are not benchmarks.

Final tree, after this record was written: `java tools/CheckAgentSetup.java` and
`java tools/CheckBaseline.java` both pass.

## Checker failure cases

Each change was applied to a scratch copy; every run exited 1 with a `FAIL:` line
naming the row or file. A bad option exits 2 with the usage line.

| Change | Reported |
| --- | --- |
| `teavm.version` changed in `pom.xml` | Manifest row `teavm` differs from its source |
| IntelliJ ZIP name changed in `tooling.yml` | Repeated literal disagrees with `build.gradle.kts` |
| React version changed in `package-lock.json` | Lockfile disagrees with `package.json` |
| Ledger status `done` | Unknown status |
| Ledger fixture path renamed | Fixture does not exist |
| Ledger command `npm run test:nothing` | Unknown command |
| Ledger row `qualified` with no CI link | Qualified without a CI link |
| Short ledger row appended | Wrong column count |
| Manifest deleted | Missing manifest |

## CI status

| Workflow | Status |
| --- | --- |
| Verify, `78c9836` | Failed, as described above |
| Verify, `3688d25` (GA-01 changes) | Passed on Ubuntu, Temurin 25, Node.js 22: [run 44891179058](https://github.com/lmrrcc/jx/actions/runs/44891179058) |
| Package and verify editors | Never executed; manual trigger only |

## Not run for this record

- IntelliJ plugin tests and Plugin Verifier, VS Code UI harness and native probe.
  Their last recorded results are in the [tooling guide](../tooling.md); the two
  editor ledger rows keep those earlier revisions and dates.
- Linux and Windows locally; Firefox and WebKit anywhere.

## Gaps recorded

Version and build inputs:

1. Node.js is inconsistent: root engine `>=20`, VS Code package `>=22`, documentation
   "22 or later", Verify uses 22, the editor workflow uses 26.3.0. No `.nvmrc`.
2. The JDK patch version and distribution are not pinned (Temurin in CI, Corretto locally).
3. The Maven wrapper has no `distributionSha256Sum`; the Gradle wrapper has one.
4. Chrome comes from the installed channel and is not pinned.
5. JUnit Jupiter is 5.13.4 in framework modules and 6.0.3 through the Boot BOM in the
   todo API. H2 and Flyway are also BOM-resolved, not declared.
6. Several version literals are copied by hand across build files, scripts and
   workflows. `CheckBaseline` now detects disagreement; there is still no single
   definition.

CI:

7. One operating system (Ubuntu) and one browser (Chrome). macOS evidence is local only.
8. Editor suites never run in CI. The editor workflow is manual and has no run.
9. Before this change Verify had no timeout and kept no browser artifacts on failure.
   Both are added; the workflow passed with them, so the failure-only upload was
   skipped and remains unexercised.
10. No dependency caching for Maven in Verify.

Evidence:

11. The ledger maps capabilities to test files, not to individual assertions. Rows
    were seeded from the status, runtime and tooling documents; each row's claim was
    not re-audited against its test.
12. No capability is `qualified`. Qualification rules, budgets and the platform
    matrix belong to GA-28 and the feature work packages.
13. `docs/status.md` recorded 69 JVM tests on 2026-11-18; this run has 74. The older
    paragraph is kept as a historical measurement.
14. No performance, bundle-size or editor-latency measurement was taken here.
