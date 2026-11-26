# Shared Todo record and generated browser codec

Source: `f6220a2`, 26 November 2026, based on main `30742a6` plus the shared
cancellation prerequisite. M1-17/M1-19 are accepted in PR77 (`8ea78ba`)
after the full local gate and both Verify checks. M1-18 exact values remain a separate slice.

The dependency-free [Todo contract](../../examples/todo-contract/src/main/java/jx/example/contract/Todo.java)
is compiled once and consumed by the Spring repository/controller and Java/.jx
browser application. `TodoApi` owns collection/item/version paths; UUID generation
and validation, optimistic conflicts and Bean Validation stay on the server.

`record-codecs` reads compiled record metadata through javac without executing
application classes. It generates TodoJson read/write code into the browser
module. Both HTTP decoding and the development model checkpoint use that codec.
The shared JAR contains no browser, React, Spring, Jackson or JDBC dependency.

Executed checks:

- `./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl jx-maven-plugin,examples/todo-api,examples/todo-app -am package`
  passed in 16.985 seconds: 159 JVM checks (63 core, 10 React, 8 Elm, 56 compiler,
  18 Maven, 1 contract, 2 real Spring HTTP, 1 Todo model), plus actual TeaVM/browser
  asset generation. No shared repository install was used.
- `npm run test:todo` passed all 3 unchanged Chrome scenarios in 3.9 seconds:
  CRUD, controlled forms, routes/history, persistence, validation, conflicts,
  loading/error/retry and error-body recovery through the real Spring process.
- Generator tests compile a codec from a source-less dependency JAR whose record
  initializer would throw if loaded. Negative cases reject unsupported JDBC field
  types, generic shapes and custom constructor-parameter annotations. Ownership
  tests cover unowned/edited files, unchanged timestamps, stale classes, unrelated
  dollar-named classes, traversal and symlinks.

Publication integration found and reproduced a discovery defect before changing
the generator: two subprocess probes failed because javac executed an auto-start
plugin from either the host classpath or the selected contract classpath, despite
`-proc:none`. The generator now reads binary symbols through its declared
classpath while its public file-manager class/service loader is restricted to
the platform loader. Empty source and processor paths also prevent implicit
source discovery. This is dependency-discovery isolation, not a JVM sandbox.
The two probes now pass, together with all 64 compiler tests in 9.004 seconds;
the source-less JAR/throwing-initializer and real generated-code javac tests still
pass. The historical 159-check package above predates this fix. The final
publication gate below qualifies the corrected integrated implementation.

Limit: only required String/bool/int fields, fixed record names and canonical
constructor/accessor behavior. Exact numbers, nullable/missing fields, arbitrary
Jackson configuration, generated async operations/request DTOs, auth/session
integration and wire-evolution gates are not inferred. Chrome only; no new React
feature-pair credit. See [ADR0009](../decisions/0009-shared-record-wire-contract.md).

## Publication acceptance

[PR77](https://github.com/lmrrcc/jx/pull/77) merged as `8ea78bab71fc88fe56f79f4f7a98aa5995dfb94d`. Exact head `0c0784a` passed all 15 local gate steps in 374 seconds and both [PR Verify](https://github.com/lmrrcc/jx/actions/runs/45805474648) and [push Verify](https://github.com/lmrrcc/jx/actions/runs/45805395768). Maven/TeaVM passes 195 JVM checks in 44.665 seconds; 47 runtime browser checks, 975 conformance checks with 217 explicit mode exclusions, three Spring Todo scenarios and four real source-save hot workflows pass. The 149 cases and 62/6328 pair count are unchanged. Numeric adapters remain the separately qualified M1-18 slice; none are inferred as generated codec support.
