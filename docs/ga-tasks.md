# GA implementation sequence

Revision 4, 21 November 2026. Parent: [GA roadmap](ga-roadmap.md). This file turns
the [work package register](ga-execution.md) into an ordered list of tasks, grouped
by the roadmap's milestones. Work proceeds down this list, one task at a time.

Progress, 21 November 2026: M1-01 to M1-05 and R-01 to R-05 are done, committed as
`75d79c2` with Verify green. `main` is protected: a change reaches it only through
a pull request whose Verify check has passed. R-06 to R-09 are done. The next task is R-10.

## Rules

1. **One task is one isolated change**: one commit (or pull request) that builds and
   passes the configured checks on its own. A task never depends on a later task.
2. **Every task implements something.** It adds or changes code and adds an
   automated check that exercises the new behavior. Design decisions are written as
   an ADR inside the task that first needs them, not as a task of their own. The
   only exceptions are the gate reviews at the end of a milestone.
3. **Every task has a "try it" command** that Luca runs before the next task starts.
   A task is `done` only after that check and a green Verify workflow.
4. A new public capability comes with a compiling consumer example and a row in the
   [capability ledger](evidence/capabilities.tsv).
5. A task that turns out too large is split here before work starts; a spike that
   fails keeps its test as evidence and records a go/change/stop decision.
6. Tasks in M1 and M2 are specified. Tasks in M3 and later are the intended slices;
   they are re-cut into changes of this size when their milestone starts, and may
   change after the gate reviews.

Status values: `done` (approved, committed and green in Verify), `approved`
(Luca approved; not yet committed), `review` (implemented, waiting for Luca's
check), `next`, `planned`. The "GA" column names the work package the task belongs to.

## Test bench

Decided by Luca on 21 November 2026: JX is proven first against a purpose-built
test bench, not against the existing products. The products come later, as pilots.

- **A case** is one React behavior in a small scenario. It is written twice: a
  reference in plain React (framework-owned JavaScript, allowed as an interop
  fixture) and the JX version in Java and `.jx`. One Chrome spec states the
  observable outcome and runs unchanged against both.
- **The reference must pass.** That proves the spec describes real React behavior.
  The JX result is `pass`, `fail` or `unsupported`.
- **Isolation:** each case mounts alone on a fresh page load with its own root, no
  network and no shared state. One build per side keeps the run fast.
- **Coverage is enumerated, not guessed.** The list of stable exports of the pinned
  `react` and `react-dom` packages is generated; each export must map to a case or
  to an exclusion with a reason. A React upgrade that adds an export fails the check
  until it is mapped.
- **Two axes:** feature cases, and rendering modes (client-rendered application,
  static HTML, server render with hydration, lazy route). A case declares the modes
  that apply to it.
- **Combinations:** literal "all permutations" is unbounded. The bench has every
  feature alone, every feature in every applicable mode, and a set of combined
  scenarios chosen so that each pair of features meets at least once. More
  combinations are added when a defect shows one was missing.
- **Every feature task turns bench cells from `unsupported` or `fail` to `pass`**,
  in addition to its own tests. The `unsupported` cells are the remaining work, in
  one table.

## React parity track

Decided by Luca on 21 November 2026: JX is to support **all** React features, not a
chosen subset. The measure is the [feature list](evidence/conformance.tsv): 118
rows today, covering every export of the pinned `react` and `react-dom` packages
plus markup and DOM features. Each row that is not yet `supported` or `equivalent`
names the task below that closes it; `CheckBaseline` fails if one does not.

Target at the end of the track: no row `unsupported`, no row `untested`. Only two
rows stay excluded, because they are not React features an application can use: the
`react-dom` build marker and the React Compiler (a build tool for JavaScript
sources). Any other exclusion needs Luca's approval, recorded in the row.

Every R task follows the same pattern, as one change: write the reference case
(it must pass), implement the feature in JX with a documented Java API and a
consumer example, turn the JX column to `pass`, and update the row. A feature whose
React shape cannot exist in Java (untyped spread, element introspection) gets a
typed equivalent with the same observable outcome, marked `equivalent`.

