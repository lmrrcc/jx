# GA execution backlog and daily review

Draft 0.3, progress reviewed 26 November 2026. Parent: [GA roadmap](ga-roadmap.md).
The Status column flags every work package: **✅ done · 🟡 partial · ⬜ planned**.
It tracks the package's acceptance outcome; an existing baseline implementation
does not complete its planned GA qualification work. IDs are stable for future
issues/PRs and evidence records.
No GitHub issues, scheduled jobs, deployments or releases were created by this plan.

Next selected class slice: **🟡 R-28-03h2a legacy runtime** passes 182 JVM/TeaVM
checks and the complete **1,001-pass /223-exclusion /153-case** matrix. Its four
new legacy cases preserve the existing **62/6,328** interaction result. Publication
gate and Verify remain pending; compiler declaration discovery and broader hot
ownership are separate slices. See the [contract and evidence](evidence/class-legacy-lifecycles.md).

Latest MV3/WASM acceptance: **✅ M1-27 merged in PR83 as `c13bfd8`**, after all 19 local gate steps passed in 461 seconds and both Verify checks passed on `c1432dc`. The ZIP-extracted Java popup runs the pinned Rust/Brotli WASM module under actual MV3 CSP, opens and closes through Chrome's action API and rejects initialization under the negative CSP. Integrated **206 JVM, 47 runtime, 975 conformance checks/217 exclusions, three extension checks and four real hot workflows** pass. Full service-worker/storage/message, wallet and marketplace qualification remains separate.

Earlier protected-SSR acceptance: **✅ M1-26 merged in PR82 as `968ccf3`**, after all 18 local gate steps passed in 495 seconds and both Verify checks passed on `20e0eaa`. The actual Spring-authenticated route preserves two immutable request snapshots, hydrates retained DOM without refetch and cleans up a disconnected suspended request. Integrated **206 JVM, 47 runtime, 975 conformance checks/217 exclusions, three protected SSR checks and four real hot workflows** pass. Progressive private streaming, backend-fetch abort and production SSR hosting remain open.

Earlier session/CSRF acceptance: **✅ M1-21 merged in PR81 as `8795b35`**, after all 17 local gate steps passed in 451 seconds and both Verify checks passed on `cb279b0`. The native login, session rotation, CSRF/logout denial and two private workspaces are exercised through the executable JAR and actual Maven development proxy. Integrated **204 JVM, 47 runtime, 975 conformance checks/217 exclusions, four Todo, two packaged, two auth and four hot workflows** pass. OIDC, timed expiry/cross-tab recovery, general identity-generation disposal and deployment hardening remain separate.

Earlier packaged-Spring acceptance: **✅ M1-20 merged in PR80 as `74eb3d9`**, after all 16 local gate steps passed in 404 seconds and both Verify checks passed on `f07bcd4`. The actual executable Boot JAR serves the Java browser UI and API from an empty directory. Integrated **201 JVM, 47 runtime, 975 conformance checks/217 exclusions, four Todo, two packaged Chrome and four hot workflows** pass. This is the bounded root-context CSR proof; authentication, deployment policy and the starter remain separately tracked.

Earlier editor acceptance: **✅ M1-28/M1-29 merged in PR79 as `c533d99`**, after all 15 local gate steps passed in 368 seconds and both Verify checks passed on `c96e945`. The integration passes **198 JVM, 975 conformance checks with 217 exclusions, four Spring Todo scenarios and four hot workflows**. Original 96-test IntelliJ SDK/ZIP/Verifier evidence includes the 100-component timing fixture, exact-source rename on 101 closed files and undo/redo on 25 files. The native SDK undo limit at 101 retained files remains explicit; there is no blanket IDE-parity or new development-service claim.

Earlier exact-value acceptance: **✅ M1-18 merged in PR78 as `07290dd`**, after all 15 local gate steps passed in 385 seconds and both Verify checks passed on `70a01ee`. The final integration passes **197 JVM, 47 runtime Chrome, 975 conformance checks with 217 mode exclusions, four real Spring Todo scenarios and four hot workflows**. Twelve shared vectors preserve long/decimal precision and missing versus null through the real HTTP boundary. The explicit adapters remain separate from the bounded generated codec; richer schemas, generated clients and protocol evolution remain open.

