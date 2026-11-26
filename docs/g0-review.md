# G0 review: browser architecture and the next implementation gates

26 November 2026. **Status: partial; G0 has not passed.** This review uses accepted
main `4e41b15` (PR87) and the separately identified component-library candidate.
It records decisions against the five M1 exit proofs, not a GA support promise.
The remaining prerequisite is acceptance of the library candidate through its
own integrated gate and Verify, followed by publication of this review.

The implementation sequence remains React runtime parity and G0, then the
functional JX/Spring starter and fast development service. Luca's clarification
on 26 November moves advanced native class-state preservation after ST/HR;
`@Component`, static `render` and hooks are the default application model.
This does not waive runtime parity, silently accept an option-level limitation,
or claim that the new fast reload is implemented.

## Decisions on the five proofs

Here **GO** means retain the demonstrated architecture for the next bounded
implementation; **CHANGE** means an evidenced constraint changes that next
implementation. Neither means production qualification. No proof currently
requires abandoning Java-to-React browser execution.

| Proof and tasks | Evidence actually available | Decision and next boundary |
| --- | --- | --- |
| Component library JAR, M1-10–13 | ABI2 candidate `b89db24`: 213 JVM checks, actual TeaVM compilation, outside-checkout source-independent Maven consumer and two Chrome journeys. The producer contains classes and versioned metadata, no producer source. Its 21-step publication gate and Verify are pending. | **GO candidate, acceptance pending.** Keep a bounded versioned metadata contract, source-mapped consumer diagnostics, and normal Maven dependencies. Future lowering metadata must receive an explicit ABI compatibility decision. No implicit class loading, source copying or assumption that every JVM dependency is browser-compatible. |
| Exact shared contract, M1-17–19 | Accepted PR77/78: the same dependency-free Todo record and paths reach Spring and Java UI; generated required String/bool/int codecs; twelve actual Spring/JVM/TeaVM vectors cover long, decimal and missing/null using explicit numeric adapters. [Shared record](evidence/shared-todo-contract.md), [exact values](evidence/shared-exact-values.md). | **GO with a required expansion.** Reuse one portable contract authority; keep server authorization and persistence private. M2 must generate the richer codec and operation/client boundary rather than advertise the existing handwritten exact-value adapter as general generation. |
| Authenticated executable Spring application, M1-20/21 | Accepted PR80/81: resource-only web classifier, Boot JAR launched from an empty directory, real login/session rotation/CSRF/logout and two private workspaces, in packaged and Maven development modes. [Packaging](evidence/packaged-spring-todo.md), [session proof](evidence/session-csrf-todo.md). | **GO for the first CSR starter.** Generalize the tested boundary into optional starter/BOM modules, application-owned security configuration, same-origin development and independent consumer packaging. Context prefixes, session expiry/cross-tab recovery, deployment TLS and production policy remain explicit follow-up work. |
| Lazy runtime boundary, M1-22/23 | Accepted [ADR0005](decisions/0005-lazy-module-boundary.md) and four Chrome scenarios: delayed download, exact encoded values/callback, retained local state, failed download/retry and error propagation. The test also establishes that a separately compiled Java Context does not cross this boundary automatically. | **GO for one shared React with explicit serialized module boundaries; CHANGE the authoring/build plan.** Generate codecs and Maven chunk wiring instead of leaving application authors to duplicate the fixture. Cross-module context identity and loss of Java exception type remain visible parity questions, not newly approved exclusions. Do not pass opaque Java objects between independent runtimes. |
| React SSR and hydration, M1-24–26 | Accepted rendering/hydration work and PR82: independently compiled server/client Java, actual authenticated Spring data, two isolated immutable snapshots, retained hydrated DOM with no refetch, disconnect cleanup and absence of server secrets in client output. [Protected proof](evidence/protected-ssr.md). | **GO for the Node renderer boundary; CHANGE delivery assumptions.** CSR can ship as the proven one Boot JAR. Request-time SSR still requires a separately supervised JavaScript renderer and its packaging/operations contract; the proof is not a JVM renderer or a production SSR starter. Progressive private-data streaming and cancellation during backend fetch still need qualification. |

The library result above is source-specific local evidence from
`docs/evidence/component-library-jar.md` in the selected ABI2 candidate; that
document is not yet on the reviewed main. Its current private consumer has a
producer-only JAR on TeaVM's classpath, and the compiler tests reject missing,
future, stale and duplicate metadata without initializing application classes.
Replace this pending reference with the accepted publication receipt when that
candidate merges. Do not mark M1-10–13 or G0 done from this paragraph alone.

## Supporting G0 evidence and its limits