| Task | Milestone | Change | Rows closed | Status |
| --- | --- | --- | --- | --- |
| R-01 | M1 | Cases for untested markup features: custom hooks, function and render props, run-time component choice, dotted component names. A case that fails becomes a defect fix in the same task | 4 untested | done |
| R-02 | M1 | Cases for untested DOM features: radio, select, textarea, uncontrolled inputs, capture-phase handlers, custom elements, document metadata, resource elements, root error callbacks | 7 untested | done |
| R-03 | M1 | Reference-only cases for every unsupported row; the JX column reports `unsupported`; results written to `evidence/conformance-results.tsv`; Verify fails on any regression | none; gives every later task a waiting test | done |
| R-04 | M1 | Typed event model: mouse, keyboard with modifiers, focus, pointer, touch, wheel, composition, clipboard, drag, media and animation events, with `currentTarget` | 1 unsupported, 3 untested | done |
| R-05 | M1 | Mutation runner over the runtime bridge, core hook API and markup lowering; survivors become new cases until none remains | none; proves the bench detects defects | done |
| R-06 | M2 | `memo`, with custom comparison | 1 | done |
| R-07 | M2 | Refs: ref as a component prop, callback refs with cleanup, `useImperativeHandle`, the `forwardRef` and `createRef` equivalents | 5 | done |
| R-08 | M2 | `useInsertionEffect`, `useEffectEvent`, `useDebugValue`, `Profiler`; reducer lazy initializer; nested `StrictMode` | 4 | done |
| R-09 | M2 | Optional and default props, typed prop forwarding in place of spread, generic components | 3 | done |
| R-10 | M2 | `createPortal`, `flushSync` | 2 | next |
| R-11 | M2 | Typed equivalents of `Children`, `cloneElement` and `isValidElement` | 3 | planned |
| R-12 | M3 | `useTransition`, `startTransition`, `useDeferredValue` | 3 | planned |
| R-13 | M3 | `Suspense` and `use` for promises and context | 2 | planned |
| R-14 | M3 | `lazy`, on the lazy-runtime boundary proven in M1 | 1 | planned |
| R-15 | M3 | `Activity`; `ViewTransition` and `addTransitionType` | 3 | planned |
| R-16 | M3 | Actions: form action functions, `useActionState`, `useFormStatus`, `useOptimistic`, `requestFormReset`, and the deprecated `useFormState` alias | 6 | planned |
| R-17 | M3 | Raw HTML through an explicit, named API equal to `dangerouslySetInnerHTML` | 1 | planned |
| R-18 | M3 | Resource hints: `preconnect`, `prefetchDNS`, `preinit`, `preinitModule`, `preload`, `preloadModule` | 6 | planned |
| R-19 | M3 | Root options: caught, uncaught and recoverable error callbacks, identifier prefix | part of `createRoot` | planned |
| R-20 | M4 | `renderToString`, `renderToStaticMarkup`, on the server-render proof from M1 | 2 | planned |
| R-21 | M4 | `hydrateRoot`; server snapshot for external stores; `useId` across server and client | 1 | planned |
| R-22 | M5 | Streaming: `renderToPipeableStream`, `renderToReadableStream`, with Suspense boundaries | 2 | planned |
| R-23 | M5 | Static rendering: `prerender`, `prerenderToNodeStream` | 2 | planned |
| R-24 | M6 | Resume: `resume`, `resumeToPipeableStream`, `resumeAndPrerender`, `resumeAndPrerenderToNodeStream` | 4 | planned |
| R-25 | M4–M6 | Generated coverage of every HTML and SVG element, attribute and event name React accepts | deepens the `dom:` rows | planned |
| R-26 | M6 | Every case run in every rendering mode that applies: client, static HTML, server render with hydration, lazy route | adds the mode axis | planned |
| R-27 | M6 | Combined scenarios so that every pair of features meets at least once | adds pair coverage | planned |
| R-28 | M7 | Class components authored in Java: `Component` lifecycle and `PureComponent` | 2 | planned |
| R-29 | M7 | Remaining small surface: `act` for JX tests, `captureOwnerStack`, version constants, `unstable_batchedUpdates`, `unstable_useCacheRefresh` | 9 | planned |
| R-30 | M8–M10 | React Server Components: `cache`, `cacheSignal`, the client and server directives and the server-component protocol, behind a decision record after a feasibility proof | 4 | planned |
| R-31 | M10 | Parity gate: zero `unsupported`, zero `untested`, mutation survivors zero, every case green in every applicable mode | closes the track | planned |

R-01 result, 21 November 2026: all four cases passed on JX without a defect; the
rows are now `supported` (three) and `equivalent` (dotted names).

