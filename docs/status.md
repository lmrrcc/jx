# Implementation status

This is an initial, executable implementation of the
[v2 specification](JX_Astra_Max_Prompt_v2.txt), with a deliberately documented
compatibility profile. It is not a promise of every Java library, React feature,
browser, or editor distribution.

The [GA roadmap](ga-roadmap.md) defines the next year of work, measurable release
gates and portfolio migrations. Its planned capabilities are not delivered status.

## R-28-01 update — 25 November 2026

The first class runtime slice creates actual React `Component`/`PureComponent`
instances backed by typed Java peers. Explicit schemas and partial patches support
native queued updates, snapshots, lifecycle callbacks and checked instance refs.
Two compiling consumers prove queue/commit behavior and keyed Pure reordering
without replacing DOM nodes, class refs or state, or rendering unchanged fields.

The package passes 34 core, 41 compiler and 15 Maven-plugin tests. The complete
integrated bench over SSR merge `7759933` passes **825 checks**, with 151
inapplicable cells across 122 case IDs: client 120 React / 119 JX, hydration 92
per side, static HTML 98 per side and lazy route 103 per side. Both class cases
pass all eight projects. The two class witnesses bring this branch to
54 / 5,460 active pairs; 5,406 remain open.
The expanded denominator includes the newly active PureComponent feature.