- M1-14–16 are accepted through PR73/75/76. Cancellation, effect-owned resources
  and route loading have actual stale-completion/disposal tests. They do not yet
  form a general application cache, route action/invalidation layer or Suspense
  data framework.
- [M1-28/29](evidence/intellij-editor-scale.md) retain a 100-component native-Java
  SDK measurement and exact closed-file rename. A 25-file undo/redo proof passes;
  a strongly retained 101-file SDK undo control also fails for stock Java. This
  is a documented qualification limit, not installed-IDE or fast compiler evidence.
- [M1-30](evidence/artifact-consumer.md) proves an outside consumer resolving Maven
  artifacts into an empty cache from a frozen file repository, then rebuilding
  offline and operating in Chrome offline. npm bootstrap and provisioned tools
  have separate network/toolchain assumptions; public signed distribution is open.
- [M1-27](evidence/mv3-wasm.md) proves an extracted MV3 ZIP, real popup lifecycle,
  enforced CSP and a typed Java-to-Rust/WASM binding. Service workers, privileged
  message protocols, persistent extension storage and store release remain open.
- Current browser results are Chrome automation. Production bundle budgets,
  cross-browser/mobile behavior, long-session retention and representative pilot
  routes retain their later gates. Test counts do not substitute for those gates.

This is a review of retained evidence and current contracts/build wiring. It does
not claim a fresh run of those historical suites. The source review additionally
checks the actual shared Todo type, backend dependency exclusions, HTTP header
validation, library bounds/discovery and named browser scenarios.

The ledger review corrects three obsolete descriptions using accepted evidence:
bounded application HTTP headers/CSRF already exist in PR81, bounded native
Writable failure is qualified in PR70, and protected SSR isolation is accepted
in PR82. Their capability statuses remain below general GA qualification; old
test totals remain historical receipts rather than being added together.

## Re-cut M2 and M3 around a usable application

Keep the existing task IDs. The sequence below clarifies dependencies rather than
inventing parallel versions of the same generator or replacing the year's plan.

1. **M2-01–05: one protocol authority.** Define a small portable operation model
   and shared request/response/error values; extend generated codecs to the
   advertised enum/list/presence/exact-number subset. Generate the cancellable
   browser operation and Spring adapter from that model, with real HTTP and
   incompatible-wire-change tests. Keep server-private fields and security logic
   outside the shared artifact. A hand-maintained URL constant plus response codec
   alone is not completion of these tasks.
2. **M2-06/07: reusable Spring hosting.** Extract the proven resource-only assets,
   routing and configurable session integration into an optional starter and BOM.
   Verify configuration backoff and that unknown API/asset paths never become SPA
   HTML. Reuse the application-owned Spring Security chain rather than installing
   a hidden framework identity system.
3. **ST-01–06: functional onboarding.** After runtime parity/G0, start the small
   React-style welcome/counter immediately alongside HR-01 measurement. The full
   contract/web/server template waits for the minimal M2-01–07 boundary above.
   The Maven create goal and tutorials use the same template. An external consumer
   must start, call its real backend, update UI code and package its Boot JAR with
   at most three documented commands; no unresolvable artifact is advertised.
4. **HR-01–10: measured development service.** Record the current loop first;
   qualify asset-only updates, then a persistent compiler process that resolves
   Maven once and builds ordinary edits without Maven subprocesses. CLI and IDEs
   share that service. Preserve compatible functional hook state, count/focus,
   last-good output on compile errors, and typed generation boundaries. Advanced
   native class checkpoints are later work. Existing Maven robustness remains the
   clean-build/release reference, not evidence of low edit latency.
5. **M2-11–13 and M3: expand only with consumer evidence.** Certify advertised
   browser Java methods using shared JVM/TeaVM vectors and unsupported-dependency
   call paths. Add markup recovery, type diagnostics, nested routes/layouts,
   composed `.jx` maps and editor dependency-JAR operations through compiling
   examples. Production SSR supervision, signed distribution and legacy-service
   compatibility retain independent acceptance; none follows from G0 alone.

## Gate closure checklist

- [x] Each of the five proof boundaries has an explicit decision above.
- [x] Accepted publication receipts and local-only library evidence are separated.
- [x] Runtime, editor, distribution and hosting limits retain their later gates.
- [x] M2/M3 and ST/HR dependencies reflect functional component priority.
- [ ] Library ABI2 passes its integrated 21-step gate and Verify and merges.
- [ ] This record links that accepted receipt and updates the ledger/task flags.
- [ ] The review's own PR passes the repository gate and Verify and merges.

Until the last three items are checked, M1-31 and G0 remain **partial**. Closing
G0 later will not complete R-27–R-31, ST/HR, G1 or GA.
