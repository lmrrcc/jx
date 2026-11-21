# Portfolio migration inventory and acceptance plan

Initial read-only inventory, 19 November 2026. Parent: [GA roadmap](ga-roadmap.md).
The paths below are relative to the named sibling repositories in the local
workspace. They are evidence pointers, not files bundled with JX. No application
was built, deployed or certified by this inventory; no secrets/runtime data were
needed. Confirm actual deployed variants and product owners before migration.

## Observed targets

| Target / evidence | Observed stack and requirements | First candidate / deferred high-risk work |
| --- | --- | --- |
| Medusa: `frontend/package.json`, `frontend/src/App.tsx`, `docs/live/agent-context.md` | React 18/Vite/TypeScript, Kotlin/Spring API with Rust services; nested workspace/account/portfolio/governance routes, forms, charts, Markdown and transaction state | Read-only account/activity route; governance and transaction submission later |
| Medusa identity: `frontend/src/api/client.ts`, `frontend/src/auth/session.ts`, `frontend/src/hooks/useLongPollingEvents.ts` | Same-origin requests, refresh cookie/in-memory token, serialized refresh, tenant generations, AbortController, BroadcastChannel, navigator.locks and cancellable long polling | Session-expiry/tenant-switch pilot; one session owner shared with old UI |
| Medusa signer: `browser/README.md`, `frontend/src/utils/extensionBridge.ts` | Separate React 19 MV3 extension and origin-bound messaging; distinct from web frontend | Read-only extension/settings after extension qualification; signing flows last |
| Gandalf: `gandalf-wallet/extension/package.json`, `gandalf-wallet/extension/public/manifest.json`, `gandalf-wallet/extension/src/background.ts`, `gandalf-wallet/extension/src/shared/wasm.ts` | React 19 MV3 popup/service worker, Rust/WASM, Chrome storage/messaging, IndexedDB, QR, background suspension, CSP | JX popup/settings/read-only account screen inside existing package; retain service worker and signing core initially |
| Gandalf protocol: `gandalf-wallet/wallet-chain-api/README.md`, `gandalf-wallet/README.md` | Rust/Axum API with canonical JSON Schema/DTO fixtures, exact decimal strings and opaque cursors; separate native clients | Generate JX contracts from existing authority; no Java backend or crypto rewrite |
| Stack Sensei: `frontend/package.json`, `frontend/src/App.tsx`, `backend/build.gradle.kts` | React 18/Vite, Kotlin/Spring Boot 3.2/JDK 21, subscription-gated routes, code/math/Markdown, charts/maps, PDF/canvas, lazy payment route, error monitoring | Profile/session list → text session; respect older server JDK and Kotlin contracts |
| Stack Sensei media/payment: `frontend/src/services/narrationService.ts`, `frontend/src/services/voiceActivityDetectionService.ts`, `frontend/src/pages/PaymentPage.tsx` | Streaming response readers, audio gesture policies, getUserMedia/AudioContext/MediaRecorder and Stripe Elements | Media permission/cleanup and streaming slice after text; payments only with provider sandbox and failure/recovery qualification |
| `lmrr.cc`: root `README.md`, `package.json`, `react/package.json` | Documented Eleventy blog/static workflow; separate React variant exists but its production role is unconfirmed | Static content section preserving Markdown, URLs, code/media, canonical metadata and no-JS readability |
| `lucasoft`: `README.md`, `package.json`, `src/entry-server.tsx`, `server/forms.ts` | Folder contains Stacktrace consultancy site: React 19 prerender/hydration, bilingual routes, shared validation, edge forms/uploads | Complete small SSG/hydrated site after forms/SEO qualification; preserve edge API boundaries |
| Lumaspect: `web/README.md`, `web/package.json`, `inspect/README.md` | Prerendered bilingual site, Three.js/WebGL with static fallback, edge forms; separate React operator console with Kotlin/Spring, Rust acquisition and C++ vision | Website content then qualified 3D; console is a separate workflow pilot, not a backend rewrite |
| Gandalf public sites: `gandalf-system/README.md`, `gandalf-website/README.md`, `gandalf-protocol/package.json` | Static HTML/CSS/JS, canonical design-system assets, forms | Low-risk preview section using JX SSG; preserve no-runtime content delivery |
| `juglugano.ch` | Empty directory in this checkout | Inventory pending; no inferred stack |

`stack-sensei` and `stack-sensei-repo` are different checkouts. The first is the
working inventory baseline; canonical repository and deployment ownership must
be confirmed before changes. Other websites, Tetra surfaces and any unlisted
frontend are **not yet inventoried**. The inventory must be completed before
promising that all frontends can retire.

Rough source-tree counts observed by this audit were 375 TS/TSX files in Medusa's
main frontend, 163 in Stack Sensei and 114 nongenerated files in Gandalf's extension.
These are scale indicators only; route/workflow acceptance, not line-for-line
translation, determines migration effort.

