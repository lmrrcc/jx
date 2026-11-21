# GA-01 — Baseline and capability/evidence ledger

Working file for the first [execution backlog](ga-execution.md) item. Parent:
[GA roadmap](ga-roadmap.md). This file is the task list for GA-01 only; update the
checklist and the daily record as work lands. Nothing from GA-02 onward is started here.

Acceptance outcome from the backlog: *reproducible clean build, verified CI status,
exact version manifest, gaps and commands recorded.*

## Status

**Done, 2026-11-21.** Committed as `3688d25`; the Verify workflow passed
([run 44891179058](https://github.com/lmrrcc/jx/actions/runs/44891179058)). The manual editor workflow was
not run and stays a recorded gap.

## Design

| Deliverable | Location | Purpose |
| --- | --- | --- |
| Version manifest | [versions.tsv](evidence/versions.tsv) | One row per declared pin: `id`, `value`, `source`, `note`. Generated from the build files. |
| Capability ledger | [capabilities.tsv](evidence/capabilities.tsv) | One row per capability with the fields required by [GA qualification](ga-qualification.md). Hand-maintained. |
| Checker | [CheckBaseline.java](../tools/CheckBaseline.java) | Fails when a pin and the manifest differ, when hand-copied version literals disagree, or when a ledger row is malformed. |
| Baseline record | [baseline.md](evidence/baseline.md) | Environment, commands, results, CI runs and recorded gaps for this revision. |

Decisions:

- The build files stay the source of truth for versions. The manifest is their
  reviewed copy: a pin change without a manifest change fails the check, so every
  upgrade is visible in one diff. `--write-versions` regenerates it.
- Versions resolved by a BOM or a browser channel (H2, Flyway, JUnit 6 in the Spring
  example, Chrome) are not declared in this repository. They are recorded as observed
  values in the baseline record, not in the manifest.
- The ledger is seeded from the checked-in status, runtime and tooling matrices. No
  row starts as `qualified`: that status requires a CI link, and the qualification
  rules belong to later work packages.
- TSV follows the existing `agent-sources.tsv` precedent and needs no parser
  dependency in a single-file Java tool.
- Known inconsistencies are recorded as gaps. Fixing them is out of scope unless
  listed below.

## Tasks

- [x] 1. Create this file and link it from the GA-01 backlog row.
- [x] 2. Reproduce the red baseline: at `78c9836`, Maven `install` passes and
  `npm run test:counter` fails (two regions named "Second counter").
- [x] 3. Restore the counter example `App.jx` to its content before `78c9836`;
  `npm run test:counter` passes.
- [x] 4. Write the manifest half of `tools/CheckBaseline.java`; generate `versions.tsv`.
- [x] 5. Seed `capabilities.tsv`; add the ledger half of the checker.
- [x] 6. Negative checks on a scratch copy: changed pin, disagreeing copy, unknown
  status, missing fixture, unknown command and `qualified` without CI each fail.
- [x] 7. Clean-build reproduction in an empty directory with an empty Maven
  repository and no `node_modules`.
- [x] 8. Update `verify.yml`, `AGENTS.md`, `README.md` and `status.md`.
- [x] 9. Write `evidence/baseline.md` from the actual run output.
- [x] 10. Run both checkers on the final tree.
- [x] 11. Update the roadmap and the backlog row.
- [x] 12. After commit and push: Verify is green for `3688d25`; its URL is in
  `baseline.md`; ledger revisions name the commit; the backlog row is marked done.
  The optional manual editor workflow was not run.

## End-to-end test

From the repository root, with JDK 25, Node.js 22 or later, npm and Google Chrome:

```sh
java tools/CheckAgentSetup.java
java tools/CheckBaseline.java
npm ci
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository install
npm run build:proof
npm run test:proof
npm run build:runtime
npm run test:runtime
npm run test:counter
npm run test:todo
npm run test:hot
```

Expected: both checkers print `PASS`; Maven reports 74 passing tests; 17 Chrome
tests pass. To see the checker reject drift, change `teavm.version` in `pom.xml` or
a status in `capabilities.tsv`, rerun `java tools/CheckBaseline.java`, read the
`FAIL:` line naming the row, then revert the change.

## Daily record

```text
Date / plan revision: 2026-11-21 / roadmap 0.1
Current milestone and selected backlog ID: M1 / GA-01
Owner / reviewer: Luca with Claude / Luca
Observable outcome for today: green local baseline, manifest, ledger, checker, record
Starting evidence and assumptions: roadmap names 78c9836 as its evidence baseline
Changes / experiment: see Tasks 1–11
Checks actually executed: see evidence/baseline.md
Observed outcome and failure cases: see evidence/baseline.md
Status: implemented-unqualified (ledger rows); GA-01 acceptance met
Decision or scope change: none; gaps recorded, not fixed
Budget/performance/security impact: none measured
Remaining uncertainty / next smallest action: choose the next backlog item
Pilot feedback / user decision needed: none for GA-01
```
