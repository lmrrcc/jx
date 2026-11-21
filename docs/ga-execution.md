# GA execution backlog and daily review

Draft 0.1, 19 November 2026. Parent: [GA roadmap](ga-roadmap.md).
All entries below are **planned, unassigned work** unless the row states a status,
even where a current partial implementation exists. IDs are stable for future issues/PRs and evidence records.
No GitHub issues, scheduled jobs, deployments or releases were created by this plan.

## Work package register

Size classes guide decomposition, not delivery promises: **S** = a 1–3 engineer-day
spike/check; **M** = roughly 1–3 engineer-weeks; **L** = a 3–8 week epic requiring
smaller tickets. Re-estimate after G0. Dates refer to roadmap milestone windows;
independent streams can overlap. A package is done only when its acceptance evidence
exists, not when its document or skeleton module lands.

| ID | Work package / owner role | Depends on | Target / size | Acceptance outcome |
| --- | --- | --- | --- | --- |
| GA-01 | Baseline and capability/evidence ledger / Q | — | M1 / M | Reproducible clean build, verified CI status, exact version manifest, gaps and commands recorded. **Implemented 2026-11-21; CI confirmation pending** — [task file](ga-01-baseline.md) |
| GA-02 | Portfolio routes, owners and pilot selection / M | — | M1 / M | Canonical deployments and required journeys agreed; old-UI behavior/performance captured |
| GA-03 | Browser async ownership design / R | GA-01 | M1 / S | One HTTP/Elm/loader/Suspense cancellation/error model demonstrated in a spike |
| GA-04 | Shared contract, codec and exact-value spike / C | GA-03 | M1 / M | One DTO/operation definition; real Spring/browser round trip, decimal/long/null boundaries |
| GA-05 | Component-library JAR ABI spike / L | GA-01 | M1 / S | Independent producer and consumer, no source copying, metadata version failure tested |
| GA-06 | Lazy-chunk Java identity spike / R | GA-05 | M1 / M | Lazy route absent initially; domain values/context/callbacks cross safely; no duplicate React |
| GA-07 | React SSR→hydration spike / W | GA-03 | M1 / M | Protected data-dependent route, scoped API/auth, two simultaneous identities, immutable hydration snapshot, abort and no duplicate fetch/mutation |
| GA-08 | Native editor architecture/performance decision / T | GA-01 | M1 / M | Scale baseline; precise projection limits; source-preserving structural proof or explicit revised design |
| GA-09 | Artifact identities and clean distribution rehearsal / Q | GA-01 | M1–M3 / M | Namespaces/owners verified; external consumer installs candidate artifacts without workspace caches |
| GA-10 | Contract IR and generators v1 / C | GA-04 | M2–M3 / L | Java authority + generated async client/codecs/server boundary/schema; incompatible wire change fails |
| GA-11 | Browser Java certification and dependency scanner / J | GA-01, GA-04 | M2–M6 / L | Per-method JVM/browser vectors and transitive unsupported-call diagnostics; size budgets |
| GA-12 | Optional Boot starter/BOM and template / S | GA-10 | M2–M3 / L | External consumer; executable JAR in empty directory; auto-config backoff, route/caching tests |
| GA-13 | Authenticated same-origin dev lifecycle / S | GA-03, GA-04 | M2–M4 / L | Session/CSRF/OIDC with hot updates; secure proxy/backend-fronting; owned backend/UI processes |
| GA-14 | Grammar, generic/default/forwarded props / L | GA-05 | M2–M4 / L | Typed reusable components, Java-equivalent JSX patterns, recovery/fuzz/mapped errors |
| GA-15 | Rich DOM/events/refs and browser schema / R | GA-03, GA-14 | M3–M5 / L | HTML/SVG/IME/pointer/file/ref behavior; safe rich-content contract, CSR/SSR sanitization; wrong types rejected at source |
| GA-16 | Nested router and resource cache / A | GA-03, GA-10, GA-06 | M3–M4 / L | Loaders/actions, nested layouts, abort/stale protection, invalidation/optimistic rollback and deep links |
| GA-17 | Forms, validation, i18n and accessible primitives / A | GA-10, GA-15 | M3–M5 / L | Complex form, arrays/errors, locale/RTL, keyboard/reader, shared pure rules and server errors |
| GA-18 | Stable React API parity / R | GA-03, GA-06, GA-15 | M3–M6 / L | Complete pinned API ledger; concurrency/actions/refs/portals/lazy/Suspense differential fixtures |
| GA-19 | HTTP headers/binary/upload/stream capabilities / C | GA-03, GA-10, GA-13 | M3–M5 / L | Auth/CSRF headers, upload progress, download, SSE/WS lifecycle, bounds/reconnect tested |
| GA-20 | Real ecosystem binding SDK and catalog / R | GA-15, GA-18 | M4–M7 / L | Selected grid/editor/chart/headless/media bindings with refs/assets/cleanup; Java consumer examples |
| GA-21 | Complex authenticated reference product / A | GA-12, GA-13, GA-16, GA-17, GA-19 | M4–M6 / L | Workspace/project workflow with roles, search/table/detail, forms/files/activity and two-tenant tests |
| GA-22 | SSG/SSR host, hydration and asset pipeline / W | GA-06, GA-07, GA-16 | M4–M6 / L | SEO website; bounded isolated SSR, data snapshots, hashed assets and cache/status correctness |
| GA-23 | Debugging, source maps and observability / T | GA-05, GA-06, GA-07 | M3–M7 / L | Event/async/SSR failures and breakpoints map to .jx; artifact-linked maps and safe logging |
| GA-24 | IntelliJ editing and run/debug parity / T | GA-08, GA-14, GA-23 | M2–M7 / L | Native action corpus incl. safe closed-file edits; installed packages, trust, scale and version tests |
| GA-25 | VS Code editing and run/debug parity / T | GA-08, GA-14, GA-23 | M2–M7 / L | Class/file rename, formatting, closed-file references, normal navigation and installed-JDT matrix |
| GA-26 | TurboJ native .jx integration / T | GA-14, GA-23 | M4–M8 / L | Reuse compiler semantics; native mixed editor/run/debug with own acceptance corpus |
| GA-27 | Hot update and cache scale / R | GA-10, GA-18, GA-23 | M4–M7 / M | Compatible typed checkpoint codecs, resets explained, no replay/leaks after 100 updates |
| GA-28 | Cross-browser/OS/performance/a11y/security CI / Q | GA-01; each delivered feature | M1–M12 / L | Required matrix/payload/latency gates; real-device and manual evidence attached to RC |
| GA-29 | Existing Spring/Kotlin and schema-first adapters / C | GA-10, GA-11 | M3–M7 / L | Java21/Kotlin Gradle Boot consumes Maven-built JX contracts/assets in clean CI+IDE; Rust-schema consumer; no forced backend/build-tool rewrite |
| GA-30 | Static site pilot / M | GA-02, GA-22, GA-28 | M6–M8 / M | Complete low-risk site with existing URLs/forms/SEO, canary and rollback |
| GA-31 | Authenticated SPA pilot / M | GA-02, GA-21, GA-29 | M7–M9 / L | Product-approved Medusa/Stack Sensei route cohort, one session owner, 30-day observation |
| GA-32 | Media/payment/finance qualification / M | GA-19, GA-20, GA-31 | M7–M9 / L | Exact values, session races, real-device audio cleanup and provider-sandbox payment recovery |
| GA-33 | Extension/Rust-WASM qualification and pilot / M | GA-11, GA-20, GA-29, GA-37 | M6–M9 / L | Packaged MV3 popup/worker/storage/CSP; existing Rust custody preserved; read-only pilot |
| GA-34 | API freeze and version-skew/rollback / Q | GA-10, GA-18, GA-22, GA-29 | M9–M10 / M | Stable public/wire/metadata policies, old tab/new backend and retained asset rollback |
| GA-35 | RC soak, external onboarding and release rehearsal / Q | GA-24, GA-25, GA-28, GA-30, GA-31, GA-34 | M10–M11 / L | Clean install/upgrade, support/security owners, 30-day pilots, zero blocking findings |
| GA-36 | GA decision package / lead + Luca | GA-35; mandatory profile gates | M12 / M | Signed evidence manifest, known limits, artifacts and go/no-go; publication is a separate authorized action |
| GA-37 | MV3/CSP/Rust-WASM feasibility / M + R | GA-01 | M1–M2 / S | Actual packaged extension renders JX and calls existing WASM under its CSP; popup close/reopen smoke |
| GA-38 | Persistence/offline policy and browser storage / A | GA-03, GA-16, GA-19 | M5–M8 / M | Storage/schema migrations, quota and stale-cache recovery; explicit opt-in PWA/service-worker profile; no unsafe offline mutation replay |
| GA-39 | G4 portfolio qualification sign-off / M + Luca | GA-30, GA-31, GA-32, GA-33, GA-34, GA-38 | M12 / M | Separate signed media/finance/extension evidence and remaining migration register; not implied by G3 framework GA |