R-02 result, 21 November 2026: five of the seven cases passed at once. Document
metadata and hoisted styles failed because JX wrapped every text child in a
fragment, which `title` and `style` reject; text children are now plain strings,
and all seven rows are `supported`.

R-03 result, 21 November 2026: 41 reference-only cases cover all unsupported rows,
including the ten server and static rendering functions, which run in Node. The
reference passes every one. `docs/evidence/conformance-results.tsv` records the
table, and the bench fails when a run differs from it.

R-04 result, 21 November 2026: `jx.Event` gained typed accessors for every event
family and `jx.EventTarget` was added. Four event cases pass on both sides; no row
is `untested` any more, and 48 rows are `unsupported`.

R-05 result, 21 November 2026: 34 curated mutants; the first run killed 33. The
survivor (layout effects ignoring their dependencies) showed a missing case, which
was added as `layout-effect-dependencies`; rerun alone, that mutant is now killed.
Results are in `docs/evidence/mutation-results.tsv`. The mutants are hand-written,
not generated, so this measures the listed defects only.

Expected count of `unsupported` rows after each milestone: M1 48, M2 32, M3 11,
M6 0. These are planning figures. R-30 is the largest single item and the one most
likely to move: it needs a server protocol that the current architecture does not
have, and the earlier roadmap kept it outside 1.0.

Platform work (contracts, Spring, editors, packaging) continues in the milestone
tables below. Within a milestone, R tasks and M tasks are done one at a time in the
order Luca picks at each review; the default is R tasks first.

## M1 — G0: feasibility and measured baseline (19 Nov–18 Dec 2026)

Exit gate: the five proofs (component JAR, exact shared contract, authenticated
packaged Spring prototype, lazy-runtime boundary, React SSR to hydration) each run
as a test, with a recorded decision.

| Task | GA | Change | Try it | Status |
| --- | --- | --- | --- | --- |
| M1-01 | 01 | Green baseline, version manifest, capability ledger, `CheckBaseline` | `java tools/CheckBaseline.java` | done |
| M1-02 | 02 | Portfolio and journey registers, old-UI baseline, pilot decisions | `java tools/CheckBaseline.java` | done |
| M1-03 | 18 | Test bench skeleton: a reference React app and a JX app that mount one case per page load, one shared Chrome spec run against both; first cases: state, effect cleanup, keyed list | `npm run test:bench` | done |
| M1-04 | 18 | React API inventory generated from the pinned `react` and `react-dom` packages; `conformance.tsv` maps every export to a case or a reasoned exclusion; `CheckBaseline` fails on an unmapped export | `java tools/CheckBaseline.java` | done |
| M1-05 | 18 | Bench cases for everything JX implements today: all current hooks, context, refs, error boundaries, events, controlled inputs, Strict Mode, attributes, text and child values. 32 cases; fixed a key warning for context providers with several children | `npm run test:bench` | done |
| M1-10 | 05 | Compiler writes a versioned component metadata descriptor into each module's output | `./mvnw -pl jx-compiler test` | planned |
| M1-11 | 05 | Compiler resolves components from descriptors in classpath JARs, without their sources | `./mvnw -pl jx-compiler test` | planned |
| M1-12 | 05 | `examples/component-library` (producer JAR) and `examples/library-consumer` (separate app) with a Chrome test | `npm run test:library` | planned |
| M1-13 | 05 | Incompatible or missing descriptor version fails the build with a diagnostic at the `.jx` usage | `./mvnw -pl jx-maven-plugin test` | planned |
| M1-14 | 03 | One cancellation type in `jx-core`, used by both `jx-browser` HTTP and `jx-elm` commands | `npm run test:runtime` | planned |
| M1-15 | 03 | `useResource` hook: loading, value and error states, abort on unmount or key change, stale replies dropped | `npm run test:runtime` | planned |
| M1-16 | 03 | Router loader built on the same model; ADR 0003 records the async ownership and Suspense decision | `npm run test:runtime` | planned |
| M1-17 | 04 | `examples/todo-contract`: one Todo value and operation paths compiled into both the Spring API and the browser app | `npm run test:todo` | planned |
| M1-18 | 04 | Exact-value vectors (64-bit ids, decimals, null and missing) run on the JVM and in Chrome against the real Spring endpoint | `npm run test:contract` | planned |
| M1-19 | 04 | Generated browser codec for the one contract value replaces the hand-written one | `npm run test:todo` | planned |
| M1-20 | 13 | The todo API packaged as one executable JAR that serves the built UI; started from an empty directory in a test | `npm run test:packaged` | planned |
| M1-21 | 13 | Session login and CSRF on the todo API; development proxy forwards cookies to its one allowed backend | `npm run test:auth` | planned |
| M1-22 | 06 | Fixture with a second TeaVM module loaded on demand; test records what crosses: record, context, callback | `npm run test:lazy` | planned |
| M1-23 | 06 | Chosen boundary (shared runtime or serialized ABI) with chunk-failure and retry tests; ADR 0004 | `npm run test:lazy` | planned |
| M1-24 | 07 | Node host renders a JX component to HTML with React's server renderer | `npm run test:ssr` | planned |
| M1-25 | 07 | Browser hydrates that HTML without replacing the tree | `npm run test:ssr` | planned |
| M1-26 | 07 | Protected data route: two simultaneous identities, immutable snapshot, abort, no second fetch | `npm run test:ssr` | planned |
| M1-27 | 37 | Packaged MV3 extension renders a JX popup under its CSP and calls a WASM function; popup close and reopen | `npm run test:extension` | planned |
| M1-28 | 08 | 100-component scale fixture and an editor latency benchmark in the IntelliJ tests | `./gradlew test` in `tooling/intellij` | planned |
| M1-29 | 08 | Source-preserving structural edit proof (component rename across closed files), or the recorded limit | `./gradlew test` in `tooling/intellij` | planned |
| M1-30 | 09 | Artifacts installed to a file repository; an outside consumer builds with only that repository | `npm run test:consumer` | planned |
| M1-31 | — | G0 review: ledger updated, go/change/stop recorded per proof, M2–M3 re-cut | read the review record | planned |

