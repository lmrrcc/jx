# GA qualification and release evidence

Proposed acceptance contract, 19 November 2026. Parent: [GA roadmap](ga-roadmap.md).
All numerical targets below are **initial budgets to validate in month 1**, not
measurements of the current implementation. Changing a budget needs a recorded
reason and workload evidence; do not relax it merely to obtain a green result.

## Evidence ledger

For each advertised feature record: capability ID; owner; implementation revision;
consumer fixture; test command and CI URL/artifact; exact dependency/platform
versions; expected outcomes; negative/failure cases; measured limits; date; status.
Statuses: proposed, spike, implemented-unqualified, qualified, blocked, deprecated.
Only qualified capabilities belong in the stable support promise.

The ledger is a planned machine-readable release input, not yet an implemented
tool. Start from the checked-in runtime/tooling matrices; stop using raw test counts
as the principal measure of readiness. A flaky required test is a blocker until
fixed or shown to be an unrelated environment failure with retained evidence.

## Capability matrix

Applicability is explicit: the **Website** row qualifies the website profile;
**Extension** qualifies the extension profile; media/payment/finance journeys
qualify the named portfolio migrations. Other rows qualify the common application
platform, with rendering-specific cases also required for websites. G3 requires
application and website gates; G4 additionally requires the portfolio/extension
gates. A profile may not be advertised before its own evidence is complete.

| Gate | Mandatory examples and failure cases within the applicable profile |
| --- | --- |
| Compiler/authoring | Generic table/form component, defaults/optional fields, typed forwarding, render props, dynamic component selection, nested fragments, keys and SVG; comments/text blocks/Unicode; incomplete edits; invalid props/ref/events mapped to source |
| Libraries | Build a component JAR in project A; consume from clean independent B without producer sources; version metadata and reject incompatible binaries; declarations/navigation work from dependency artifacts |
| React | Differential JS/TSX vs Java fixtures for snapshots, queued updates, identity, Strict Mode, cleanup and error boundaries; advanced hooks/refs/actions/Suspense/lazy/portal behavior under interruption and rejected work |
| Browser events | Pointer, keyboard modifiers, touch, IME/composition, focus/selection, clipboard, drag/drop, file input and currentTarget; preventDefault and propagation; memory/resource cleanup |
| Routing/data | Deep links and real 404s, nested layouts, route loaders/actions, back/forward, blockers, scroll/focus, cache invalidation, stale/out-of-order replies, optimistic rollback and concurrent mutations |
| Shared contracts | JVM↔real browser golden vectors, actual Spring JSON, large IDs/money/time, missing/null, unknown enums/unions, oversize/deep payloads, cross-version reader/writer and source provenance |
| Spring/security | Real authenticated browser+Spring HTTP tests, session expiry/refresh/logout, CSRF missing/invalid tokens, two users/tenants, horizontal/vertical access denial, rate/payload bounds, no API→HTML fallback |
| Browser libraries | Identical seeded JVM/TeaVM vectors for every advertised method; dependency path for unsupported methods; bundle-size impact; no server-only closure |
| Media/transports | Multipart/binary, abort/reconnect, SSE ordering, WebSocket slow consumer/backpressure, microphone denied/revoked, audio gesture restrictions, recorder dispose and route change |
| Website | Static HTML content/metadata without JS, localized canonical/hreflang, sitemap/robots/redirects, SSR status/cache isolation, hydration IDs/data match, chunk failure recovery and SSR abort |
| Development | Save→working update, compile failure leaves old app usable, incompatible state reset explained, compatible drafts preserved, no stale runtime objects or mutation replay, repeated updates and process cleanup |
| Editors | Installed VSIX/ZIP; Java+markup completion, diagnostics, imports, navigation, references, rename/file moves/undo, formatting/typing, closed-file edits, dependency-JAR symbols; safe rejection of unsupported structural edits |
| Debugging | `.jx` breakpoints/step/stack traces in browser and supported IDE workflow, event/effect/async errors mapped; source maps correspond to deployed artifact and are not publicly exposed by default |
| Extension | CSP without eval/remote executable code, popup/worker lifecycle, persistent storage compatibility, permissions, origin/message schemas, typed Rust/WASM calls and cleanup; actual packaged extension test |
| Release | Fresh machine uses published artifacts; checksum/signature verification; repeatable production build; old/new deployment and rollback; documented support and ownership |