These epic ranges intentionally expose that the full scope exceeds a casual
one-person checklist. Staffing/reuse determines overlap; the first review must
reconcile capacity with this register. Do not sum optimistic lower bounds and call
the result a committed schedule. Dependencies on large epics mean the necessary
contract/slice, not waiting for every optional subfeature of that epic.

## First 30 calendar days

The following are work-session goals within the first month, not a requirement to
work every weekend. Maintain a daily record even if the action is simply updating
a decision or investigating evidence. Candidate API syntax stays provisional.

| Session window | Work and reviewable output |
| --- | --- |
| D1–3 | Freeze baseline revision, run clean checks, establish ledger, inspect CI failures and inventory deployed variants; select pilot owners and record staffing |
| D4–7 | Define reference workload and baseline React implementation; exact-value vectors; component JAR consumer; benchmark cold/warm build and editor latency; review scope/capacity |
| D8–12 | Async/contract spike with actual Spring/browser request; Java21/Kotlin compatibility decision; lazy-route runtime-identity spike in parallel with isolated React SSR proof |
| D13–17 | Authenticated development experiment and executable Boot JAR; decide contract authority/IR and wire rules; record RSC scope; test failure cases of lazy/SSR prototypes |
| D18–22 | Native editor structural/scale experiments; library certification prototype; compare SSR host alternatives and supported chunk model; start MV3/CSP/WASM smoke; identify upstream TeaVM contributions |
| D23–26 | Integrate one thin vertical consumer using the chosen contracts/library ABI; draft compatibility matrix, security boundaries and rejected-design rationale |
| D27–30 | G0 review: demonstrate evidence, reject unsupported claims, assign owners, re-estimate M2–M3 and record go/change/stop for each architecture risk |

