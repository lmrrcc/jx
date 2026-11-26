# G0 IntelliJ scale and structural-edit proof

Scope: M1-28 and M1-29. The fixture starts from `main` at `d9151c0` and uses the
actual bundled Java engine in IntelliJ IDEA `IU-261.25134.95` (2026.1.3), with the
repository's compiler and language-server artifacts. The SDK tests do not launch
Maven, a language-server substitute, or an application backend while editing.
Source commits: `d362aa9` (syntax isolation) and `a271794` (editor proof/rename).

## Acceptance

`JxScaleFixture` creates exactly 100 Java-authored `.jx` components in one Maven
module: a shared component and 99 consumers, plus ordinary Java helper/caller
files. The consumers use imports, Java constructors, simple and qualified paired
component tags, props and children. Component names also occur in comments,
strings, markup text and quoted attributes, where a symbol rename must leave
them unchanged. Each test owns and disposes its project fixture.

`JxEditorScaleTest` records the first complete project projection separately,
then three warmups and 30 samples for each of:

- Unchanged project projection retrieval, with object-identity reuse assertions.
- Authored document replacement and commit through the current Java projection,
  checking that each distinct edit is present and all 100 components remain indexed.
- Authored document replacement and commit through completed native Java
  completion, checking both expected typed method candidates.
- Authored document replacement and commit through completed native Java
  diagnostics, alternating invalid/valid arguments and checking original-source
  ranges and removal of stale errors.

Timing uses a monotonic clock around the whole named synchronous operation.
Correctness assertions are mandatory; wall-clock thresholds are not imposed on a
shared workstation. Raw samples and environment details are written to
`tooling/intellij/build/reports/editor-scale/`. The editor packaging workflow
retains these reports alongside its SDK test and Plugin Verifier artifacts.

`JxClosedFileRenameTest` invokes the native rename handler with every authored
file saved and closed. It requires exact expected source contents for the
declaration, constructor, 99 JX consumers and ordinary Java caller; the authored
`.jx` filename must also change. A separate 24-component fixture proves one-step
undo and redo across closed files; the 101-file SDK undo limit is recorded below.
A colliding Java class must reject the entire operation
without changing any file. Closed means no editor is open, not that PSI or the
project's indices have never loaded the file.

## Qualification

On 2026-11-26 the final Maven package passed **136 JVM tests** (66 core, 60
compiler, 10 language server). The focused SDK suite passed **4/4** in 35 seconds;
the complete SDK suite passed **96/96**, with no skips.
`test buildPlugin verifyPlugin` completed in 1m42s. The ZIP is compatible with
`IU-261.25134.95`, with zero internal API usages and the existing 15 deprecated
and 18 experimental API warnings. The embedded compiler and server match this
checkout's artifacts byte-for-byte. ZIP SHA-256:
`e4634a7208f722e534a112a48659fcd727b9602745d14030484b86a4f9c99e26`.
This is the original local development rebuild of 0.3.2. The later PR79
publication gate and both Verify checks pass as recorded below; the ZIP hash and
SDK measurements remain evidence of this original source-specific execution.

The following measurements are from the complete SDK run on Luca's Apple M5
workstation, 10 reported processors and 16 GiB RAM, macOS 26.5.1/aarch64. The SDK
worker uses the pinned IDE's JBR `25.0.3+9-b329.124` with a 2 GiB maximum heap;
Maven uses Java `25.0.4.1`. Each repeated category has 3 warmups and 30 measured
samples. [All raw samples](intellij-editor-scale-latency.tsv) are retained.

| Completed operation | p50 | p95 | Maximum |
| --- | ---: | ---: | ---: |
| Unchanged project projection | 0.000208 ms | 0.000291 ms | 0.001375 ms |
| Edit → current projection | 24.595 ms | 38.571 ms | 42.445 ms |
| Edit → native Java completion | 626.289 ms | 787.271 ms | 794.482 ms |
| Edit → native Java diagnostics | 72.558 ms | 151.184 ms | 160.952 ms |

The first project projection took 48.372 ms (one observation). Renaming across
100 components plus an ordinary Java consumer took 1,577.204 ms (one observation).
These times include SDK orchestration for the named operation and are not a
general typing-latency guarantee or an enforced GA budget.

## Defects reproduced and fixed

The original rename processor changed all 101 documents before committing them.
The real SDK rejected this with `Too many uncommitted documents (101)`. Every
range is now validated before any mutation, then each document is committed
after its descending-offset edits, inside the same native refactoring command.
Rollback also commits each restored document. The 101-file source-preserving
rename and the 24-component undo/redo tests pass without disabling that guard.

