# JX Java Components

JX editing through the shared Java language server: `.jx` highlighting, diagnostics,
completion, hover, definition navigation, and document symbols. The extension
colors HTML/SVG tag names, attribute names, quoted values, entities, and nested
markup inside `.jx`, while Java expressions in braces retain Java highlighting.
Java strings, comments, and ordinary generic types retain their Java scopes.
The colors follow the selected VS Code theme.
The extension
bundles its language server and requires a Java 25 executable. Set `jx.javaPath`
in user settings when `java` is not available on the extension host's PATH.

The language server starts only in trusted workspaces. In remote workspaces Java
and Maven run on the remote extension host. Additional semantic classpath entries
can be set with `jx.classpath`. The Output panel's **JX Language Server** channel
and **JX: Restart Language Server** command provide diagnostics and recovery.

**JX: Create Component** writes a component selected by the user. To develop an
application, open one of its `.jx` files and run **JX: Start Development Server**.
The extension saves open files, finds that file's nearest `pom.xml`, then finds
the Maven Wrapper in the module or an ancestor. This works when the workspace is
the whole JX repository: the application module is selected instead of the root
aggregator POM. The task terminal shows Maven and original-source diagnostics.

Repeated Start commands keep one development task per module. **JX: Stop
Development Server** stops the active module's task and its server; if several
other modules are running, it lets you select one. The task can then be started
again. **JX: Build Application** uses the same module discovery and save behavior.
No project build runs merely because a file is opened. Restricted workspaces
cannot launch Maven. State preservation during hot reload is implemented by the
JX development runtime; the editor uses the same explicit Maven dev goal.

`jx.devArguments` is a list of Maven arguments, without shell parsing. For example:

```json
{
  "jx.devArguments": [
    "dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev",
    "-Djx.port=4173"
  ]
}
```

Keep module selection out of that list: the extension supplies `-f` itself.
Additional application URL/system properties can be included as separate list
entries when the application supports them. `jx.mavenRepository` optionally sets
the Maven local repository, using an absolute path or a path relative to the
workspace folder. Otherwise an existing `.cache/maven-repository` beside the
wrapper is used; if absent, Maven uses its normal default. An explicit
`-Dmaven.repo.local=...` in the argument list takes precedence.

Build the repository's language-server JAR first, then use Node 22 or newer and run `npm ci` and
`npm run package` in this directory. Install `jx-language-0.1.0.vsix` using VS
Code's **Extensions: Install from VSIX** action. This is a local development
package; no marketplace publication is implied.

Rename refactoring, formatting, debugger stepping, and complete embedded-Java IDE
semantics are not advertised. Feature depth is defined by the shared compiler and
language server. See the repository's tooling documentation for verified coverage.

The packaged-extension tests use the installed VS Code executable and an isolated
copy of the real Counter application. They cover module/wrapper discovery, saved
edits, argument boundaries in paths with spaces, repository configuration,
duplicate Start commands, real Maven/HTTP startup, Stop/restart, and workspace
trust, alongside completion, diagnostics, hover, definitions, and symbols.
TextMate/Oniguruma tokenization tests also check the installed grammar against
the editor's real Java grammar, including nested tags, fragments, quoted
attributes, and Java lambdas with nested braces.
