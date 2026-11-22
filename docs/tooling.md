# JX development tooling

Both editor packages combine native Java tooling with JX markup services.
IntelliJ uses its bundled Java plugin and Java PSI; VS Code uses the official
Red Hat Java extension and JDT LS. Both bundle the same executable JX language
server for component metadata and compiler diagnostics. The compiler supplies
in-memory Java projections with exact source mappings; the adapters do not
maintain separate Java parsers. A full Java 25 JDK is required. Build the server
and its Java dependencies before packaging either editor:

```sh
./mvnw -B -ntp -pl jx-language-server -am package
```

The packaged server comes from
`jx-language-server/target/jx-language-server-0.1.0-SNAPSHOT.jar`. Rebuild both
packages after changing the compiler or server. Editor package version **0.2.0**
replaces the earlier highlighting/LSP-only adapters. No editor marketplace publication
has been performed.

After building both packages, verify that they contain the current server
byte-for-byte, together with IntelliJ's compiler JAR and VS Code's JDT bridge,
and print their SHA-256 hashes from the repository root:

```sh
java tooling/vscode/VerifyPackages.java
```

When rebuilding only IntelliJ, use `java tooling/vscode/VerifyPackages.java --intellij-only`
to verify its bundled compiler and language server without checking an older VSIX.

## Java editing matrix

| Operation in `.jx` | IntelliJ IDEA 2026.1.3 | VS Code 1.138 / Red Hat Java 1.58 |
| --- | --- | --- |
| Java/HTML colors; component and prop assistance | Java/markup highlighting + JX compiler services | Java/markup grammar + JX compiler services |
| Java member completion, imports, typed event lambdas | Native Java PSI, including incomplete markup | Native JDT working copies, including incomplete expressions |
| Java errors and reference searches | Native Java engine, mapped to source | Native JDT engine, mapped to source |
| Java → JX navigation | Native navigation | **JX: Go to Definition**, also scoped to F12 in Java files of JX modules |
| Local and method rename | Native mapped edits across Java/JX | Mapped edits; ordinary Java targets must be open |
| Component class/file rename | Authored declaration, constructors, Java references, tags and `.jx` filename | Rejected atomically until complete mappings are available |
| Import quick fixes and Optimize Imports from markup | Native Java edit manipulator and host optimizer; incomplete markup protected | See bounded Java edit support below |
| Structure, folding, comments and tag typing | Host services with authored navigation and Java indentation settings | Not advertised as equivalent native services |
| Reformat Code / selection | Native Java whitespace plus markup-safe layout; meaningful text preserved | Not advertised |
| Arbitrary structural refactoring, browser debugging | Not advertised | Not advertised |

Native Java editing uses the project's imported Java dependencies. Opening a
`.jx` file as plain text or assigning it the `java` language ID bypasses the JX
adapter and does not produce this integration.

## VS Code

Build from `tooling/vscode` with Node 22 or newer:

```sh
npm ci
npm run package
```

Install `tooling/vscode/jx-language-0.2.0.vsix` through **Extensions: Install from
VSIX**. Set `jx.javaPath` in user settings if the extension host cannot find a
Java 25 executable as `java`. Remote workspaces run Java on the remote extension
host; install the JDK there. Use a full JDK with `lib/ct.sym`, rather than a runtime
image with only a `java` launcher. Remote execution is designed into the extension but
has not been verified on a remote host.

The extension highlights Java alongside HTML-style markup: tags, attributes,
quoted values, and `{Java expressions}` have distinct scopes. Install
**Language Support for Java by Red Hat 1.58.0** and let it import the Maven
project in Standard mode. Native Java operations use that project's actual JDT
classpath. The bridge is pinned to **JDT LS 1.63.0**; other Java extension versions
need a compatibility check. Its runtime must be Java 25 or newer.

Java member completion, completion imports, hover, overload signature help,
definitions, references, safe rename edits and Java diagnostics use JDT. Java
expressions inside attributes and children retain their lexical scope, and event
lambdas retain their `jx.Event` parameter type. Component-tag/prop completion,
hover, navigation and document symbols use the JX language server. Additional
semantic dependencies can be listed in `jx.classpath`, relative to each workspace
folder or as absolute paths.

- **JX: Restart Language Server** restarts its process. **JX: Show Language Server
  Output** opens its log channel.
- **JX: Create Component** prompts for a Java class name and destination.
- **JX: Build Application** runs the selected workspace's Maven Wrapper with
  `package`.