R-28 remains partial: `.jx` class authoring, advanced lifecycle/context options
and class hot checkpoints are still planned. Class hot replacement resets state.
All three targeted mutants were killed after the isolated baseline passed,
with no survivors or unusable mutants; push gate and Verify are
pending. These results are Chrome evidence, with no new human qualification.
See [the class decision](decisions/0006-java-class-components.md) and
[task slices](ga-tasks.md#r-28-implementation-slices).

## R-26 update — 24 November 2026

The conformance bench now runs client, hydration, static HTML and lazy-route
modes. A complete local run passes **715 checks**, with 133 inapplicable cells
across 106 case IDs:

| Mode | React reference | JX |
| --- | --- | --- |
| Client (including Node API cases) | 104 / 104 | 103 / 103 |
| Hydration | 79 / 79 | 79 / 79 |
| Static HTML | 85 / 85 | 85 / 85 |
| Lazy route | 90 / 90 | 90 / 90 |

The lazy checks verify a real deferred download, absence of case code from the
initial TeaVM program, loading UI, download failure and explicit retry. Generated
DOM/event comparisons use the same mode on both sides. The inventory also handles
Node's synthetic CommonJS export marker without adding it to the React API list.

The scoped mutation run killed both new defects (`lazy-route-eager` and
`lazy-host-includes-cases`), with zero survivors or unusable mutants. The latter
is caught specifically by `lazy-route-loading`'s TeaVM source-map check. This
extends the curated catalog to 102 entries; the previous 100-entry full result
was not regenerated and must not be described as a fresh 102/102 campaign.

The first complete rerun hit an existing hydration test's 20-second timeout with
five workers; the bench now caps concurrency at two workers and retains the same
timeouts and assertions. The final complete run passed. This is Chrome evidence,
not a performance or cross-browser qualification.

R-26 is **complete**: Luca's try-it run on 24 November reports the same 715 passes,
133 skipped cells and an exact match with the recorded results. Code revision
`eb5a18a` passed [Verify](https://github.com/lmrrcc/jx/actions/runs/45372691183).
R-27 is the next React parity task. `npm run test:bench` runs the full matrix after the Maven build.
M1-22/M1-23 already proved the serialized module boundary; automatic Maven chunk
packaging remains open. G0 remains open. The dated review below retains its
earlier evidence baseline; this update supersedes its rendering-mode counts and
its statement that the lazy boundary is still unimplemented.

## Progress review — 23 November 2026

The client React runtime has advanced substantially. The full-stack GA foundation
is still incomplete: **G0 has not passed**. This review checked source at `2667d2c`
and incorporates the test-tooling record merged as `52bcd2f`. Code revision `2667d2c`
has a successful [Verify run](https://github.com/lmrrcc/jx/actions/runs/45015947179),
the latest application evidence used here. This review did not rerun application
builds, browser tests or installed-editor tests.

| Evidence | Recorded result | What it establishes |
| --- | --- | --- |
| [React feature register](evidence/conformance.tsv) | 120 rows: 89 supported, 15 equivalent, 0 unsupported, 16 excluded, 0 untested (updated 24 November) | Coverage of the listed API shapes; individual rows still have option and environment limits. |
| [Differential bench](evidence/conformance-results.tsv) | 104 cases: React reference 104 pass; JX 103 pass, 0 unsupported, 1 not applicable. In hydrate mode 79 cases and in static HTML mode 85 cases apply and pass on both sides (updated 24 November) | Matching outcomes for the exercised scenarios. The not-applicable case inventories React exports. JX server rendering is not yet implemented. |
| [Curated mutation results](evidence/mutation-results.tsv) | 100 of 100 mutants detected | Detection of the listed hand-written defects; this is not PIT or exhaustive mutation coverage. |
| Foundation tasks | GA-01 and GA-02; M1-01 through M1-05 complete | Green baseline, version/evidence checks, portfolio inventory and differential bench. |
| React implementation tasks | 26 complete | R-01–R-25 and R-32 complete (R-20–R-25 on 24 November). This audit found R-16 without its promised `useFormState` alias; the alias and a bench case were added the same day. |

These counts are **not a percentage of GA completion**. Of the 16 excluded feature
rows, only the build marker and React Compiler are intended permanent exclusions;
the others remain assigned work under the all-React-features goal.

| Area | Delivered | Still required |
| --- | --- | --- |
| Language and React client | Typed/default/optional/generic props, typed forwarding, events, memo, callback/component refs, portals, transitions, Suspense, Java promises/async, lazy component factories, Activity/ViewTransition, actions/optimistic state, resource hints | R-19 root options; complete HTML/SVG schema; class components; remaining API options, rendering modes and combined scenarios. `lazy` does not split the bundle. |
| Application framework | Counter and todo browser apps; Elm store, routing, bounded JSON HTTP; compatible hot-state checkpoints | Unified async ownership, nested routes/loaders/cache, complex forms, uploads/streaming, authentication and production-scale evidence. |
| Shared Java and Spring | Browser Java profile and runnable Spring todo API | One contract definition with generated clients/codecs, exact-value round trips, certified standard-library methods, optional starter/BOM, packaged authenticated UI/API and same-origin sessions/CSRF. |
| Server and distribution | Browser assets and local snapshot artifacts | Actual JX SSR/SSG/hydration/streaming/resume, RSC feasibility/protocol, safe lazy chunks, independent component JAR consumers and clean artifact installation. |
| Editors | IntelliJ and VS Code packages with native Java integration, mixed highlighting and dev launch | Scale/latency and native-action qualification, broader version matrix, closed-file edits and composed `.jx` browser debugging. TurboJ integration remains planned. |
| Qualification | Verify, Chrome bench, curated mutations; Cherry Test unit coverage/CRAP baseline | Browser/integration orchestration, screenshot baselines, manual gallery, PIT setup, root-wrapper discovery and agreed quality thresholds; broader browsers, security, performance and release evidence. |

At this review, the scheduled feature was **R-26**; R-19 to R-24 were added on 23 and 24 November. Of the five G0 proofs,
React SSR→hydration has its first two tasks done (M1-24, M1-25; M1-26 is open). Four remain
open: external component JAR, exact shared contract, authenticated packaged
Spring/dev-origin prototype and safe lazy-runtime boundary.
Progress on later React tasks does not close those foundation gates.

The portfolio inventory covers **21 browser surfaces and 40 journeys**. Selected
website pilots are `lmrr.cc`, `stacktrace.cc` and `luca4j.com`; the application pilot
is a separate Stack Sensei implementation with one-to-one behavior. Existing
products come after the bench and platform proofs. No migration is complete, and
the existing Stack Sensei site is not to be changed for the pilot.

## Delivered boundaries

| Boundary | Implementation and evidence |
| --- | --- |
| Java/browser feasibility | Java 25, TeaVM 0.16.0, actual React/React DOM 19.3.1; real Chrome tests include offline clicks, independent instances, captured snapshots, and cleanup. |
| `.jx` compiler | Java lexical scanning plus javac component metadata; typed record props, markup lowering, fragments/children/keys, source maps, original-source syntax/type diagnostics, and practical hook-placement checks. |
| Maven | `generate`, `generate-test`, `check`, `check-test`, `browser`, and `dev`; output ownership, TeaVM and locked bundling, isolated development assets, atomic successful-build publication, state-preserving updates, and optional loopback API proxy. |
| Runtime | Typed components/events/hooks, memo, context, refs, portals, transitions, Suspense, async/lazy factories, actions, error boundaries, resource hints and explicit React interoperability. Raw HTML uses the explicitly unsanitized `RawHtml.unsanitized` API. |
| Elm | Immutable transitions, pure updates, serialized committed store snapshots, keyed commands/subscriptions, cancellation, stale-response protection, and Strict Mode lifecycle tests. |
| Browser services | Same-origin asynchronous bounded JSON HTTP, typed outcomes, cancellation/timeouts, history, route parameters, query strings, and back/forward navigation. |
| Shared LSP | Compiler diagnostics, component/property completion, declared Java type hover, definition navigation, symbols, incomplete markup, UTF-16 positions, cancellation, and version handling. |
| Editor packages | IntelliJ 0.3.2 adds a Run gutter action for `.jx` classes, alongside projection reuse and native theme presentation; VS Code remains 0.2.0. Both integrate native Java engines and module-aware development start/stop; tested operations and limits are in the [tooling guide](tooling.md). |
| Complete example | A Java/.jx todo application with controlled forms, keyboard submission/focus, custom hook, context, MVU workflows, async states, routing, filters, and Spring Boot persistence. |

`jx-core` has no Spring, database, or browser implementation dependency.
`jx-react`, `jx-browser`, `jx-router`, and `jx-elm` provide the exercised browser
boundaries. The Spring integration is a runnable example API, rather than an
empty starter module. The [interop consumer](../tests/runtime-browser/src/main/java/jx/fixture/RuntimeApplication.java)
and its framework-owned JavaScript host prove both binding directions; individual
third-party React libraries still need their own typed bindings and tests.

## Verification

The root README gives the combined build and browser commands. Maven includes
negative compiler fixtures, mapped javac errors, source deletion/rename ownership,
pure model and Elm lifecycle tests, real LSP JSON-RPC tests, and an actual HTTP/H2
restart test. Playwright exercises the initial proof, runtime contracts, generated
counter, complete todo application, and real Maven-driven `.jx` edits in Google Chrome.

The backend test proves that invalid fields produce a structured 400 response,
successful updates increment a version, stale updates cannot overwrite them, and
records survive application/database restart. The end-to-end app test verifies
creation, completion, deletion, direct routed reload, back/forward, controlled
input, keyboard submission, focus, loading, server failure, and retry.

The npm dependency tree contains one deduplicated React 19.3.1 installation shared
by React DOM and the Java bridge. Hot development generations reuse one renderer
vendor bundle, avoiding repeated React root/document listener registration.
Framework JavaScript/TypeScript and explicit
interop/browser fixtures are separated from Java/.jx application logic.

On 2026-11-18, the fourteen-module Maven reactor install passed after the
native editor and `@Component` changes. The Java test run contained 69 passing
tests; all 17 Chrome tests passed. Browser checks include source edits during a build, compile failure and
recovery, runtime failure and checkpoint recovery, incompatible-hook resets,
unchanged document identity, stable renderer listeners, and Todo draft/model/focus
preservation without replayed HTTP. Packaged VS Code and IntelliJ evidence is
recorded separately in the tooling guide. These counts describe the checked
revision, rather than replacing the commands and assertions in the repository.

On 2026-11-21, the [GA baseline record](evidence/baseline.md) found commit `78c9836`
red in CI because of development edits left in the counter example. With the
example restored, a clean-directory run passed 74 Java tests and 17 Chrome tests.
The [version manifest](evidence/versions.tsv) and [capability ledger](evidence/capabilities.tsv)
are checked by `java tools/CheckBaseline.java`.

## Development build measurement

Before the hot-reload change, one warm, unchanged-input todo rebuild on macOS ARM64, Apple M5, Corretto
25.0.4.1, with dependencies already cached and other development tools open,
took 5.16 seconds wall time:

```sh
/usr/bin/time -p ./mvnw -q -ntp -Dmaven.repo.local=.cache/maven-repository -f examples/todo-app/pom.xml package
```

That command includes semantic checking, TeaVM, the model test, and bundling. It
is a development rebuild observation, not a cold-build or browser-performance
benchmark. The unminified development application bundle measured 1,998,173 bytes
(334,019 bytes with local `gzip -c`); its separate source map measured 3,148,911
bytes. React development checks and readable generated JavaScript remain enabled.
Production minification, code splitting, runtime performance, and loading budgets
need their own measurements.

## Current limitations and next work

1. Treat the documented [language](language.md) and [browser profile](runtime.md)
   as the supported subset. Arbitrary Java control-flow verification, reflection,
   JVM threading, blocking I/O, and server libraries are not browser capabilities.
2. Source locations for compiler and IDE diagnostics are implemented. TeaVM and
   esbuild emit their own maps. End-to-end `.jx` browser stack mapping and debugger
   breakpoints still need composed maps and dedicated acceptance tests.
3. The [JX LSP](language-server.md) follows Maven conventions and explicit
   classpaths; native Java services use the editors' Java engines. Whole-file
   formatting, arbitrary refactorings and additional IDE versions need their own
   verification. See the [editor matrix](tooling.md) for supported operations.
4. The [feature register](evidence/conformance.tsv) and [runtime guide](runtime.md)
   track implemented client APIs and their limits. Root options, actual lazy code
   splitting, JX server/static rendering, hydration, resume and the RSC protocol
   remain open. API names marked supported do not imply all options or modes:
   external-store server snapshots, action permalinks, `FormStatus.action`,
   ViewTransition callbacks and image preload source-set options are not exposed.
5. The demo is one local workspace, bound to loopback with no login or sessions.
   Its transactional capacity limit is 500 tasks, so successful creates remain
   visible after reload. A hosted multi-user product needs its own
   authentication, resource ownership, pagination, and deployment configuration.
   Client validation and route visibility do not authorize server operations.
6. Validate additional browsers and IDE versions before extending compatibility
   claims. Review production packaging, bundle optimization, API stability, and
   third-party binding coverage before a stable release.

All artifacts currently use local snapshot versions. GitHub source publication is
authorized and performed; Maven Central, npm, and editor marketplace publication
are separate release actions and have not been performed.