G0 has **five non-negotiable proofs**: external component JAR, shared exact contract,
thin authenticated packaged Spring/dev-origin prototype, safe lazy-runtime boundary, and actual
React SSR→hydration. Tooling/Java profile/CI baselines run alongside them. Timebox
each unknown and preserve a failed prototype as evidence; failure triggers a
decision, not a cosmetic mock that makes the checkpoint appear complete.

## Risk register

| Risk | Trigger / early signal | Response and owner |
| --- | --- | --- |
| TeaVM chunk/object ABI incompatible | Shared record/context/callback breaks across lazy route | GA-06 early proof; supported shared runtime or explicit ABI ADR; runtime lead |
| SSR cannot reuse browser-oriented code safely | Browser globals on import, request state leakage, mismatched IDs | GA-07; server capability isolation and real React host; rendering lead |
| Contract drift despite generated types | Actual Jackson/controller output differs from schema | Real HTTP golden fixtures, bounded annotation subset/custom adapters; contract lead |
| Java reuse pulls server dependencies | Reachability includes JDBC/Spring/reflection or large resources | Dependency-path diagnostic and portable module boundary; compatibility lead |
| Browser/event/library breadth explodes | Every product requires one-off untyped JS fixes | Prioritized binding catalog, typed SDK, product conformance corpus; runtime lead |
| Native editor integration plateaus | Corrupting edits, stalls, incomplete cross-file mapping | Explicit action corpus, architecture spike, budget; tooling lead; no fake parity |
| React18/19 migration conflict | Duplicate React/context loss in mixed page | Route isolation or qualified host upgrade; migration lead |
| Existing JVM baseline mismatch | Java25 shared artifact cannot load on Java21 service | Lower shared bytecode or schema-derived adapter, explicit migration decision; contract lead |
| Authenticated dev widens trust boundary | Unrestricted proxy headers/targets/cookies | Same-origin design and threat tests before expansion; Spring/security lead |
| Schedule/capacity underestimated | Repeated milestone carryover, defects crowd out evidence | Re-estimate monthly, reduce WIP, move dates; lead + Luca |
| Upstream churn | React/TeaVM/IDE update invalidates behavior | Pinned manifest, upstream test lane, bounded support lines; release lead |
| Portfolio acceptance understated | Media/custody/payment/SEO behavior missed | Product-owned journey inventory and separate gates; migration lead |

Track likelihood/impact and current evidence at the first weekly review; do not
invent numerical probabilities before the initial spikes.

## Daily record template

```text
Date / plan revision:
Current milestone and selected backlog ID:
Owner / reviewer:
Observable outcome for today:
Starting evidence and assumptions:
Changes / experiment:
Checks actually executed (revision, commands, artifacts):
Observed outcome and failure cases:
Status: proposed / spike / implemented-unqualified / qualified / blocked
Decision or scope change (link ADR if consequential):
Budget/performance/security impact:
Remaining uncertainty / next smallest action:
Pilot feedback / user decision needed:
```

Weekly scorecard: mandatory capabilities qualified/total, new regressions, flaky
tests, unresolved critical issues, API/wire changes, build/editor/payload trends,
pilot journey results, incident/rollback evidence and next decisions. Do not reward
test counts, generated code volume or agent activity over user-visible behavior.

Monthly review archives the previous plan version, records changed assumptions,
updates target dates and approves the next 2–4 weeks. The annual plan remains a
living decision record; it is not 365 preassigned tasks or an unattended release
authorization.