## M2 — G1: shared application skeleton (19 Dec–18 Jan)

Exit gate: published-local contract and component modules, generated async client,
Boot JAR serving UI and API, session and CSRF journey in development and production.

| Task | GA | Change | Try it | Status |
| --- | --- | --- | --- | --- |
| M2-01 | 10 | Contract annotations and the intermediate model, with validation errors mapped to source | `./mvnw -pl jx-contract test` | planned |
| M2-02 | 10 | Browser codec generator for records, enums, lists, optional and exact numbers | `npm run test:contract` | planned |
| M2-03 | 10 | Asynchronous browser client generator with typed errors and cancellation | `npm run test:contract` | planned |
| M2-04 | 10 | Spring server boundary from the same contract; conformance test against real HTTP | `./mvnw -pl examples/todo-api test` | planned |
| M2-05 | 10 | Wire schema file; an incompatible contract change fails the build | `./mvnw -pl jx-contract test` | planned |
| M2-06 | 12 | `jx-spring-boot-starter`: serves packaged assets, SPA fallback that never answers `/api` or missing assets | `./mvnw -pl jx-spring-boot-starter test` | planned |
| M2-07 | 12 | Starter backs off to user configuration; BOM module | same | planned |
| M2-08 | 12 | Application template; a generated project builds and runs from an empty directory | `npm run test:template` | planned |
| M2-09 | 13 | Same-origin development: the backend fronts the dev server; owned process lifecycle | `npm run test:auth` | planned |
| M2-10 | 13 | Session expiry, logout and CSRF failure journeys, in development and from the packaged JAR | `npm run test:auth` | planned |
| M2-11 | 11 | Differential harness: the same vectors run on the JVM and in TeaVM | `npm run test:profile` | planned |
| M2-12 | 11 | First certified set: `BigDecimal`, `long`, selected `java.time` | `npm run test:profile` | planned |
| M2-13 | 11 | Dependency scanner names the first unsupported reachable method and its call path | `./mvnw -pl jx-maven-plugin test` | planned |
| M2-15 | — | G1 review | read the review record | planned |

## M3 — Alpha 1: language and reusable components (19 Jan–18 Feb)

| Task | GA | Change |
| --- | --- | --- |
| M3-04 | 14 | Markup recovery for incomplete edits; fuzz test |
| M3-05 | 15 | HTML and SVG attribute schema; wrong types rejected at source |
| M3-06 | 16 | Nested routes and layouts |
| M3-07 | 23 | Composed source maps: browser stack traces point to `.jx` |
| M3-08 | 09 | Signed test packages through the candidate distribution path |
| M3-09 | 29 | Java 21 and Kotlin Gradle Boot service consumes a JX contract and assets |

