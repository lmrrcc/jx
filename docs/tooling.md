# JX development tooling

Both editor packages bundle the same executable Java language-server JAR. The
server uses the JX compiler for component metadata and diagnostics; the adapters
do not maintain independent semantic parsers. Java 25 is required. Build the
server and its Java dependencies before packaging either editor:

```sh
./mvnw -B -ntp -pl jx-language-server -am package
```

The packaged server comes from
`jx-language-server/target/jx-language-server-0.1.0-SNAPSHOT.jar`. Rebuild both
packages after changing the compiler or server. No editor marketplace publication
has been performed.

After building both packages, verify that they contain the current server
byte-for-byte and print their SHA-256 hashes from the repository root:

```sh
java tooling/vscode/VerifyPackages.java
```

## VS Code

Build from `tooling/vscode` with Node 22 or newer:

```sh
npm ci
npm run package
```

Install `tooling/vscode/jx-language-0.1.0.vsix` through **Extensions: Install from
VSIX**. Set `jx.javaPath` in user settings if the extension host cannot find a
Java 25 executable as `java`. Remote workspaces run Java on the remote extension
host; install the JDK there. Remote execution is designed into the extension but
has not been verified on a remote host.

The extension supplies `.jx` highlighting and connects diagnostics, completion,
hover, definition navigation, and document symbols to the server. Additional
semantic dependencies can be listed in `jx.classpath`, relative to each workspace
folder or as absolute paths.

- **JX: Restart Language Server** restarts its process. **JX: Show Language Server
  Output** opens its log channel.
- **JX: Create Component** prompts for a Java class name and destination.
- **JX: Build Application** runs the selected workspace's Maven Wrapper with
  `package`.
- **JX: Start Development Server** runs that wrapper with `jx.devArguments`, whose
  default is `dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev`. Set additional arguments
  such as `-f` when the application is a nested Maven module. Configure the Maven
  plugin's development web directory for that application's generated assets.

Opening an untrusted file permits highlighting, but starts neither Java nor a
project build. Build and dev tasks require explicit command invocation in a
trusted workspace. A virtual workspace is unsupported.

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
untrusted workspace. Remote-host behavior and the build/dev task UI still need
separate end-to-end tests. Rename, formatting, and debugging are not advertised
capabilities.

The extension pins `minimatch` 11.0.1 to avoid an advisory in its client's original
dependency range. The packaging dependency tree still reports an upstream
`picomatch` advisory through `@vscode/vsce`; the newest published `picomatch`
checked for this setup was 4.0.7, within the affected range. Those packaging
dependencies are excluded from the VSIX by bundling only the extension entry
point and its runtime dependencies.

## IntelliJ IDEA

The plugin targets the unified IntelliJ IDEA 2026.1.3 distribution, build
`IU-261.25134.95`. It uses JetBrains' LSP integration and depends on the platform
and LSP modules. It does not depend on `com.intellij.modules.ultimate`.

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
`tooling/intellij/build/distributions/jx-intellij-0.1.0.zip` through **Settings →
Plugins → Install Plugin from Disk**.

Set the Java executable under **Settings → Languages & Frameworks → JX**. The
default uses the IDE's bundled Java 25 runtime. The same page accepts additional
classpath entries. Restart using **Tools → Restart JX Language Server** after
changing these settings. IntelliJ owns server lifecycle and protocol integration;
its Language Services widget exposes server status. Enable
`#com.intellij.platform.lsp` in **Help → Diagnostic Tools → Debug Log Settings**
when detailed protocol logs are needed.

The adapter provides highlighting and the server's diagnostics, completion,
hover, navigation, and document symbols. It starts only for trusted projects.
Java extraction uses a content-addressed JAR under the IDE system directory;
concurrent projects share the immutable file. The process working directory is
stable, while project roots are supplied through LSP initialization.

Plugin Verifier reports compatibility with `IU-261.25134.95`, including four
deprecated and eight experimental API usages in JetBrains' project-trust API.
These warnings are retained; compatibility with future IDEs is not inferred.
The declared build range is intentionally narrow. All three SDK tests pass: file
registration/highlighting, file selection, and a live LSP-manager integration
test that initializes the bundled process and requests completion. The tests use
real temporary files and isolate JX with its required platform dependencies.

The adapter does not provide native Java refactoring, rename, formatting, debugger
stepping, or generated-Java source hiding. A broader IDE/product matrix, remote
development, and a manual install-and-edit walkthrough remain separate checks.

## Verification scope

Editor integration proves that supported protocol features reach the editor. It
does not make every Java construct understood by the language server, and a
successful ZIP build alone does not establish IDE compatibility. Preserve the
separate compiler, protocol, actual-editor, and Plugin Verifier checks when
changing these packages.

The manual [editor packaging workflow](../.github/workflows/tooling.yml) builds the
shared server and VSIX, resolves the pinned IDEA SDK, runs IntelliJ tests and
Plugin Verifier, checks bundled-server identity, and uploads both installables.
It does not run the VS Code UI harness. The workflow is configured but has not
yet been executed remotely; local macOS results do not establish Linux CI success.