- **JX: Start Development Server** saves files, finds the nearest `pom.xml` for
  the active `.jx` file, and runs an ancestor Maven Wrapper with `-f` pointing to
  that module. `jx.devArguments` defaults to
  `dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev`; add options such as `-Djx.port=4174`.
  The command suppresses duplicate starts for the same module and shows its task
  terminal. **JX: Stop Development Server** stops the selected development task.
- `jx.mavenRepository` optionally selects a repository. If unset, an existing
  wrapper-root `.cache/maven-repository` is used, otherwise Maven's default.

The same [Maven development loop](maven.md) preserves compatible browser state
when `.jx` changes. Start/Stop controls own the process; editor restart and build
commands are not required for each save.

Opening an untrusted file permits highlighting; JX starts neither its language
server nor native Java requests or Maven tasks. The Red Hat extension owns its
normal Java import/build policy. JX build and dev tasks require explicit command
invocation in a trusted workspace. A virtual workspace is unsupported.

The test harness installs the VSIX into an isolated profile, then loads that
installed package in the actual editor. From `tooling/vscode`:

```sh
VSCODE_EXECUTABLE_PATH='/Applications/Visual Studio Code.app/Contents/MacOS/Code' npm test
```

Set the executable path for the editor being verified. The harness does not
silently download a different editor version. The recorded target is VS Code
1.138.0 on macOS ARM64; tests pass for bundled-server initialization, completion,
hover, navigation to `Counter.jx`, component symbols, invalid-prop diagnostics on
the original `.jx` line, and restart. A second isolated profile with workspace
trust enabled verifies that activation and restart leave Java stopped in an
untrusted workspace. The packaged editor suite also starts the real Maven dev
goal against an isolated copied Counter module, checks module/wrapper discovery,
saved input, repository selection, duplicate suppression, stop and restart.
Native Java tests exercise actual Method completions, imports, typed event
lambdas, incomplete expressions, hover/signatures, navigation to ordinary Java,
mapped references and rename, and Java errors on authored `.jx` lines. The
separate `npm run test:native` suite exercises the JDT extension commands,
working-copy ownership, collision rejection and cleanup in the actual editor.

Lowered Java exists in owned JDT working copies. It is never saved to disk and
does not create hidden dirty editor tabs or modify Maven classpaths. Edits that
cross generated wrappers fail atomically. Local rename is supported; class/file
rename and generated component references require additional mapping work.
Method rename can include open ordinary Java files; a closed Java target is
rejected before any edit so its document version can be validated. Open the
referenced Java file and retry. For Java-to-JX navigation, use **JX: Go to
Definition** or F12 in a Java file belonging to a JX Maven module. The stock Java
extension's separate context-menu navigation is not intercepted.
Remote-host behavior, formatting and browser debugging still need separate
implementation and tests. This is not a claim of every Java extension feature.

Packaging compiles the bridge against Red Hat Java 1.58.0. It can use an installed
copy or `JX_JAVA_EXTENSION`; a fresh checkout downloads the official pinned SDK
archive, checks its SHA-256 and extracts build dependencies into `.cache/java-sdk`.
The SDK/runtime is not included in the JX package. Details and the pinned checksum
are in the [extension guide](../tooling/vscode/README.md).

The extension pins `minimatch` 11.0.1 to avoid an advisory in its client's original
dependency range. The final audit reports four high-severity packaging-tree
findings propagated from the upstream `picomatch` advisory
`GHSA-fc5q-39xc-vjpf` through the existing `@vscode/vsce` 4.1.1 tool. The newest
published `picomatch` checked was 4.0.7, still within the affected range. The
packager was retained and the resulting VSIX tested; packaging and tokenizer-test
dependencies are excluded from the VSIX, which bundles the extension entry point
and its runtime dependencies.

## IntelliJ IDEA

The plugin targets the unified IntelliJ IDEA 2026.1.3 distribution, build
`IU-261.25134.95`. It depends on the bundled **Java plugin** (`com.intellij.java`),
platform and LSP modules. It does not depend on `com.intellij.modules.ultimate`.