## M4 — Alpha 2: complete application workflow (19 Feb–18 Mar)

| Task | GA | Change |
| --- | --- | --- |
| M4-01 | 16 | Route loaders and actions on the resource cache; invalidation |
| M4-02 | 16 | Optimistic update with rollback; stale and out-of-order replies |
| M4-03 | 17 | Form model with field arrays, validation and server errors |
| M4-04 | 19 | Request headers and binary bodies |
| M4-05 | 19 | Upload with progress; download |
| M4-06 | 19 | SSE and WebSocket lifecycle with bounds and reconnect |
| M4-07 | 21 | Reference product: workspace, roles, list and detail |
| M4-08 | 21 | Reference product: two-tenant isolation and session-expiry tests |
| M4-09 | 27 | Generated checkpoint codecs for hot updates |

## M5 — Alpha 3: React ecosystem and rich input (19 Mar–18 Apr)

| Task | GA | Change |
| --- | --- | --- |
| M5-05 | 15 | Safe rich-content rendering with one CSR and SSR policy |
| M5-06 | 20 | Binding SDK and a first headless-control binding |
| M5-07 | 20 | Grid or virtualized table binding |
| M5-08 | 20 | Chart and Markdown/code bindings |
| M5-09 | 17 | Localization, RTL and accessible form primitives |

## M6 — G2: website rendering alpha (19 Apr–18 May)

| Task | GA | Change |
| --- | --- | --- |
| M6-01 | 22 | Static generation: HTML and CSS with no client runtime for content pages |
| M6-02 | 22 | Opt-in hydration per page; metadata, canonical and sitemap |
| M6-03 | 22 | Request-time SSR host with isolation, limits and timeouts |
| M6-04 | 22 | Hashed assets, route chunks and cache headers |
| M6-05 | 18 | Lazy routes on the M1 boundary |
| M6-06 | 30 | Website pilot, first pages: `lmrr.cc` listings in JX beside the existing site |
| M6-07 | 28 | Performance budgets evaluated in CI against the portfolio baseline |

## M7 — Beta 1: daily developer platform (19 May–18 Jun)

| Task | GA | Change |
| --- | --- | --- |
| M7-01 | 23 | `.jx` breakpoints and stepping in the browser |
| M7-02 | 24 | IntelliJ run and debug parity; safe closed-file edits |
| M7-03 | 25 | VS Code class and file rename, formatting, closed-file references |
| M7-04 | 27 | 100-update hot-reload soak without leaks or replay |
| M7-05 | 20 | Media and payment-element bindings |
| M7-06 | 30 | Website pilots: Stacktrace and luca4j pages, with rollback |
| M7-07 | 31 | Stack Sensei pilot: first read-only route as a separate implementation beside the existing frontend |

## M8 — Beta 2: portfolio readiness (19 Jun–18 Jul)

| Task | GA | Change |
| --- | --- | --- |
| M8-01 | 26 | TurboJ native `.jx` integration |
| M8-02 | 38 | Browser storage with schema migration and quota recovery |
| M8-03 | 38 | Opt-in service-worker profile without unsafe offline replay |
| M8-04 | 33 | Extension profile: storage, messaging and worker lifecycle |
| M8-05 | 31 | Stack Sensei pilot: authenticated route with one-to-one journey tests |
| M8-06 | 32 | Media permission, cleanup and streaming qualification slice |

## M9 — Beta 3: extension and migration hardening (19 Jul–18 Aug)

| Task | GA | Change |
| --- | --- | --- |
| M9-01 | 33 | Gandalf read-only popup screen inside the existing package |
| M9-02 | 32 | Exact-value and payment-recovery qualification in a provider sandbox |
| M9-03 | 34 | Version-skew tests: old tab against new backend |
| M9-04 | 34 | Retained-asset rollback rehearsal |
| M9-05 | 28 | Firefox and WebKit in CI; Windows build |

## M10–M12 — RC entry, stabilization, GA decision (19 Aug–18 Nov 2027)

| Task | GA | Change |
| --- | --- | --- |
| M10-02 | 34 | Public API, wire and metadata compatibility checks enforced in CI |
| M10-03 | 28 | Security, performance and accessibility review fixes |
| M11-01 | 35 | Clean-machine install and upgrade from published candidates |
| M11-02 | 35 | 30-day pilot observation; recovery and version-skew drills |
| M12-01 | 36 | GA evidence manifest and go/no-go for application and website profiles |
| M12-02 | 39 | Portfolio qualification sign-off for media, finance and extension |

