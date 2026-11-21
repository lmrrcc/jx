# GA-02 — Portfolio routes, owners and pilot selection

Working file for the second [execution backlog](ga-execution.md) item. Parent:
[GA roadmap](ga-roadmap.md). This file is the task list for GA-02 only; update the
checklist and the daily record as work lands. Nothing from GA-03 onward is started here.

Acceptance outcome from the backlog: *canonical deployments and required journeys
agreed; old-UI behavior/performance captured.*

## Status

**Done, 2026-11-21.** Committed as `aebedc1`; Verify passed on the following commit
([run 44912595660](https://github.com/lmrrcc/jx/actions/runs/44912595660)).

## Design

| Deliverable | Location | Purpose |
| --- | --- | --- |
| Surface register | [portfolio.tsv](evidence/portfolio.tsv) | One row per browser surface: product, profile, stack, checkout, revision, URL, deployment state, owner, pilot state. |
| Journey register | [journeys.tsv](evidence/journeys.tsv) | Required journeys and pilot candidates per surface, with access level, existing test and baseline reference. |
| Checker | [CheckBaseline.java](../tools/CheckBaseline.java) | Validates both registers: vocabularies, unique ids, journeys refer to surfaces, a selected pilot has an owner and a journey. |
| Old-UI baseline | [portfolio-baseline.md](evidence/portfolio-baseline.md) | Dated measurements of the deployed public sites and local production bundles. |
| Corrected inventory | [ga-migrations.md](ga-migrations.md) | Observed targets and findings for product owners. |

Rules for this work:

- Sibling checkouts are read only. Nothing is installed, built into, committed or
  deployed there. Build output goes to a temporary directory or is not produced.
- Production sites receive page loads only. No form is submitted and no login is tried.
- Checkout paths in the registers are relative to the local workspace directory that
  holds the sibling repositories. They are evidence pointers, not bundled files.
- The journey register lists required journeys and pilot candidates, not every
  route. Route counts are in the surface notes.
- Authenticated journeys are described from source. Running them against their
  backends belongs to the pilot work packages and stays open here.

## Tasks

- [x] 1. Create this file and link it from the GA-02 backlog row.
- [x] 2. Write `portfolio.tsv` from the inventory; re-check revisions and remotes.
- [x] 3. Write `journeys.tsv`: required journeys, pilot candidates, existing tests.
- [x] 4. Extend `CheckBaseline.java`; negative checks on a scratch copy.
- [x] 5. Check which candidate public URLs respond; set `deployment` from the result.
- [x] 6. Lighthouse runs on the live URLs; summarize medians.
- [x] 7. Local production bundle measurements.
- [x] 8. Write `evidence/portfolio-baseline.md` from actual output.
- [x] 9. Correct `ga-migrations.md`; add findings for product owners.
- [x] 10. Write the scored pilot shortlist; record Luca's decisions on the pilot
  pair, canonical deployments and owners.
- [x] 11. Run both checkers; update the roadmap and the backlog row.
- [x] 12. After Luca's end-to-end check: commit, push, confirm Verify is green.

## Pilot shortlist

Scored from the registers and the [baseline record](evidence/portfolio-baseline.md).
"Measured" means a deployed page has a baseline; "tests" means the existing UI has
browser journey tests to compare against.

Website profile:

| Rank | Candidate | For | Against |
| --- | --- | --- | --- |
| 1 | `lmrr.blog` listing pages: `/blog/`, `/docs/` and three section indexes | Live and measured; five pages; no forms; no script needed. The 53–61 KiB baseline forces JX to prove HTML output without a client runtime | No tests; 438 uncommitted changes in the checkout; only the homepage has a canonical tag |
| 2 | `stacktrace.site` legal pages: `/:lang/privacy/`, `/:lang/imprint/` | Live and measured; prerender plus hydration is the shape JX must support; Playwright and accessibility tests exist; two languages | Repository has no commits; 165 KiB script baseline to match |
| 3 | `gandalf.protocol-site` home | Live and measured; no forms; strict headers; ten Playwright tests | One long page with animation and a carousel; its test server setup conflicts with that repository's rules |

Application profile:

| Rank | Candidate | For | Against |
| --- | --- | --- | --- |
| 1 | `medusa.changes-list`: `/app/changes` | Read-only; one GET; exercises the real session, tenant and cancellation model that the roadmap names as the hard case; Kotlin/Spring backend | Not deployed anywhere, so no cohort or field baseline until hosting is decided; 49 uncommitted frontend changes; no journey test for this route |
| 2 | `sensei.leaderboards`: `/web/leaderboards` | Live; GET only; anonymous and signed-in states; older Kotlin/JDK 21 backend exercises the no-rewrite rule | Public rather than authenticated; no browser tests in the product; client-rendered baseline is slow |
| 3 | `sensei.sessions`: `/app/sessions` | Live; exercises token, session verification and subscription gating | Same component creates sessions; token in local storage and cached entitlement behavior would be carried over or must change |

Extension profile, for later qualification (GA-33, GA-37): `gandalf.ext-activity`,
then `gandalf.ext-networks` and `gandalf.ext-connected-apps`.

Recommendation: `lmrr.blog` listing pages and `medusa.changes-list`. They are the
default pair in the roadmap (one content page, one read-only authenticated route).
Medusa needs a hosting decision before any real users see a pilot.

## Decisions

Given by Luca on 2026-11-21.

| Decision | Outcome |
| --- | --- |
| Website pilots | `lmrr.cc`, `stacktrace.cc` and `luca4j.com` are the starting points. `luca4j.com` was not in the inventory; it was added to the registers and measured the same day. |
| Application pilot | Stack Sensei. |
| Canonical checkouts and deployments | Confirmed as listed in the surface register. |
| Owner | Luca Marrocco for every surface. |

Conditions Luca set for the Stack Sensei pilot:

- The existing site is not modified. The JX version is a separate implementation
  that runs beside it.
- Functionality must match one to one.
- The application is critical and expensive to tune, so any change to it must be
  very small and controlled.

Still open, for the pilot work packages and not for GA-02:

- Which Stack Sensei route goes first. The shortlist order is
  `sensei.leaderboards`, then `sensei.profile`, then `sensei.sessions`.
- Which pages of each website go first. The shortlist suggests the `lmrr.cc`
  listing pages and the Stacktrace legal pages; for `luca4j.com`, `/privacy/`.
- How a separate implementation is routed beside the existing Stack Sensei
  frontend without changing it.
- Medusa stays a candidate; it has no deployment to pilot against.

## Daily record

```text
Date / plan revision: 2026-11-21 / roadmap 0.1
Current milestone and selected backlog ID: M1 / GA-02
Owner / reviewer: Luca with Claude / Luca
Observable outcome for today: registers, old-UI baseline, pilot decisions
Starting evidence and assumptions: initial inventory in ga-migrations.md
Changes / experiment: see Tasks 1-11; sibling checkouts read only
Checks actually executed: see evidence/portfolio-baseline.md; both checkers pass
Observed outcome and failure cases: deployment facts differ from several
  repository documents; no authenticated journey was run
Status: implemented-unqualified
Decision or scope change: luca4j.com added as a website pilot; Stack Sensei chosen
  with a no-modification, one-to-one condition
Budget/performance/security impact: baselines recorded; findings listed in
  ga-migrations.md for the owner
Remaining uncertainty / next smallest action: Task 12
Pilot feedback / user decision needed: end-to-end check, then commit and push
```

## End-to-end check

```sh
java tools/CheckAgentSetup.java
java tools/CheckBaseline.java
```

Both print `PASS`; the second counts portfolio surfaces and journeys. Then read the
shortlist and decisions below and the numbers in the baseline record, and rerun one
recorded Lighthouse command to compare. To see the checker reject drift, change a
`deployment` value in `portfolio.tsv` to `maybe`, rerun it, read the `FAIL:` line,
then revert.