JetBrains documents that LSP is available without a paid subscription in the
[unified distribution](https://blog.jetbrains.com/platform/2025/11/intellij-platform-2025-3-what-plugin-developers-should-know/).
The separate open-source IDE builds, older Community distributions, and Android
Studio are not supported by this adapter. The subscription statement is based on
JetBrains' documented platform contract; separate licensed and unlicensed IDE
sessions have not both been tested.

From `tooling/intellij`, build, test, and verify with Java 25:

```sh
./gradlew --no-daemon test buildPlugin verifyPlugin
```

That command resolves the pinned IDEA distribution. To use an existing matching
installation, the locally executed variant is:

```sh
./gradlew --gradle-user-home ../../.cache/gradle --no-daemon \
  '-PideaLocalPath=/Applications/IntelliJ IDEA.app' test buildPlugin verifyPlugin
```

Gradle 9.7.1 is pinned with its distribution SHA-256. The IntelliJ Platform Gradle
plugin is 2.18.1 and Plugin Verifier is 1.411. Install
`tooling/intellij/build/distributions/jx-intellij-0.3.2.zip` through **Settings →
Plugins → Install Plugin from Disk**.

Version 0.3.1 removes the injected-fragment background from normal JX editing,
uses theme HTML colors (customizable under **Editor → Color Scheme → JX**), and
reuses unchanged compiler projections. The [performance and presentation report](intellij-performance.md)
records the reproduced defects, before/after measurements and their limits.

Set the Java executable under **Settings → Languages & Frameworks → JX**. The
default uses the IDE's bundled Java 25 runtime. The same page accepts additional
classpath entries. Restart using **Tools → Restart JX Language Server** after
changing these settings. IntelliJ owns server lifecycle and protocol integration;
its Language Services widget exposes server status. Enable
`#com.intellij.platform.lsp` in **Help → Diagnostic Tools → Debug Log Settings**
when detailed protocol logs are needed.

The adapter provides mixed Java/HTML highlighting (tags, attributes, quoted
values, entities and Java expression islands). A complete in-memory Java
compilation unit is assembled through JetBrains' language-injection API, retaining
Java scopes and callback types. Native Java PSI supplies completion, navigation,
references and safe source edits. Java declarations in `.jx` participate in
project lookup, including Maven's `src/main/jx` outside registered Java source
roots. Main sources cannot resolve test-only JX declarations. In standard Maven
output directories, manifest-owned generated counterparts are treated as plain
text for IDE indexing, removing duplicate navigation/refactoring targets. Their
physical Java file type, contents and compiler source roots remain unchanged.
Custom generation directories are outside this ownership-filtering profile.
Explicit markup references connect component tags and record props to those
declarations. The shared server supplies JX diagnostics, component completion,
hover, navigation and document symbols. It starts only for trusted projects.
Java extraction uses a content-addressed JAR under the IDE system directory;
concurrent projects share the immutable file. The process working directory is
stable, while project roots are supplied through LSP initialization.

**Tools → Start JX Development Server** saves documents and chooses the active
file's Maven module and ancestor wrapper. The Run console provides clickable
URLs and Stop/Rerun controls. Starting again focuses the existing process;
**Tools → Stop JX Development Server** and project disposal stop owned processes.
Version 0.3.2 also provides a green Run gutter icon beside the class matching a
`.jx` filename, such as `App` in `App.jx`. It launches this same containing-module
workflow, preserving trust checks and process ownership. It does not run a
component in isolation or invoke a JVM entry point. The default port is 4173;
the console URL reflects any configured override.
The JX settings page also accepts development arguments (one per line), a Maven
repository override, and optional use of an existing project cache. See the
[plugin guide](../tooling/intellij/README.md).

Plugin Verifier passes for `IU-261.25134.95` with **zero internal API usages**.
The 15 deprecated and 18 experimental API warnings are retained; compatibility
with future IDEs is not inferred. All **89 IntelliJ SDK tests** pass for 0.3.1.
The declared build range is intentionally narrow. SDK tests cover mixed-language
highlighting and lexer restarts, module discovery/settings, a live LSP-manager
integration, and Run console process lifecycle including duplicate starts,
graceful stop, project disposal, and trust blocking. The lifecycle tests launch a
real Java fixture through the wrapper boundary; Maven rebuild behavior is tested
separately in the browser suite. Tests isolate JX with required platform dependencies.

Native acceptance tests use the actual Java engine for member completion in
methods, attributes and child expressions, typed event completion while markup
is incomplete, source navigation, cross-file reference searches and rename,
markup references, completion imports, native Java inspections, class/file rename
and undo. Synthetic generated code is read-only;
source edits must map to authored Java or explicit markup references.
Version 0.3.0 adds a registered injection-host manipulator required by native
Java intention and ModCommand edits, fixing Add Import and inspection quick fixes.
Its host import optimizer makes Optimize Imports available from markup, retains
component references and import comments, and declines unsafe incomplete-source
changes. Structure view and folding target authored `.jx`, including unsaved edits.
Context-aware comments, quote pairs, automatic closing tags and paired-tag Enter
use normal editor actions. Comments inside tag attributes are deliberately not
inserted because XML comment delimiters are invalid there.

Reformat Code and selection formatting use the native Java formatter on a detached
projection, accepting only whitespace edits wholly within exact copied Java ranges.
The host layout model preserves meaningful markup text, strings and comments,
and follows Java indent/tab settings. If native formatting rewrites non-whitespace
tokens, such as Javadoc or text-block contents, Java whitespace mapping is declined.
This bounded formatter is not arbitrary Java/XML restructuring.

The [TSX/Java architecture study](decisions/0004-intellij-mixed-language-editing.md)
explains why the current projection does not provide universal Java feature parity.
Arbitrary Java refactorings and browser debugger stepping remain unverified.
A broader IDE/product matrix, remote development, and a
manual install-and-edit walkthrough remain separate checks.

The 0.3.1 IntelliJ package was built and verified locally on 2026-11-19. Its additional checks
cover light/Darcula editor painting, theme overrides, incremental token updates,
projection reuse/invalidation, JX-only packages and rename offsets after leading
whitespace. The earlier scoped Maven build passed 49 core/compiler/language-server
tests, including the five markup-comment regressions; those artifacts are unchanged
by the 0.3.1 editor fixes. Package verification confirms the ZIP contains the current
compiler and shared server byte-for-byte. ZIP SHA-256:
`0a42ed36138117b5188a967f5e07fb780fb541b1255d0892cf5d58718fff8e8b`.

The 0.3.2 package adds the Run gutter action. Plugin Verifier reports compatibility
with `IU-261.25134.95`, zero internal API usages, and the same 15 deprecated/18
experimental API warnings. All **92 IntelliJ SDK tests** pass,
including invoking the actual gutter click action, selecting the containing Maven
module and displaying its process in the Run console. The process-boundary fixture
uses a Java test server behind a temporary wrapper; it does not rebuild the counter
application during the IDE test. Package verification confirms the unchanged
compiler/server bytes. ZIP SHA-256:
`68f9ff39ec24c0c11cd9e27676e9ecbe5bf2f41bcec1ba005fe4933ed82e2288`.

## Verification scope

Editor integration tests exercise the actual native Java engines, alongside
shared JX protocol services. A successful ZIP build alone does not establish
IDE compatibility. Preserve the
separate compiler, protocol, actual-editor, and Plugin Verifier checks when
changing these packages.

The manual [editor packaging workflow](../.github/workflows/tooling.yml) builds the
shared server and VSIX, resolves the pinned IDEA SDK, runs IntelliJ tests and
Plugin Verifier, checks bundled-artifact identity, and uploads both installables.
It does not run the VS Code UI harness. The workflow is configured but has not
yet been executed remotely; local macOS results do not establish Linux CI success.

The 0.2.0 packages were verified locally on 2026-11-18. The complete installed
VS Code suite and separate native JDT suite passed; the latter also proves Maven
main/test visibility. At that revision, package identity checks confirmed that
both installers contained the matching shared server and compiler/Java bridge
dependencies. The IntelliJ-only 0.3.1 verification above does not rebuild the VSIX.

## Coverage and CRAP scores with Cherry Test

[Cherry Test](https://www.cherry-test.com/) runs the unit tests of the Maven modules
that have them, measures line coverage with JaCoCo and reports a CRAP score per
method (complexity against coverage). The modules are listed in
[`cherry-test.yaml`](../cherry-test.yaml); the parent POM carries the JaCoCo agent and
the build-directory property Cherry Test sets.

```sh
cherry-test --kinds unit --no-open
```

The report is `target/cherry-test/latest/reports/index.html`. Cherry Test starts
Maven in each module's directory and looks for `mvnw` there, so with the single
wrapper at the repository root a `mvn` command must be on the `PATH`; the modules
must already be installed in the local repository that `mvn` uses.

Baseline on 22 November 2026, Cherry Test 0.3.16, 82 unit tests:

| Module | Lines covered by unit tests |
| --- | --- |
| `jx-compiler` | 899 of 997 (90.2%) |
| `jx-elm` | 103 of 110 (93.6%) |
| `jx-language-server` | 317 of 372 (85.2%) |
| `jx-maven-plugin` | 395 of 637 (62.0%) |
| `jx-core` | 164 of 305 (53.8%) |

427 methods; 15 have a CRAP score of 30 or more, the worst being `DevMojo.execute`
(240). These numbers see JVM unit tests only. Code that runs in the browser through
TeaVM, which is most of `jx-core` and all of `jx-react`, is exercised by the
conformance bench and is not counted here. Nothing fails on a score yet; the figures
are a baseline, not a gate. Mutation testing with PIT through Cherry Test needs a
newer Cherry Test than 0.3.16 and is not set up.