The React ledger must enumerate **all stable APIs in the pinned package**, including
newer form/action, effect-event and visibility capabilities where present. Every
item needs supported, replacement-with-equivalent-outcome, or explicitly excluded
status and justification. Required migration workloads cannot be excluded merely
because their bindings are difficult. RSC/framework protocol decisions remain
separate and visible in the roadmap.

Minimum API inventory: foundation hooks and context; `useId`, imperative refs and
ref props; `memo`, Profiler, StrictMode; portals; transitions/deferred values;
Suspense/lazy and promise consumption; action state/optimistic/form status;
insertion/effect-event/debug hooks and Activity where stable; element/children
utilities; DOM root/hydration/resource-hint APIs. Record exact version semantics,
idiomatic Java equivalents and why any omission does not block a promised workflow.

## Environments

| Dimension | Proposed coverage |
| --- | --- |
| Build/JVM | JDK 25 at baseline, pinned Maven Wrapper and Node build tools; Linux and macOS continuously, Windows build/install qualification before GA |
| React/TeaVM | One stable pinned pair initially; upgrade branch runs all conformance fixtures. No broad range based only on binary compilation |
| Browsers | Current and previous supported Chrome/Edge, Firefox and Safari releases at GA; Chromium/Firefox/WebKit CI plus real Safari/iOS and Android device checks for media/input |
| Spring | Start from current Boot 4.1.2 example; separately qualify the actual Boot 3.x/Kotlin/JDK 21 estate or its explicit upgrade plan. Publish versions that pass, not a wildcard |
| Editors | Current verified IDEA build and VS Code/Red Hat Java pair first; qualify current stable and one retained support line where feasible. Windows/Linux/macOS installation, update and trust tests |
| Hosting | Boot JAR/container, static/CDN hosting, reverse-proxy context path; separately supervised SSR host; extension package. Each has its own rollback/compatibility evidence |
| Extension hosts | Explicit current Chrome/Chromium MV3 baseline first. Firefox/Safari extension support requires independent packaging, permission, lifecycle and store-policy qualification; web support does not imply extension support |

Version choices are refreshed at the monthly review and frozen for an RC. Simulated
WebKit is useful but does not replace testing actual Safari media/extension behavior.
Remote development and TurboJ receive their own qualification rows; they do not
inherit desktop IDEA or VS Code results.

## Performance and reliability budgets

Create small, medium and large checked-in scale fixtures: approximately 10, 100
and 1,000 components; large typed props/library dependencies; 10,000-row virtualized
data; long text/forms; streaming updates. These are workload definitions, not
targets for rendering thousands of DOM nodes unnecessarily.

Record hardware, OS/JDK/browser, CPU/memory limits, network/cache state, commands,
sample counts, p50/p95, failure counts and comparison revision. Proposed lab setup:
a recorded 4-core/8-GiB CI allocation and a named midrange mobile device, 10 Mbps /
100 ms network profile. Recalibrate during G0 if not reproducible. Compare equivalent
production React and JX features; never compare minified React to development JX.

| Measure | Initial target / release rule |
| --- | --- |
| Editor input latency | p95 below 50 ms; no repeatable UI-thread stall above 100 ms on medium fixture |
| Warm completion | p95 below 250 ms; cancellation does not leave stale results; large fixture tracked separately |
| Warm save→usable update | p95 at most 2 s small / 5 s medium; disclose full rebuild/reset cases; no claim based on projection-cache lookup alone |
| Cold build | Medium fixture at most 60 s with dependencies cached on reference machine; network/bootstrap timings reported separately |
| Initial production JS | Provisional 250 KiB gzip content-site route / 500 KiB authenticated app shell, including React + TeaVM/runtime; heavy editors/charts/media route-loaded and budgeted separately |
| User experience | Field p75 LCP ≤2.5 s, INP ≤200 ms, CLS ≤0.1 for qualified routes; lab proxies before sufficient field data, clearly labeled |
| Lazy routes | Heavy route code absent from initial requests; load/cache/error/retry exercised; no duplicated renderer or incompatible Java runtime identity |
| Steady-state memory | After warmup and 100 navigate/mount/unmount cycles, retained-heap growth ≤10% and no monotonically growing listener/timer/store count; 100 hot updates separately tested |
| Soak | 8-hour automated representative session plus 30-day pilot observation; bounded resources during reconnects, repeated auth changes and failed builds |
| Frontend errors | No uncaught failures in required scripted journeys; pilot error-free sessions target ≥99.9%, with denominator/traffic and known backend outages reported separately |

