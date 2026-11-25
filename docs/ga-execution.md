# GA execution backlog and daily review

Draft 0.3, progress reviewed 25 November 2026. Parent: [GA roadmap](ga-roadmap.md).
The Status column flags every work package: **✅ done · 🟡 partial · ⬜ planned**.
It tracks the package's acceptance outcome; an existing baseline implementation
does not complete its planned GA qualification work. IDs are stable for future
issues/PRs and evidence records.
No GitHub issues, scheduled jobs, deployments or releases were created by this plan.

Progress: **GA-01 and GA-02 are complete**. GA-14, GA-15 and GA-18 have substantial
partial implementations through the React parity tasks; their broader acceptance
outcomes below are not yet complete. R-26 is accepted; R-27-01 and R-27-02a–02c
merged with green Verify. R-27-02a landed in [PR #39](https://github.com/lmrrcc/jx/pull/39)
at `413558b636679dd5926f4d7bf1c987ea707ed95c`.
R-27-02b landed in [PR #40](https://github.com/lmrrcc/jx/pull/40) at
`15c0c6eee36f506910e854c568547ebc6d3758ea`, with its scoped mutant killed and green
[CI](https://github.com/lmrrcc/jx/actions/runs/45452120669) and
[CI](https://github.com/lmrrcc/jx/actions/runs/45452801956).
R-27-02c's complete local bench passes 779 checks, with 133 skipped across 114
case IDs, and records 38 / 5,356 active feature pairs, with 5,318 still uncovered.
Its six new portal witnesses cover context, actual DOM identity, event phases and
payloads, focus and cleanup. The portal starts closed for SSR and opens after
hydration; open-portal server rendering is not qualified. Its unmutated baseline
passed and its scoped mutant was killed (1 killed, 0 survived, 0 unusable);
Verify passed and [PR #41](https://github.com/lmrrcc/jx/pull/41) merged as `a0f5460`,
with [green CI](https://github.com/lmrrcc/jx/actions/runs/45461059543) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45460586560).
R-27-03a's complete local bench passes 787 checks, with 133 skipped across 115
case IDs: client 113 reference / 112 JX, hydrate 88, static 94 and lazy 99 per side.
Its eight focused checks pass. Four action-state pairs raise local evidence to
42 / 5,356, leaving 5,314 active pairs open. The case proves queued payloads and
previous-state composition, form status/reset, canceled successors on rejection,
retry while pending and unmount/remount with a fresh owner. The fixture's local
cancellation does not deliver the G0 API. Its unmutated baseline passed, then
`combined-action-queue` killed `action-queue-previous-lost` (1 killed, 0 survived,
0 unusable). [PR #43](https://github.com/lmrrcc/jx/pull/43) merged as `1ba8c1d`,
with [green CI](https://github.com/lmrrcc/jx/actions/runs/45480919951) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45480798051).
R-27-03b's complete local bench passes 795 checks, with 133 skipped across 116
case IDs: client 114 reference / 113 JX, hydrate 89, static 95 and lazy 100 per side.
Its eight focused checks pass. Three optimistic/store pairs raise local evidence
to 45 / 5,356, leaving 5,311 active pairs open. The case proves remote rebase,
rejection rollback, one committed item while a retry remains pending, and explicit
owner disposal during both awaits with zero requests and subscriptions remaining.
Disposal is fixture-local, not the G0 cancellation API. Its unmutated baseline
passed, then `combined-optimistic-store` killed `optimistic-duplicate-overlay`
(1 killed, 0 survived, 0 unusable). [PR #44](https://github.com/lmrrcc/jx/pull/44)
merged as `3e24edb`, with [green CI](https://github.com/lmrrcc/jx/actions/runs/45548420721)
and [green CI](https://github.com/lmrrcc/jx/actions/runs/45548370606).
That slice's catalog has 109 entries, while the historical full campaign remains 100.
R-27-03c's complete local bench passes 803 checks, with 133 skipped across 117
case IDs: client 115 reference / 114 JX, hydrate 90, static 96 and lazy 101 per side.
Its eight focused checks pass. Three transition pairs raise local evidence to
48 / 5,356, leaving 5,308 active pairs open. They prove urgent input updates,
replacement and cancellation under a ref-owned operation token, a successfully
settled result becoming stale before its continuation, and effect cleanup before
an independent instance mounts. The test observes the old transition ending
before awaiting its replacement, so React entanglement cannot conceal stale UI.
Cancellation is fixture-local and permits transport to finish later; G0 remains
open. After the shared scratch baseline passed, `combined-transition-cancel`
killed `transition-owner-guard-lost` (1 killed, 0 survived, 0 unusable). The
exclusive run used `abfb24a` in scratch `jx-mutation-1181316345297598843`, with
identical transition source to rebased `d89e23f`. PR #45 merged as `812a27a` after
the full local gate and both Verify checks passed. This slice's
catalog has 110 entries; the shared run's 112 includes later SSR cases. The
historical full campaign remains 100.
The Promise prerequisite has nine Promise tests, 36 runtime-package JVM tests and
13 runtime browser checks passing, followed by its full local gate and CI.
[PR #42](https://github.com/lmrrcc/jx/pull/42) merged as `f2b64cb`, with
[green CI](https://github.com/lmrrcc/jx/actions/runs/45467245368) and
[green CI](https://github.com/lmrrcc/jx/actions/runs/45466911060).
R-27-04's complete local bench passes 809 checks, with 151 skipped across 120
case IDs: client 118 reference / 117 JX, hydrate 90, static 96 and lazy 101 per side.
Four server/resource pairs bring complete local evidence to 52 / 5,356, leaving
5,304 active pairs open. Real Writable bytes precede gated data; aborting one
request preserves the other provider; resource hints and actual server nodes
survive hydration and updates. A fatal shell now rejects both readiness consumers.
All three scoped SSR mutants were killed after a passing baseline; Verify is pending.
These proofs do not complete protected
SSR G0 M1-26, general destination-failure handling or cancellation of application I/O.
R-27-03b and R-27-03c are merged; R-27-04 remains in review. See the
[interaction evidence record](ga-react-interactions.md) for the unchanged eventual
118-feature / 6,903-pair scope and its limits. G0 remains open: the serialized lazy
boundary and basic Node rendering/hydration proofs are delivered, while component
JARs, exact shared contracts, authenticated packaging and protected SSR data still
need their complete evidence. Existing editor packages,
the todo API and hot updates are useful baselines, not completion of their GA
qualification packages. The Cherry Test unit/coverage baseline is delivered;
TT-01–TT-06 in the implementation sequence track the remaining test tooling.

Luca authorized continued implementation of React parity and G0 on 24 November,
followed by the JX/Spring starter and faster hot reload. Preserve the automated
gates and record human qualification or applicability decisions explicitly.
The current browser evidence is Chrome automation, not cross-browser qualification.

## Work package register

The order of implementation is the [implementation sequence](ga-tasks.md): tasks
the size of one change, grouped by milestone and done one after the other. This
register remains the scope and acceptance definition for each work package.

Size classes guide decomposition, not delivery promises: **S** = a 1–3 engineer-day
spike/check; **M** = roughly 1–3 engineer-weeks; **L** = a 3–8 week epic requiring
smaller tickets. Re-estimate after G0. Dates refer to roadmap milestone windows;
independent streams can overlap. A package is done only when its acceptance evidence
exists, not when its document or skeleton module lands.

| ID | Work package / owner role | Depends on | Target / size | Acceptance outcome | Status |
| --- | --- | --- | --- | --- | --- |
| GA-01 | Baseline and capability/evidence ledger / Q | — | M1 / M | Reproducible clean build, verified CI status, exact version manifest, gaps and commands recorded. **Done 2026-11-21, CI green at `3688d25`** — [task file](ga-01-baseline.md) | ✅ done |
| GA-02 | Portfolio routes, owners and pilot selection / M | — | M1 / M | Canonical deployments and required journeys agreed; old-UI behavior/performance captured. **Done 2026-11-21** — [task file](ga-02-portfolio.md) | ✅ done |
| GA-03 | Browser async ownership design / R | GA-01 | M1 / S | One HTTP/Elm/loader/Suspense cancellation/error model demonstrated in a spike | ⬜ planned |
| GA-04 | Shared contract, codec and exact-value spike / C | GA-03 | M1 / M | One DTO/operation definition; real Spring/browser round trip, decimal/long/null boundaries | ⬜ planned |
| GA-05 | Component-library JAR ABI spike / L | GA-01 | M1 / S | Independent producer and consumer, no source copying, metadata version failure tested | ⬜ planned |
| GA-06 | Lazy-chunk Java identity spike / R | GA-05 | M1 / M | Lazy route absent initially; domain values/context/callbacks cross safely; no duplicate React. M1-22/M1-23 prove the serialized boundary; independent library consumption and automatic application packaging remain | 🟡 partial |
| GA-07 | React SSR→hydration spike / W | GA-03 | M1 / M | Protected data-dependent route, scoped API/auth, two simultaneous identities, immutable hydration snapshot, abort and no duplicate fetch/mutation. Basic Node rendering/hydration is delivered; protected data isolation remains | 🟡 partial |
| GA-08 | Native editor architecture/performance decision / T | GA-01 | M1 / M | Scale baseline; precise projection limits; source-preserving structural proof or explicit revised design | ⬜ planned |
| GA-09 | Artifact identities and clean distribution rehearsal / Q | GA-01 | M1–M3 / M | Namespaces/owners verified; external consumer installs candidate artifacts without workspace caches | ⬜ planned |
| GA-10 | Contract IR and generators v1 / C | GA-04 | M2–M3 / L | Java authority + generated async client/codecs/server boundary/schema; incompatible wire change fails | ⬜ planned |
| GA-11 | Browser Java certification and dependency scanner / J | GA-01, GA-04 | M2–M6 / L | Per-method JVM/browser vectors and transitive unsupported-call diagnostics; size budgets | ⬜ planned |
| GA-12 | Optional Boot starter/BOM and template / S | GA-10 | M2–M3 / L | External consumer; executable JAR in empty directory; auto-config backoff, route/caching tests | ⬜ planned |
| GA-13 | Authenticated same-origin dev lifecycle / S | GA-03, GA-04 | M2–M4 / L | Session/CSRF/OIDC with hot updates; secure proxy/backend-fronting; owned backend/UI processes | ⬜ planned |
| GA-14 | Grammar, generic/default/forwarded props / L | GA-05 | M2–M4 / L | Typed reusable components, Java-equivalent JSX patterns, recovery/fuzz/mapped errors | 🟡 partial |
| GA-15 | Rich DOM/events/refs and browser schema / R | GA-03, GA-14 | M3–M5 / L | HTML/SVG/IME/pointer/file/ref behavior; safe rich-content contract, CSR/SSR sanitization; wrong types rejected at source | 🟡 partial |
| GA-16 | Nested router and resource cache / A | GA-03, GA-10, GA-06 | M3–M4 / L | Loaders/actions, nested layouts, abort/stale protection, invalidation/optimistic rollback and deep links | ⬜ planned |
| GA-17 | Forms, validation, i18n and accessible primitives / A | GA-10, GA-15 | M3–M5 / L | Complex form, arrays/errors, locale/RTL, keyboard/reader, shared pure rules and server errors | ⬜ planned |
| GA-18 | Stable React API parity / R | GA-03, GA-06, GA-15 | M3–M6 / L | Complete pinned API ledger; concurrency/actions/refs/portals/lazy/Suspense differential fixtures. R-27-02b, portal PR #41, Promise PR #42, action PR #43, optimistic PR #44 and transition PR #45 merged; R-27-04 locally evidences 52 active pairs; 5,304 active pairs plus deferred-feature interactions remain; four transition/SSR scoped mutants killed, Verify pending | 🟡 partial |
| GA-19 | HTTP headers/binary/upload/stream capabilities / C | GA-03, GA-10, GA-13 | M3–M5 / L | Auth/CSRF headers, upload progress, download, SSE/WS lifecycle, bounds/reconnect tested | ⬜ planned |
| GA-20 | Real ecosystem binding SDK and catalog / R | GA-15, GA-18 | M4–M7 / L | Selected grid/editor/chart/headless/media bindings with refs/assets/cleanup; Java consumer examples | ⬜ planned |
| GA-21 | Complex authenticated reference product / A | GA-12, GA-13, GA-16, GA-17, GA-19 | M4–M6 / L | Workspace/project workflow with roles, search/table/detail, forms/files/activity and two-tenant tests | ⬜ planned |
| GA-22 | SSG/SSR host, hydration and asset pipeline / W | GA-06, GA-07, GA-16 | M4–M6 / L | SEO website; bounded isolated SSR, data snapshots, hashed assets and cache/status correctness | ⬜ planned |
| GA-23 | Debugging, source maps and observability / T | GA-05, GA-06, GA-07 | M3–M7 / L | Event/async/SSR failures and breakpoints map to .jx; artifact-linked maps and safe logging | ⬜ planned |
| GA-24 | IntelliJ editing and run/debug parity / T | GA-08, GA-14, GA-23 | M2–M7 / L | Native action corpus incl. safe closed-file edits; installed packages, trust, scale and version tests | ⬜ planned |
| GA-25 | VS Code editing and run/debug parity / T | GA-08, GA-14, GA-23 | M2–M7 / L | Class/file rename, formatting, closed-file references, normal navigation and installed-JDT matrix | ⬜ planned |
| GA-26 | TurboJ native .jx integration / T | GA-14, GA-23 | M4–M8 / L | Reuse compiler semantics; native mixed editor/run/debug with own acceptance corpus | ⬜ planned |
| GA-27 | Hot update and cache scale / R | GA-10, GA-18, GA-23 | M4–M7 / M | Compatible typed checkpoint codecs, resets explained, no replay/leaks after 100 updates | ⬜ planned |
| GA-28 | Cross-browser/OS/performance/a11y/security CI / Q | GA-01; each delivered feature | M1–M12 / L | Required matrix/payload/latency gates; real-device and manual evidence attached to RC | ⬜ planned |
| GA-29 | Existing Spring/Kotlin and schema-first adapters / C | GA-10, GA-11 | M3–M7 / L | Java21/Kotlin Gradle Boot consumes Maven-built JX contracts/assets in clean CI+IDE; Rust-schema consumer; no forced backend/build-tool rewrite | ⬜ planned |
| GA-30 | Static site pilot / M | GA-02, GA-22, GA-28 | M6–M8 / M | Complete low-risk site with existing URLs/forms/SEO, canary and rollback | ⬜ planned |
| GA-31 | Authenticated SPA pilot / M | GA-02, GA-21, GA-29 | M7–M9 / L | Product-approved Medusa/Stack Sensei route cohort, one session owner, 30-day observation | ⬜ planned |
| GA-32 | Media/payment/finance qualification / M | GA-19, GA-20, GA-31 | M7–M9 / L | Exact values, session races, real-device audio cleanup and provider-sandbox payment recovery | ⬜ planned |
| GA-33 | Extension/Rust-WASM qualification and pilot / M | GA-11, GA-20, GA-29, GA-37 | M6–M9 / L | Packaged MV3 popup/worker/storage/CSP; existing Rust custody preserved; read-only pilot | ⬜ planned |
| GA-34 | API freeze and version-skew/rollback / Q | GA-10, GA-18, GA-22, GA-29 | M9–M10 / M | Stable public/wire/metadata policies, old tab/new backend and retained asset rollback | ⬜ planned |
| GA-35 | RC soak, external onboarding and release rehearsal / Q | GA-24, GA-25, GA-28, GA-30, GA-31, GA-34 | M10–M11 / L | Clean install/upgrade, support/security owners, 30-day pilots, zero blocking findings | ⬜ planned |
| GA-36 | GA decision package / lead + Luca | GA-35; mandatory profile gates | M12 / M | Signed evidence manifest, known limits, artifacts and go/no-go; publication is a separate authorized action | ⬜ planned |
| GA-37 | MV3/CSP/Rust-WASM feasibility / M + R | GA-01 | M1–M2 / S | Actual packaged extension renders JX and calls existing WASM under its CSP; popup close/reopen smoke | ⬜ planned |
| GA-38 | Persistence/offline policy and browser storage / A | GA-03, GA-16, GA-19 | M5–M8 / M | Storage/schema migrations, quota and stale-cache recovery; explicit opt-in PWA/service-worker profile; no unsafe offline mutation replay | ⬜ planned |
| GA-39 | G4 portfolio qualification sign-off / M + Luca | GA-30, GA-31, GA-32, GA-33, GA-34, GA-38 | M12 / M | Separate signed media/finance/extension evidence and remaining migration register; not implied by G3 framework GA | ⬜ planned |

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
