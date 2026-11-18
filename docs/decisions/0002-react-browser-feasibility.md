# 0002: TeaVM and React browser feasibility

Date: 2026-11-18. Status: initial Java-to-React proof verified; further acceptance
gates remain.

## Decision and scope

Continue with TeaVM's JavaScript target and the actual React/React DOM runtime,
as required by the [baseline](0001-java-framework-baseline.md) and
[v2 specification](../JX_Astra_Max_Prompt_v2.txt). A small browser proof demonstrates
that React can invoke Java-authored components and their callbacks and that those
components can call React hooks during rendering. This supports investment in the
compiler and public bridge. It does not complete the specification's feasibility
milestone or prove every required Java/JavaScript conversion.

The proof uses handwritten Java. The `.jx` parsing and generated-Java part of the
intended pipeline is a separate acceptance gate.

## Evidence

The proof was built with Corretto Java 25, Maven 3.9.11 through Maven Wrapper,
TeaVM 0.16.0, React and React DOM 19.3.1, and esbuild 0.28.2. Playwright 1.65.1
ran one integration test against the locally installed Google Chrome. This is
evidence for that configuration, not a browser compatibility matrix.

The [Java entry point](../../proof/react-bridge/src/main/java/jx/proof/BrowserProof.java)
holds stable component callbacks and passes them to `React.createElement`.
TeaVM's `@JSFunctor` callbacks and `@JSBody` module imports connect Java to React.
A Java record supplies opaque component props; integer and boolean state and
event/effect callbacks cross the bridge. React creates the DOM root and mounts
the app under `StrictMode`.

The [browser test](../../proof/react-bridge/tests/browser.spec.js) verifies:

- Two instances of the same counter component start at 0 and 10 independently.
- Three queued functional updates in one click change 0 to 3 while the other
  counter remains at 10.
- The handler still observes its captured value of 0 after scheduling updates.
- After networking is disabled, another click changes 3 to 6.
- Removing that counter runs its effect cleanup with the committed value 6.
- The second counter reaches 13, retains that state across the parent's toggle,
  and the remounted first counter starts again at 0.
- No uncaught page errors occur during this sequence.

These commands completed successfully for the initial proof:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository package
npm run build:proof
npm run test:proof
```

The test runs under Strict Mode, but does not assert every development lifecycle
sequence or the complete dependency-change behavior of effects.

## Interoperability findings

Use native `JSObject` handles with explicit accessors for React's hook result
arrays. The attempted direct Java-array representation produced incorrect scalar
unwrapping during development. Keep scalar conversion explicit where values
become JavaScript children or dependencies, and preserve opaque Java object
identity where a typed binding does not request structural conversion.

The working proof uses separate interop bodies for the React and React DOM
imports and conservative JavaScript syntax inside `@JSBody`. Earlier attempts
with multiple imports in one body or newer body syntax encountered generation
or parser problems. These are observations about this proof, not a claim that
every such pattern is unsupported by TeaVM. Minimized reproductions are needed
before treating an issue as a backend limitation.

## Remaining acceptance gates

- Generate the proof from `.jx` through Maven, with typed props, deterministic
  output, original-source diagnostics, and source mappings.
- Exercise the public Java hook API, effect dependency changes, cleanup ordering,
  lazy initialization, stored callable values, context, refs, and custom hooks.
- Verify conversion and identity rules for nulls, strings, booleans, numbers
  including special floating-point values, opaque Java values, typed bindings,
  callbacks, and dependency arrays. Test equality against React's semantics.
- Verify the required Java subset, including sealed types and the collection and
  asynchronous APIs actually used by applications. Define browser cancellation
  and scheduling without assuming JVM threads or blocking I/O.
- Test React error boundaries and distinct event, async, and command failures.
  Demonstrate both directions of React interoperability with one React runtime.
- Prove Elm store ordering, immutable snapshot stability, command execution
  outside rendering, cancellation, stale-result handling, and subscription cleanup.
- Package and verify the Maven plugin, shared language server, VS Code extension,
  and IntelliJ plugin, then complete the routed application demonstration.

Consult the [TeaVM interop documentation](https://www.teavm.org/docs/runtime/jso.html),
[Java 25 release notes](https://teavm.org/docs/release-notes/0.13.0.html), and
[React state contract](https://react.dev/reference/react/useState) when expanding
the bridge. Documentation establishes intended support; runnable consumer and
browser tests establish JX's implemented support.