Thresholds do not imply all devices will meet them. Severe regressions against the
existing migrated product block its cutover even when a generic framework budget
passes. Keep raw traces and scripts. Performance waivers must name the route,
reason, owner and remediation; a waived required profile is not fully qualified.

## Security, accessibility and failure testing

Security review follows actual trust boundaries: browser data and route visibility
never authorize server operations. Test XSS/unsafe URLs/HTML boundaries, CSP and
bootstrap escaping, cross-tenant caches, session refresh races, CSRF, malicious
uploads, SSR request leakage, path traversal/SPA fallback, credentialed dev proxy,
extension message origins and dependency supply chain. Fuzz parsers and generated
codecs with bounds. Separate untrusted editor files from build-script execution.

Ship a security contact, supported-version policy and advisory process; remove
secrets from artifacts/logs/maps, generate an SBOM/license inventory, verify
dependencies and release provenance. Existing editor packaging advisories need
fresh assessment and remediation/disposition before release; current local ZIP
verification is not a security audit.

Use WCAG 2.2 AA as the product accessibility target. Automated checks plus manual
keyboard/screen-reader tasks: focus trapping/restoration, dialogs/portals, live
regions, form errors, IME, reduced motion, zoom/high contrast, touch targets and
loading states. A core widget library should ship accessible behavior; consuming
products still require their own audit.

Required recovery drills: dropped network during a mutation; timeout with unknown
server outcome; duplicate clicks; concurrent edits; expired identity mid-stream;
tenant switch during requests; stale cached chunks after deployment; service-worker
upgrade; renderer crash/timeout; invalid hydration bootstrap; disk/storage quota;
extension suspension; incompatible hot checkpoint. Retain visible typed recovery
paths rather than treating errors as empty successful data.

Service-worker install/update and offline mutation queues apply only to the opt-in
PWA profile or products already using them, delivered through GA-38. Ordinary
application GA still requires honest network-loss recovery and bounded browser
storage, but does not claim every server operation works offline. Extension worker
suspension is a separate mandatory extension-host case. Rich Markdown/HTML output
needs a safe parsed-node or sanitized-content API with identical CSR/SSR policy,
malformed content/unsafe URL/SVG cases and CSP/Trusted Types tests.

## CI, distribution and support

The current [Verify workflow](../.github/workflows/verify.yml) runs the framework and
Chrome checks; [editor packaging](../.github/workflows/tooling.yml) is manual and
does not run the actual VS Code UI harness. They are starting points. Planned tiers:

- PR: compiler/JVM/protocol/security fixtures, relevant browser checks, API/wire diff,
  deterministic generation, dependency leakage, installation smoke for changed tooling.
- Nightly: full browser/OS/editor matrix, scale/fuzz/soak, upstream compatibility,
  SSG/SSR/extension, real databases for the supported Spring example profile.
- RC: clean artifact consumption, complete parity ledger, license/SBOM/provenance,
  manual accessibility/device evidence, migration/rollback, documentation walkthrough.

Release one coherent version manifest for framework artifacts, compiler metadata,
browser bridge/npm dependencies and editors; they need not share identical version
numbers, but supported combinations must be explicit. Verify Maven Central group,
npm scope and marketplace publisher ownership; signing, reproducibility, notices,
changelog and install/update/uninstall must be tested before public publication.

Proposed support policy for review: SemVer after 1.0, additive changes in minors,
no breaking wire/generator changes in patches, deprecation for at least two minors
and six months where possible, security fixes for current stable and one explicitly
retained line. Name the human release/security/on-call owners before promising
response SLAs. Critical advisories should trigger immediate triage with an agreed
coverage plan; an unattended agent is not an incident owner.

GA sign-off records product approval (Luca), technical maintainer, qualification
reviewer and security/release owner, with links to evidence and known limitations.
Open blocking issues mean **no-go**. A narrower profile may release only with an
explicit scope decision and accurate naming; it cannot stand in for the requested
complete-application GA claim.