Earlier shared-contract acceptance: **✅ M1-17/M1-19 merged in PR77 as `8ea78ba`**, after the 15-step gate passed in 374 seconds and both Verify checks passed on `0c0784a`. The final integration passes **195 JVM, 47 runtime Chrome, 975 conformance checks with 217 mode exclusions, three real Spring Todo scenarios and four hot workflows**. One portable record and its operation paths are shared; the generated codec supports required String/bool/int. Exact values are the next separate slice; generated clients, richer schemas and protocol evolution remain open.

Earlier route acceptance: **✅ M1-16 merged in PR76 as `515f2c2`**, after the 15-step gate passed in 339 seconds and both Verify checks passed on `226f1b4`. The final integration passes **187 JVM, 47 runtime Chrome, 975 conformance checks with 217 mode exclusions and four real hot workflows**. The 149 cases and 62/6328 pair count are unchanged. Shared cancellation and owned resources remain accepted in PR73/75; shared Spring contracts are the next prepared slice.

Earlier resource acceptance: **✅ M1-15 merged in PR75 as `f4b87ba`**, after the 15-step gate passed in 366 seconds and both Verify checks passed on `74ee92e`. The final integration passes **187 JVM, 45 runtime Chrome, 975 conformance checks with 217 mode exclusions and four real hot workflows**. The 149 cases and 62/6328 interaction count are unchanged. Cancellation remains accepted in PR73 and the bounded class journal in PR74; route loading is prepared separately.

