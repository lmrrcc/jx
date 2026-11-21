# Implementation status

This is an initial, executable implementation of the
[v2 specification](JX_Astra_Max_Prompt_v2.txt), with a deliberately documented
compatibility profile. It is not a promise of every Java library, React feature,
browser, or editor distribution.

The [GA roadmap](ga-roadmap.md) proposes the next year of work, measurable release
gates and portfolio migrations. Its planned capabilities are not delivered status.

## Delivered boundaries

| Boundary | Implementation and evidence |
| --- | --- |
| Java/browser feasibility | Java 25, TeaVM 0.16.0, actual React/React DOM 19.3.1; real Chrome tests include offline clicks, independent instances, captured snapshots, and cleanup. |
| `.jx` compiler | Java lexical scanning plus javac component metadata; typed record props, markup lowering, fragments/children/keys, source maps, original-source syntax/type diagnostics, and practical hook-placement checks. |
| Maven | `generate`, `generate-test`, `check`, `check-test`, `browser`, and `dev`; output ownership, TeaVM and locked bundling, isolated development assets, atomic successful-build publication, state-preserving updates, and optional loopback API proxy. |
| Runtime | Typed components, events, hooks, context, refs, error boundaries, safe text rendering, and explicitly bound React interoperability in both directions. |
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
4. The [capability ledger](evidence/capabilities.tsv) and the [runtime guide](runtime.md)
   track unimplemented portals, lazy/code-split components, Suspense,
   transitions/deferred values, ref forwarding, SSR, and hydration. Loading flags
   do not constitute Suspense support.
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