## Changes from the register order

- The test bench comes first (M1-03 to M1-05), followed by the React parity
  track. Together they replace the React slices that the register spread over
  GA-14, GA-15, GA-18 and GA-22, and extend them to every React feature.
- GA-05 (component JAR) follows the bench, ahead of GA-03 and GA-04. It depends
  only on the baseline.
- The thin authenticated packaged Spring proof, a G0 requirement, is taken from
  GA-13 into M1 as two tasks; the rest of GA-13 stays in M2.
- Design-only steps (async ownership, lazy boundary) are folded into the tasks that
  implement them.
- The existing products are not the starting point. The pilots Luca chose on
  21 November 2026 stay at M6-06, M7-06, M7-07 and M8-05, after the bench is green
  for the features they need. The Stack Sensei tasks are separate implementations that do not modify the
  existing frontend.

## Language and developer experience

Requested by Luca on 21 November 2026. These follow the parity track; each is one
change with compiler or editor tests, a bench case written in the new form, and
the old form kept working. Order and milestone are set at a later review.

| Task | Change | Notes | Status |
| --- | --- | --- | --- |
| DX-01 | **Components without the class envelope.** A `.jx` file may declare `public static View Name(...)` at top level; the compiler generates the `@Component` class, its `JX` descriptor and, from the parameters, the props record | Done in the `.jx` compiler, so it does not depend on Java's compact source files. To settle in the task: one component per file or several, how props are declared (parameters or a record), and how Java code refers to the generated class | planned |
| DX-02 | **Object-literal syntax in markup.** `style={{color: "rgb(255, 0, 0)", opacity: 0.5}}` lowers to the typed `Style` API; the same literal form is considered for other typed values | String values need quotes: an unquoted `rgb(255, 0, 0)` cannot be told apart from a Java call. Keys are checked against the target type; an unknown key is a compile error at the `.jx` position | planned |
| DX-03 | **`var` and static imports as the documented style.** `var right = useState(0);` with `import static jx.Hooks.*` | Already compiles today (checked on a bench case on 21 November 2026). The task is to cover it with compiler and bench tests, confirm the hook-placement checks and both editors handle unqualified hook calls, and switch the examples and guide to this form | planned |
| DX-04 | **`console.log`.** A `jx.browser.Console` API whose static import gives `console.log(...)`, `warn`, `error` and `debug` in ordinary Java | `import static jx.browser.Console.console;` makes `console.log("x")` valid Java with no language change. Values are formatted on the Java side; the bench keeps its own log for assertions | planned |
| DX-05 | **Documentation on hover in IntelliJ, then VS Code.** Hovering a component tag shows the component's Javadoc and its props with types; hovering a prop shows the record component's documentation; hovering an HTML attribute or event such as `onClick` shows its meaning and the handler type; hovering inside Java expressions keeps the native Java documentation | Component and prop documentation comes from the same compiler metadata that completion uses. Attribute and event documentation needs the DOM schema planned for the parity track (R-25), so that part lands with or after it | planned |

## Test tooling

Requested by Luca on 21 November 2026.

| Task | Change | Notes | Status |
| --- | --- | --- | --- |
| TT-01 | **Cherry Test configuration.** A `cherry-test.yaml` at the repository root so one run covers unit tests (Maven modules), integration tests (Spring example, Maven plugin, language server), functional browser tests (bench, runtime, counter, todo, hot reload) and produces one browsable report | Uses Cherry Test's `maven` and `playwright` adapters. The Playwright configurations must write their JSON results and screenshots where the adapter expects them; that wiring is settled by trial runs in the task | next after R-05 |
| TT-02 | **Visual tests.** Screenshot comparisons for the examples and for bench cases whose result is visual (style, SVG, hidden activity, hoisted styles), run as Cherry Test's `visual` kind, with expected, actual and difference images in the report | Baseline images are recorded per platform; the first set is macOS Chrome, and CI needs its own Linux set | planned |
| TT-03 | **A bench gallery page.** A page that lists every bench case with links to open it on the JX side and on the React reference side, served by the existing bench server | For looking at cases by hand; it adds no assertions | planned |
