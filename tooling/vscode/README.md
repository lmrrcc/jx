# JX Java Components

JX combines the compiler's markup services with native Java editing from
**Language Support for Java by Red Hat 1.58.0**. Java completion, hover, overload
signature help, definitions, references, local-variable and method rename, and diagnostics
work in ordinary Java code and Java expressions inside `.jx` attributes and
children. Event lambda parameters retain their Java types. Component tags and
props use the shared JX language server. The extension
colors HTML/SVG tag names, attribute names, quoted values, entities, and nested
markup inside `.jx`, while Java expressions in braces retain Java highlighting.
Java strings, comments, and ordinary generic types retain their Java scopes.
The colors follow the selected VS Code theme.
In ordinary `.java` files within a Maven module containing JX source, **F12** and
**JX: Go to Definition** map native Java definitions back to authored `.jx`.
The shortcut remains unchanged outside those modules. The ordinary Java extension's
separate **Go to Definition** context-menu action does not remap JX projections;
use the JX action when navigating from Java into JX.
The extension
bundles its language server and a small JDT bridge, and requires a Java 25 executable. Set `jx.javaPath`
in user settings when `java` is not available on the extension host's PATH.

Native Java editing requires the Maven project to be imported by the official
Java extension in Standard mode. Its runtime must also be Java 25 or newer; the
verified Java extension includes a suitable runtime. An explicit
`java.jdt.ls.java.home` override must satisfy that requirement. The bridge is
versioned against Red Hat Java 1.58.0 / JDT LS 1.63.0; other versions require a
compatibility check before support is advertised.

JX launches its language server and requests native Java analysis only in trusted workspaces. In remote workspaces Java
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

The JDT bridge keeps lowered Java in owned in-memory working copies in the imported
Maven project. It does not write Java mirrors, change project classpaths, or create
hidden dirty editors. Existing generated Java is overlaid only when the generator's
output manifest and source map both prove ownership; the files remain unchanged.
Main and test projections retain their separate Maven source scopes. References
and edits map back to `.jx`. Edits that would
change generated markup fail atomically; ordinary Java targets must be open before
renaming a method from JX so their versions can be checked. Local-variable and method
rename use native JDT references. Class/file rename, generated component
references, formatting, and arbitrary Java refactorings are not advertised.
Closed sessions release their working copies. The bridge accepts up to 256 files
and 16 Mi UTF-16 code units per project, with a 1 Mi code-unit limit per file and
32 concurrent project sessions. Changed document versions invalidate pending results.

Build the repository's language-server JAR first, then use Node 22 or newer and run `npm ci` and
`npm run package` in this directory. Install `jx-language-0.2.0.vsix` using VS
Code's **Extensions: Install from VSIX** action. This is a local development
package; no marketplace publication is implied.

Packaging compiles the small Java bridge against an installed Red Hat Java 1.58.0
SDK, or `JX_JAVA_EXTENSION` when explicitly set. On a fresh checkout it downloads
the official pinned Linux VSIX as a platform-independent compile SDK, verifies
SHA-256 `45049a2273c78814287fb31a45255e5ce1fdbe34161bb9f1ba8f62a6aa84bdfc`,
and extracts only metadata and Java compiler dependencies to `.cache/java-sdk`.
The SDK and its bundled runtime are not redistributed in the JX VSIX. The first
download requires Marketplace network access; subsequent builds use the cache.
Debugger stepping and complete Java IDE parity are not advertised. See the
repository's tooling documentation for verified coverage.

The packaged-extension tests use the installed VS Code executable and an isolated
copy of the real Counter application. They cover module/wrapper discovery, saved
edits, argument boundaries in paths with spaces, repository configuration,
duplicate Start commands, real Maven/HTTP startup, Stop/restart, and workspace
trust, alongside completion, diagnostics, hover, definitions, and symbols. The
native editing fixture first builds the application and explicitly imports its
generated source root with Maven's build-helper plugin. It verifies that native
editing preserves generated files, maps Java-to-JX navigation, and renames a method
across JX and an open ordinary Java file.
The installed Red Hat Java extension is included in the isolated test environment.
`npm run test:native` separately exercises the native JDT bridge, including
ordinary-Java source collision protection, separate main/test scopes, and working-copy cleanup.
TextMate/Oniguruma tokenization tests also check the installed grammar against
the editor's real Java grammar, including nested tags, fragments, quoted
attributes, and Java lambdas with nested braces.