Earlier published journal integration: **975 passed /217 skipped /149 cases**,
with 180 JVM checks, 39 runtime Chrome checks and four real hot workflow checks.
The full run preserves **62/6328 pairs** (6266 active pairs open). Shared
cancellation is **✅ merged in PR73 as `d9151c0`**; the bounded journal scopes
R-28-04a/b1/b2 are **✅ merged in PR74 as `ae8cdca`**, after the 15-step gate
passed in 406 seconds and both Verify checks passed on `4c02583`. Defaults remain
accepted through PR72. The journal's three historical direct mutants and separate
nullable-reset mutant retain their recorded source/observation scope. Full class
hot reload, broader R-28/R-31 and G0 remain open; resource and route loading are
prepared next. Earlier figures below record their respective slices.

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
All three scoped SSR mutants were killed after a passing baseline.
[PR #47](https://github.com/lmrrcc/jx/pull/47) merged as `7759933` after the full
local gate and both Verify workflows passed:
[push CI](https://github.com/lmrrcc/jx/actions/runs/45555926188) and
[PR CI](https://github.com/lmrrcc/jx/actions/runs/45556004135).
These proofs do not complete protected
SSR G0 M1-26, general destination-failure handling or cancellation of application I/O.
R-27-03b, R-27-03c and R-27-04 are merged.

R-28-01's integrated local bench passes **825 checks, with 151 skipped across
122 case IDs**: client **120 reference / 119 JX**, hydrate **92**, static **98**
and lazy **103** per side. Actual native React classes now have typed Java peers,
state patches, commit snapshots and instance refs; `PureComponent` uses React's
own shallow field comparison. The two new explicit key/ref witnesses bring
evidence to **54 / 5,460**, with **5,406** active pairs open. Activating
`PureComponent` increases the active universe to 105 features; the eventual
118-feature / 6,903-pair universe is unchanged, leaving 1,443 deferred-feature
pairs. This runtime slice uses explicit descriptors and ordinary Java `Views`.
`.jx` class authoring, native editor support and advanced lifecycle/context
options remained open in that first slice, whose hot replacement reset class
state. The later bounded journal is now published through PR74; complete class
checkpoint qualification remains open. All three targeted mutants were killed after
the unmutated baseline passed, with zero survivors or unusable mutants.
[PR #48](https://github.com/lmrrcc/jx/pull/48) merged as `ccfcb2d` after the local
14-step gate and both Verify checks passed on `b8657cb`:
[push CI](https://github.com/lmrrcc/jx/actions/runs/45560759038) and
[PR CI](https://github.com/lmrrcc/jx/actions/runs/45560907924).
The runtime slice's 115-entry catalog is separate from
the historical full campaign of 100. See the
[class-runtime decision](decisions/0006-java-class-components.md) and the
[interaction evidence record](ga-react-interactions.md) for the unchanged eventual
118-feature / 6,903-pair scope and its limits. G0 remains open: the serialized lazy
boundary, bounded shared/exact contracts, authenticated packaging and protected
SSR and MV3/Rust-WASM feasibility proofs are now accepted. Component JAR,
artifact-consumer publication and the remaining G0 review are tracked separately. Existing editor packages,
the todo API and hot updates are useful baselines, not completion of their GA
qualification packages. The Cherry Test unit/coverage baseline is delivered;
TT-01–TT-06 in the implementation sequence track the remaining test tooling.

Luca authorized continued implementation of React parity and G0 on 24 November,
followed by the JX/Spring starter and faster hot reload. Preserve the automated
gates and record human qualification or applicability decisions explicitly.
The current browser evidence is Chrome automation, not cross-browser qualification.

R-29's utility slice adds actual loaded version getters, raw development owner
stacks, legacy batching and always-awaited asynchronous Java `act` scopes.
Integrated over merged class authoring `3e1db97`, its complete bench passes
**847 checks, 169 skipped, across 127 cases**: client 125 reference / 124 JX,
hydrate 94, static 100 and lazy 105 per side. Four focused utility cases pass
14 applicable checks; all four targeted mutants were killed after the scratch
baseline passed, with zero survivors or unusable mutants. The active universe
expands to **113 features / 6,328 pairs**, with the same **54** witnesses and
**6,274** open pairs; 575 eventual pairs involve deferred features. No automatic
pair credit is given to the utility fixtures. [PR #52](https://github.com/lmrrcc/jx/pull/52)
merged as `8fdbed0` after the 15-step local gate and both Verify checks passed on
`f0b5a5b`; deep Java owner-stack diagnostics and server-cache refresh stay open.
The utility snapshot's catalog has 120 entries; the historical full campaign remains 100.

R-28-03a adds typed native class context readers. Integrated over PR #52's merge
`8fdbed0`, package checks pass 37 core, 48 compiler and 15 Maven tests. The complete
bench passes **855 checks, 169 skipped, across 128 cases**: client 126 reference /
125 JX, hydrate 95, static 101 and lazy 106 per side. All four class cases pass
eight projects each, with unchanged **54 / 6,328** pair evidence. The scoped
captured-context mutant was killed after a passing baseline, zero survived/unusable;
its runtime and fixture source is identical after rebase. The catalog is now 121,
without rerunning the historical full campaign of 100. Context merged as PR #53
at `198c0b8` after the 15-step gate and both Verify checks passed on `30a8911`.
Compiler context sugar, advanced lifecycle options, native
IDE qualification and class hot-state preservation are separate tasks.

R-28-03b adds native static derived state from props. Integrated over functional
merge `adcf22b`, package checks pass 42 core, 48 compiler and 15 Maven tests.
The complete bench passes **879 checks, 169 skipped, across 131 cases**: client
129 reference / 128 JX, hydrate 98, static 104 and lazy 109 per side. All five
class cases pass eight projects each, with unchanged **54 / 6,328** pair evidence.
The catalog now has 124 mutants; the scoped dropped-derivation mutant was killed
after a passing baseline, zero survived/unusable, on byte-identical derived source.
The historical full campaign remains 100. PR #55 merged as `84eb8cd` after its
15-step local gate passed in 377 seconds and both Verify checks passed on `021bc02`.

R-28-03c adds native error-state derivation and committed reporting. Integration
on PR #55's tested tree passes 47 core, 48 compiler and 15 Maven tests, plus
**887 bench checks, 169 skipped, across 132 cases**: client 130 reference / 129 JX,
hydrate 99, static 105 and lazy 110 per side. All six class cases pass eight
projects each. Tightened assertions for exactly eight expected caught reports
and one event error pass a further eight focused checks. Pair evidence remains
**54 / 6,328**. The reporting mutant was killed after a passing baseline, zero survived/unusable; the catalog has 127 entries,
without rerunning the historical full campaign of 100. PR
[#57](https://github.com/lmrrcc/jx/pull/57) merged as `6ebfc76` after the 15-step
gate passed in 342 seconds and both Verify checks passed on `baa2da5`.
Production, server-error and broader foreign-value qualification remain open.

R-28-03d composes those lifecycles in direct `.jx` class declarations. The
integrated package passes 47 core, 52 compiler, 15 Maven and 10 LSP tests. The
complete bench passes **895 checks, 169 skipped, across 133 cases**: client 131
reference / 130 JX, hydrate 100, static 106 and lazy 111 per side. All 56 class
cells pass, as do eight focused checks with strict expected-error assertions.
Case evidence adds one consumer; pair evidence stays **54 / 6,328**. The compiler
mutant was killed after a passing baseline on byte-identical source in the
five-mutant candidate campaign `e63ea76` (five killed, zero survived/unusable).
PR [#58](https://github.com/lmrrcc/jx/pull/58) merged as `338872c` after the
15-step gate passed in 352 seconds and both Verify checks passed on `0011c1d`.
Following the source-preserving rebase
onto the Forms-integrated error-lifecycle head, the catalog has 128 entries; the
historical full campaign remains 100. Native IDE qualification is separate.

R-31-01 adds responsive image preload settings and the request referrer policy
through immutable `Resources.Options`, retaining its original constructor.
Integrated source `33a2fae` over merged lifecycle authoring `338872c` passes package
(47 core, two React option, 52 compiler and 15 Maven tests) and **903 bench checks,
169 skipped, 134 cases**: client 132 reference / 131 JX, hydrate 101, static 107
and lazy 112 per side. All 15 reporter checks pass. The two targeted preload
mutants were killed after the shared baseline passed; those files are unchanged
from that qualified source. This closes a bounded option gap without adding pair
credit (54 / 6,328 unchanged). PR #59 merged as `a805810` after its 15-step
gate passed in 436 seconds and both Verify checks passed on `4af20f2`;
no cross-browser or full parity qualification is implied.

R-28-03e adds typed pending context to explicit and authored class update
policies. Integration over merged preload `a805810` passes 51 core, 2 React,
53 compiler, 15 Maven and 10 LSP tests, plus **911 bench checks, 169 skipped,
across 135 cases**: 133 reference / 132 JX client, 102 hydrate, 108 static and
113 lazy per side.
All 64 class cells pass; pair evidence stays 54/6328. Its two mutants were
killed after a passing baseline in the scoped seven-mutant `f49b48f` campaign;
zero survived/unusable. PR [#60](https://github.com/lmrrcc/jx/pull/60) merged as
`3815001` after the 15-step gate passed in 373 seconds and both Verify checks
passed on `251aaa7`. The integrated catalog
contains 132 entries;
the historical full campaign remains 100.

R-28-03f merged in [PR #61](https://github.com/lmrrcc/jx/pull/61) as `a93e325`.
The direct null-patch defect was reproduced against native React before repair.
Final integration passes 54 core, 2 React-option, 53 compiler, 15 Maven and 10 LSP
tests; the complete bench matches **919 passes, 169 skipped, 136 cases**:
134 reference / 133 JX client, 103 hydrate, 109 static and 114 lazy per side.
Its callback-dropping mutant was killed after a passing baseline. The local
15-step gate passed in 348 seconds and both Verify checks passed on `1c23a22`.
Pair evidence remains 54/6328; other class options remain open.

R-28-03g permits custom PureComponent update policies through typed descriptors
and ordinary `.jx` declarations while retaining the actual native Pure base,
default shallow comparison and development warning. Integration passes 56 core,
2 React-option, 54 compiler, 15 Maven and 10 LSP tests; the complete bench passes
**927 checks, 169 skipped, 137 cases**: client 135 reference / 134 JX, hydrate
104, static 110 and lazy 115 per side. All 80 class cells pass. Its scoped
policy-omission mutant was killed after a passing baseline on byte-identical
capability sources; this is one of seven scoped mutations, not a new full-catalog
campaign. The local 15-step gate passed in 311 seconds on `035e6ab`;
[PR #62](https://github.com/lmrrcc/jx/pull/62) merged as `39dd3ce` after both
[PR Verify](https://github.com/lmrrcc/jx/actions/runs/45676165881) and
[push Verify](https://github.com/lmrrcc/jx/actions/runs/45675888499) passed.
The pair register stays 54/6328 and R-28 remains partial.

R-30-01 now has a [Java Server Components proof](../tests/rsc-proof/README.md)
using actual Flight and separate server/client Java programs. Seven Node and four
Chrome checks passed, including suspension, rejection, abort and server-code
exclusion from browser chunks/maps. PR #49 merged at `5fa53c0` after the 15-step
local gate and [Verify](https://github.com/lmrrcc/jx/actions/runs/45563088488) passed. The broad R-30 package,
its capability-ledger rows and authenticated G0 acceptance remain open.
R-30-03 adds typed `ReactCache.cache` functions and `cacheSignal` with eight
differential Node checks. Twelve additional context checks prove Java await/map/
recovery retain each request's cache after external settlement, including a single
promise shared by requests and late subscriptions. A Node observer-error check
preserves uncaught-error reporting and sibling delivery. The combined local RSC
command passes **28 Node and four Chrome checks**; all 16 runtime browser checks
and ten JVM Promise checks also pass. Cache functions support zero/one/two
arguments, and cache lifetimes end on success, failure and abort. This slice
merged in PR #51 as `a7a3f62` after the 15-step gate and both Verify checks passed;
automatic HTTP cancellation, abandoned-request retention
measurement and authenticated G0 isolation remain open.

The direct `.jx` authoring slice (R-28-02a) is rebased onto the merged R-28-01
runtime. It generates typed schemas,
immutable partial patches and native descriptors, with checked class refs in
tags. Its package checks pass 34 core, 48 compiler and 15 Maven-plugin tests;
the language-server reactor passes 10 tests and the new consumer passes all eight
focused React/JX browser projects. The original branch passed 819 checks with
133 skipped across 119 cases. The integrated run over merged PR #48 now passes
**833 checks, 151 skipped, across 123 case IDs**: client **121 reference / 120 JX**,
hydrate **93**, static **99** and lazy **104** per side. Recorded tables match
this integrated run. It adds no pair witness: evidence remains **54 / 5,460**, with
**5,406** active pairs open and **1,443** deferred-feature pairs. The generated
snapshot mutant was killed by `class-authoring` after the unmutated baseline
passed: 1 killed, 0 survived, 0 unusable. PR #50 merged as `3e1db97` after its
15-step gate and both Verify checks passed. That slice's catalog has 116 entries;
the historical full campaign remains
100. The authored subset uses direct, non-generic classes with nested record
props/state; remaining syntax limits are documented in ADR 0006. Native IDE
qualification remains R-28-02b. Advanced lifecycle/context
options and class state preservation remain R-28-03/04. The scope and acceptance
are recorded in [the R-28 slice table](ga-tasks.md#r-28-implementation-slices).

Added 24 November 2026: [starter and fast development](ga-developer-experience.md)
is an explicit product requirement. ST-01–ST-06 refine GA-09/12/13; HR-01–HR-10
refine GA-08/24/25/26/27/28. All are planned. Luca confirmed on 25 November that
the entire React parity track and all G0 proofs precede starter and fast-loop
implementation. These slices do not add completed work to the register or change
the remaining shared-contract/security prerequisites.

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
| GA-03 | Browser async ownership design / R | GA-01 | M1 / S | One HTTP/Elm/loader/Suspense cancellation/error model demonstrated in a spike | 🟡 partial: shared cancellation M1-14, effect-owned resources M1-15 and route proof M1-16 are merged; ADR0008 explicitly separates effect loading from Suspense/server ownership. Shared cache and server/Suspense data-owner qualification remain outside these bounded G0 proofs. |
| GA-04 | Shared contract, codec and exact-value spike / C | GA-03 | M1 / M | One DTO/operation definition; real Spring/browser round trip, decimal/long/null boundaries | 🟡 bounded spike accepted: M1-17/M1-19 merged in PR77 and M1-18 in PR78, each after gate/Verify. Shared response/path contract, generated required String/bool/int codec and explicit long/decimal/presence adapters pass actual Spring/browser tests. Broader generated clients, richer schemas, protocol evolution and G0 review remain open. |
| GA-05 | Component-library JAR ABI spike / L | GA-01 | M1 / S | Independent producer and consumer, no source copying, metadata version failure tested | ⬜ planned |
| GA-06 | Lazy-chunk Java identity spike / R | GA-05 | M1 / M | Lazy route absent initially; domain values/context/callbacks cross safely; no duplicate React. M1-22/M1-23 prove the serialized boundary; independent library consumption and automatic application packaging remain | 🟡 partial |
| GA-07 | React SSR→hydration spike / W | GA-03 | M1 / M | Protected data-dependent route, scoped API/auth, two simultaneous identities, immutable hydration snapshot, abort and no duplicate fetch/mutation | ✅ bounded G0 spike accepted through M1-24–26; PR82 adds real Spring-owned snapshots, retained hydration and suspended-request disconnect cleanup after gate/Verify. Progressive streaming, in-flight backend-fetch abort and production hosting remain GA-22 work. |
| GA-08 | Native editor architecture/performance decision / T | GA-01 | M1 / M | Scale baseline; precise projection limits; source-preserving structural proof or explicit revised design | 🟡 bounded M1-28/M1-29 proofs accepted in PR79: measured SDK100-component baseline, closed rename101 and undo/redo25 with native undo101 limit recorded; original96SDK/ZIP/Verifier and integrated gate/Verify pass. Broader installed-editor qualification and architecture decision remain open. |
| GA-09 | Artifact identities and clean distribution rehearsal / Q | GA-01 | M1–M3 / M | Namespaces/owners verified; external consumer installs candidate artifacts without workspace caches | 🟡 M1-30 local file-repository rehearsal passes: empty Maven cache, all Maven dependencies/plugins file-only, offline rebuild and real Chrome; independent npm provisioning stays online; namespace/publication/signing and Verify remain pending |
| GA-10 | Contract IR and generators v1 / C | GA-04 | M2–M3 / L | Java authority + generated async client/codecs/server boundary/schema; incompatible wire change fails | ⬜ planned |
| GA-11 | Browser Java certification and dependency scanner / J | GA-01, GA-04 | M2–M6 / L | Per-method JVM/browser vectors and transitive unsupported-call diagnostics; size budgets | ⬜ planned |
| GA-12 | Optional Boot starter/BOM and template / S | GA-10 | M2–M3 / L | ST-01–06: React-style welcome, Maven create goal, one contract authority, independent three-command UI/API startup; executable Boot JAR in empty directory, auto-config backoff and route/caching tests | ⬜ planned |
| GA-13 | Authenticated same-origin dev lifecycle / S | GA-03, GA-04 | M2–M4 / L | ST-05 one-command backend/UI readiness and process ownership; session/CSRF/OIDC with hot updates and secure backend-fronting; public demo does not qualify authentication | 🟡 partial: packaged M1-20 and native-session/CSRF M1-21 proofs accepted through PR80/81 after gate/Verify. OIDC, broad identity-generation disposal, timed-expiry/cross-tab recovery and the complete development lifecycle remain open. |
| GA-14 | Grammar, generic/default/forwarded props / L | GA-05 | M2–M4 / L | Typed reusable components, Java-equivalent JSX patterns, recovery/fuzz/mapped errors | 🟡 partial |
| GA-15 | Rich DOM/events/refs and browser schema / R | GA-03, GA-14 | M3–M5 / L | HTML/SVG/IME/pointer/file/ref behavior; safe rich-content contract, CSR/SSR sanitization; wrong types rejected at source | 🟡 partial |
| GA-16 | Nested router and resource cache / A | GA-03, GA-10, GA-06 | M3–M4 / L | Loaders/actions, nested layouts, abort/stale protection, invalidation/optimistic rollback and deep links | ⬜ planned |
| GA-17 | Forms, validation, i18n and accessible primitives / A | GA-10, GA-15 | M3–M5 / L | Complex form, arrays/errors, locale/RTL, keyboard/reader, shared pure rules and server errors | ⬜ planned |
| GA-18 | Stable React API parity / R | GA-03, GA-06, GA-15 | M3–M6 / L | Complete pinned API ledger and differential fixtures. Interaction, class, Flight, cache, utility and class-policy slices merged through PR62 with green gates and Verify. The local header/resume slice passes 911 checks across 138 cases and 62 / 6,328 pairs before subsequent class-policy integration; 6,266 active and 575 deferred-feature pairs remain open. Both scoped header mutants are killed; integrated publication and remaining APIs/options/combined scenarios pending | 🟡 partial |
| GA-19 | HTTP headers/binary/upload/stream capabilities / C | GA-03, GA-10, GA-13 | M3–M5 / L | Auth/CSRF headers, upload progress, download, SSE/WS lifecycle, bounds/reconnect tested | ⬜ planned |
| GA-20 | Real ecosystem binding SDK and catalog / R | GA-15, GA-18 | M4–M7 / L | Selected grid/editor/chart/headless/media bindings with refs/assets/cleanup; Java consumer examples | ⬜ planned |
| GA-21 | Complex authenticated reference product / A | GA-12, GA-13, GA-16, GA-17, GA-19 | M4–M6 / L | Workspace/project workflow with roles, search/table/detail, forms/files/activity and two-tenant tests | ⬜ planned |
| GA-22 | SSG/SSR host, hydration and asset pipeline / W | GA-06, GA-07, GA-16 | M4–M6 / L | SEO website; bounded isolated SSR, data snapshots, hashed assets and cache/status correctness | ⬜ planned |
| GA-23 | Debugging, source maps and observability / T | GA-05, GA-06, GA-07 | M3–M7 / L | Event/async/SSR failures and breakpoints map to .jx; artifact-linked maps and safe logging | ⬜ planned |
| GA-24 | IntelliJ editing and run/debug parity / T | GA-08, GA-14, GA-23 | M2–M7 / L | Native action corpus incl. safe closed-file edits; installed packages, trust, scale and version tests | ⬜ planned |
| GA-25 | VS Code editing and run/debug parity / T | GA-08, GA-14, GA-23 | M2–M7 / L | Class/file rename, formatting, closed-file references, normal navigation and installed-JDT matrix | ⬜ planned |
| GA-26 | TurboJ native .jx integration / T | GA-14, GA-23 | M4–M8 / L | Reuse compiler semantics; native mixed editor/run/debug with own acceptance corpus | ⬜ planned |
| GA-27 | Fast development service and hot-update scale / R | React parity and G0; GA-10, GA-18, GA-23 for full qualification | After G0 / M2 proof; M4–M7 qualification / L | HR-01–10: measured edit-to-paint latency, persistent compiler/TeaVM/bundler, no Maven per UI edit, versioned IDE buffers, compatible checkpoints, bounded caches and no replay/leaks after 100 updates | ⬜ planned |
| GA-28 | Cross-browser/OS/performance/a11y/security CI / Q | GA-01; each delivered feature | M1–M12 / L | Required matrix/payload/latency gates; real-device and manual evidence attached to RC | ⬜ planned |
| GA-29 | Existing Spring/Kotlin and schema-first adapters / C | GA-10, GA-11 | M3–M7 / L | Java21/Kotlin Gradle Boot consumes Maven-built JX contracts/assets in clean CI+IDE; Rust-schema consumer; no forced backend/build-tool rewrite | ⬜ planned |
| GA-30 | Static site pilot / M | GA-02, GA-22, GA-28 | M6–M8 / M | Complete low-risk site with existing URLs/forms/SEO, canary and rollback | ⬜ planned |
| GA-31 | Authenticated SPA pilot / M | GA-02, GA-21, GA-29 | M7–M9 / L | Product-approved Medusa/Stack Sensei route cohort, one session owner, 30-day observation | ⬜ planned |
| GA-32 | Media/payment/finance qualification / M | GA-19, GA-20, GA-31 | M7–M9 / L | Exact values, session races, real-device audio cleanup and provider-sandbox payment recovery | ⬜ planned |
| GA-33 | Extension/Rust-WASM qualification and pilot / M | GA-11, GA-20, GA-29, GA-37 | M6–M9 / L | Packaged MV3 popup/worker/storage/CSP; existing Rust custody preserved; read-only pilot | ⬜ planned |
| GA-34 | API freeze and version-skew/rollback / Q | GA-10, GA-18, GA-22, GA-29 | M9–M10 / M | Stable public/wire/metadata policies, old tab/new backend and retained asset rollback | ⬜ planned |
| GA-35 | RC soak, external onboarding and release rehearsal / Q | GA-24, GA-25, GA-28, GA-30, GA-31, GA-34 | M10–M11 / L | Clean install/upgrade, support/security owners, 30-day pilots, zero blocking findings | ⬜ planned |
| GA-36 | GA decision package / lead + Luca | GA-35; mandatory profile gates | M12 / M | Signed evidence manifest, known limits, artifacts and go/no-go; publication is a separate authorized action | ⬜ planned |
| GA-37 | MV3/CSP/Rust-WASM feasibility / M + R | GA-01 | M1–M2 / S | Actual packaged extension renders JX and calls existing WASM under its CSP; popup close/reopen smoke | ✅ bounded feasibility accepted in PR83 after gate/Verify: three actual Chrome extension checks cover action-popup lifetime, Java/Rust computation and CSP denial. Full extension/privileged-operation qualification remains GA-33 work. |
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
