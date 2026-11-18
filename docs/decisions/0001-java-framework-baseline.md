# 0001: Java framework baseline

Date: 2026-11-18. Status: accepted direction, revised for the
[v2 implementation specification](../JX_Astra_Max_Prompt_v2.txt).

## Context

JX provides complete React browser applications authored in Java and `.jx` markup,
with optional Spring Boot backend services. The v2 specification replaces the
earlier server-first, class-only scope. Browser execution, functional components,
hooks, an optional Elm application architecture, and development tooling are
requirements. This decision records that direction; it does not claim those
deliverables are complete.

## Compilation and runtime

Use Java 25 without required preview features and Maven Wrapper. The intended
pipeline is `.jx` → generated Java and source mappings → javac bytecode → TeaVM
JavaScript → a typed React bridge → React and React DOM in the browser.

TeaVM's JavaScript target is the initial backend. The
[browser feasibility record](0002-react-browser-feasibility.md) documents the
working Java-to-React proof and its limits. A reproduced blocking limitation is
required before evaluating an alternative backend. React owns component
invocation, reconciliation, scheduling, hooks, effects, and DOM rendering.

Keep browser and JVM server dependencies separate. Java 25 source compatibility
does not imply availability of the full JVM library in a browser. Publish a tested
browser profile covering the APIs, value conversions, asynchronous behavior, and
cancellation that applications can use. Local interaction must continue after
networking is disabled once application assets have loaded.

Ordinary application logic is Java-only. Framework-maintained JavaScript or
TypeScript adapters, npm dependencies and tooling, VS Code extension code, and
explicit React interoperability fixtures are allowed. IntelliJ's supported Gradle
tooling is allowed. Other custom repository automation uses Java; standard
wrappers, CI configuration, and existing external tools remain appropriate.

## Component and application state

Functional components and hooks form the browser API. Register stable component
types and let React invoke them. State belongs to mounted component instances,
never a static field or component class. A state handle exposes its render's
snapshot; setters schedule replacements and functional updaters queue against
preceding updates. Java lambda capture and assignment retain their usual meaning.

Specify and test dependency comparison, Java/JavaScript identity conversions,
stored function values, cleanup, refs, context, and external-store subscriptions.
Keep rendering and reducer evaluation pure. Publish hook support as tested
capabilities rather than claiming compatibility from familiar method names.

Provide optional Elm Model–View–Update with immutable models, typed messages,
pure updates, command descriptions, and managed subscriptions. Serialize updates,
commit a cached immutable snapshot before interpreting commands, and expose that
store through React's `useSyncExternalStore`. Execute commands outside rendering
and reducers. Define cancellation, stale-result protection, subscription keys,
disposal, and replay; each state value has one owner.

## Required development workflow

| Deliverable | Acceptance boundary |
| --- | --- |
| `.jx` compiler | Java-aware parsing, typed component contracts, deterministic generation, and diagnostics mapped to original source |
| Maven plugin | Normal source-generation lifecycle, browser assets, useful original-source failures, incremental cleanup, and a development workflow |
| Shared language server | Compiler-backed semantics, incomplete-source handling, diagnostics, completion, hover, and navigation |
| VS Code extension | Installable and tested `.vsix`, language-server integration, and workspace-trust handling |
| IntelliJ plugin | Installable ZIP with verified product/version compatibility and the specified practical editing baseline |

The complete application demonstration must cover routing and browser history,
forms, async HTTP, loading/error states, cancellation or stale-response protection,
accessibility, and an optional Spring API. Interoperability must work both for JX
inside an ordinary React host and a typed JavaScript React binding inside JX.
SSR, hydration, Suspense, and other advanced features need their own verified
milestones; they do not follow automatically from the bridge.

## Framework boundaries and remaining decisions

Keep core contracts independent of Spring, persistence, and any server. Add an
optional Spring integration with a real consumer. Use records for values,
ordinary classes for identity and lifecycle, constructor injection, and explicit
errors; do not introduce Lombok. Protect server-owned operations on the server
and exclude credentials and server-only dependencies from browser bundles.

Choose modules for exercised boundaries. Public API details, the supported
browser/Java profile, exact editor compatibility, package publication ownership,
and advanced rendering capabilities still require implementation evidence.
Pin dependencies and wrappers; use JUnit Jupiter and compiler, browser, protocol,
and integration checks at the boundaries they verify.

The portable skills under `.agents/skills/` intentionally do not encode JX's JDK,
build, runtime, or language policy. Those choices belong in this repository's
guide and decisions.