The initial scale benchmark was stopped after more than six minutes, with nine
daemon workers repeatedly preparing javac for the same 100-source batch. Thread
samples showed `JavaModel.read` opening the JDK release archive and scanning the
host/IDE classpath. No complete baseline distribution was obtained, so no numeric
speedup factor is claimed.

An independent subprocess regression also proved that an ambient javac plugin
with `autoStart()` ran during `EditorProjection.project`, despite `-proc:none`.
That violated the projection's no-project-code contract. The syntax pass now uses
empty class/source/processor paths and a public forwarding file manager whose
classloader and service lookup are JDK-platform-only. Empty paths alone were
insufficient because javac's loader inherited the host classloader. The same
real-plugin regression now passes. This isolates ambient discovery; it is not a
sandbox for arbitrary code already installed in a platform loader.

Only `JavacTask.parse()` runs in this pass, so Java 25 grammar is selected with
`--source 25` without repeatedly opening the release API archive. Actual type
attribution remains in the application's Java compiler or native IDE engine;
the build's `--release 25` contract is unchanged. The JDK distinguishes
[parse from analyze](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html).

## Recorded large-undo qualification limit

After the rename fix, the native SDK undo operation still hit its unit-test-only
dirty-document guard with 101 strongly retained PSI files. A Java-only control
using the stock Java rename and undo handlers reproduced the same failure after
verifying all 100 caller edits; it uses no JX files or processor. Keeping those
PSI files strongly reachable matters: a weaker probe without the retained
consumer list passed and did not establish the same condition.

The [negative diagnostic control](../../tooling/intellij/diagnostics/NativeClosedUndoProbeTest.java)
is retained outside the normal test source set. To reproduce in a disposable
checkout, copy it into `tooling/intellij/src/test/java/jx/intellij/` and run
`test --tests jx.intellij.NativeClosedUndoProbeTest` with the same pinned IDE.
Expected result: the SDK logs the 101-document error at `UndoManager.undo`,
after a successful native Java rename. Do not disable the SDK assertion or count
this negative probe as a passing qualification test. The regular suite keeps
the 101-file rename and a separate 25-file (24 components plus Java) undo/redo
proof. Installed-IDE undo at 101 files remains unqualified by this slice.

## Reproduce

First build this checkout's compiler, core and shared server:

```sh
./mvnw -B -ntp -pl jx-language-server -am package
```

Then from `tooling/intellij`:

```sh
./gradlew --no-daemon --max-workers=1 test \
  --tests jx.intellij.JxEditorScaleTest \
  --tests jx.intellij.JxClosedFileRenameTest
```

The documented `-PideaLocalPath` option may select an already installed matching
IDE. The Gradle task also runs as part of the existing complete editor test suite.

## Limits and source basis

These are headless SDK model tests with the real Java implementation, as described
by JetBrains' [testing overview](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html).
The structural check follows the SDK's [rename-test contract](https://plugins.jetbrains.com/docs/intellij/rename-test.html)
and compares whitespace as well as tokens. Project setup occurs before timing;
the first projection is one observation, not a cold IDE startup distribution.
The benchmark replaces and commits document snapshots, rather than simulating
physical keystroke-to-paint latency. Completion timing ends when candidates are
available, not when a popup is painted. Rename timing is a single observation.

This proof does not establish an enforced GA performance budget, universal Java
refactoring support, large-project indexing speed, native class-component editor
qualification, VS Code closed-file rename, or an installed-IDE manual walkthrough.
The separate [native editing decision](../decisions/0002-native-java-editor-services.md)
and [tooling capability matrix](../tooling.md) retain those boundaries. The future
HR edit-to-browser-frame benchmark remains distinct.

## Publication acceptance

[PR79](https://github.com/lmrrcc/jx/pull/79) merged as `c533d9906162c1ee76c91bac88f2fae15b0a674a`. Exact head `c96e945` passed all 15 local gate steps in 368 seconds and both [PR Verify](https://github.com/lmrrcc/jx/actions/runs/45812869867) and [push Verify](https://github.com/lmrrcc/jx/actions/runs/45812731127). The integrated gate passes 198 JVM checks, the runtime suite, 975 conformance checks with 217 explicit mode exclusions, four Spring Todo scenarios and four real hot workflows. The 149-case inventory and 62/6328 pair count are unchanged. The JavaModel/parser regression, IntelliJ implementation and SDK proof sources match the qualified originals; no additional SDK run or rebuilt ZIP is claimed for this publication assembly. The 101-retained-file undo limit above remains explicit.