## Three profiles, shared platform

1. **SPA/application:** identity, domain contracts, data lifecycle, routing, rich
   forms, browser capabilities and operations. Spring is a first-class integration,
   while Rust/Kotlin/other API hosts remain supported through schema adapters.
2. **Website:** SSG/SSR/hydration, content/assets, metadata, localization, forms and
   SEO. Migrating static content to an empty client shell would be a regression.
3. **Extension:** packaged CSP, permissions, worker/popup lifecycle, messaging,
   browser storage, WASM and upgrade semantics. Browser-SPA qualification does not
   establish extension compatibility or store acceptance.

Preserve Rust/WASM key custody, signing, zeroization and vault responsibilities.
Native iOS applications and industrial acquisition/vision systems are outside this
frontend migration scope. Any change to them requires its own explicit project.

## Required binding catalog

Prioritize from actual target dependencies, choosing one representative library
per category first rather than cloning an entire npm ecosystem:

- Accessible headless controls, modal/portal, selection, virtualized table and grid.
- Charts, QR, Markdown with sanitization, code editor/highlighting, math/PDF/canvas.
- Stripe Elements and its provider/context/ref/promise lifecycle.
- MediaRecorder, AudioContext, streams, permissions, WebGL/Three.js and cleanup.
- Browser extension messaging/storage/alarms/notifications/identity, IndexedDB,
  BroadcastChannel, navigator.locks and Rust/WASM binary/promise adapters.
- Error monitoring/source maps, animation and reduced-motion support.

Bindings need schema-defined props/events/refs/children, asset/CSS handling,
ownership/disposal, exact value conversion, error translation and versioned peer
dependencies. Framework-maintained adapters may use JS/TS; ordinary product logic
should remain Java. Document the escape hatch for an unbound library and turn
repeated product-specific adapters into maintained bindings after real use.

## Route migration record

Create one record per bounded workflow with:

| Field | Required evidence |
| --- | --- |
| Identity | Product, route/journey ID, current source/backend revision, owner and actual deployed variant |
| Baseline | Required behavior, data/error cases, screenshots where useful, a11y tasks and measured performance |
| Dependencies | API/schema version, browser/React/library needs, auth owner, persistence, feature flags |
| JX proof | Equivalent executable journey, generated contracts, installed package versions, qualification profile |
| Cutover | Routing ownership, opt-in cohort, traffic/monitoring window, operator and success/abort thresholds |
| Rollback | Prior asset manifest and frontend, compatible backend range, cached chunks, drafts/storage/session treatment and rehearsal result |
| Retirement | Product-owner acceptance, operational window complete, no remaining required old-UI path |

One router owns each path; one authority owns each session/store. Coexistence across
different React majors (React 18 in Medusa/Stack Sensei, React 19 in newer surfaces)
needs a deliberate choice: upgrade the host after regression tests, or use separate
roots/routes with explicit message/data boundaries. Never assume shared context
works across incompatible React copies. No opaque Java object interchange between
independent TeaVM runtimes.

## Product gates beyond framework GA

**Medusa:** tenant switch/logout cancels and invalidates work; exact quantities and
pagination; long-poll cleanup; permission and governed review flows preserve server
authorization. Unknown mutation outcome has a reconciliation path, not blind retry.

**Stack Sensei:** a session survives/recoverably fails during disconnect and route
changes; microphone/audio resources stop; mobile/Safari permission behavior is
verified; payments do not double-submit and provider/server status is authoritative.
Retain existing acceptance suites and distinguish sandbox from production evidence.

**Gandalf:** locked-wallet behavior, message origin/schema validation, popup closure,
worker suspension, permission revocation and storage/vault schema compatibility
must pass. Signing intent/review and operation cancellation require separate
product/security review; a UI rewrite cannot certify cryptographic safety.

**Websites:** existing URLs, redirects, content, languages, forms, sitemap/canonical
and metadata remain correct; no-JS content readable; hydration stable; 3D fallback,
reduced motion and resource cleanup work. Existing edge functions may remain hosts.

Use preview → bounded opt-in → monitored canary → broader rollout, with explicit
approvals for external deployment. Do not shadow state-changing requests. Backend
schema changes remain additive until both frontends no longer require old fields.
Retire an old frontend only after rollback, storage/draft compatibility and product
acceptance, not simply after counting migrated routes.

Year-one target: qualify all three profiles; operate a complex authenticated pilot
and a complete public site; prove a production-shaped extension integration.
Track full portfolio retirement separately—it may extend beyond the GA year.
The signed G4 portfolio gate includes media/finance/extension qualification;
G3 application/website GA does not substitute for it. Static-only pages should
preserve zero-JavaScript output unless interaction justifies opt-in hydration;
existing transfer/runtime cost is a product cutover gate.
