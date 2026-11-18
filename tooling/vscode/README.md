# JX Java Components

JX editing through the shared Java language server: `.jx` highlighting, diagnostics,
completion, hover, definition navigation, and document symbols. The extension
bundles its language server and requires a Java 25 executable. Set `jx.javaPath`
in user settings when `java` is not available on the extension host's PATH.

The language server starts only in trusted workspaces. In remote workspaces Java
and Maven run on the remote extension host. Additional semantic classpath entries
can be set with `jx.classpath`. The Output panel's **JX Language Server** channel
and **JX: Restart Language Server** command provide diagnostics and recovery.

**JX: Create Component** writes a component selected by the user. **JX: Build
Application** and **JX: Start Development Server** run the selected workspace's
Maven Wrapper only on explicit invocation. `jx.devArguments` configures the
development goal. No project build runs merely because a file is opened.

Build the repository's language-server JAR first, then use Node 22 or newer and run `npm ci` and
`npm run package` in this directory. Install `jx-language-0.1.0.vsix` using VS
Code's **Extensions: Install from VSIX** action. This is a local development
package; no marketplace publication is implied.

Rename refactoring, formatting, debugger stepping, and complete embedded-Java IDE
semantics are not advertised. Feature depth is defined by the shared compiler and
language server. See the repository's tooling documentation for verified coverage.
