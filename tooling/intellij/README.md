# JX for IntelliJ IDEA

Install the ZIP built by `./gradlew buildPlugin` in IntelliJ IDEA 2026.1.3
(`IU-261.25134.95`) through **Settings → Plugins → Install Plugin from Disk**.
The shared language-server JAR must be built first; see the repository's tooling
guide for the Maven build and verified compatibility limits.

Inside `.jx`, HTML/component tags, attributes, quoted attribute values, entities,
and `{Java expressions}` use the IDE theme's markup and Java colors. Nested
components and fragments keep their surrounding Java context; text, comments,
and Java strings containing angle brackets stay text. Highlighting is lexical;
ordinary Java editing uses the bundled IntelliJ Java plugin. The compiler creates
a complete Java PSI projection through the platform's language-injection API,
preserving lexical variables and callback types inside markup expressions.
Native Java completion, navigation, references and safe rename edits map back to
the `.jx` source. Shared language-server services provide JX component/prop
semantics and compiler diagnostics. Install the updated **0.3.2** package.

Java regions use the normal editor background. Markup inherits the active theme's
HTML tag, attribute and value colors; optional overrides are under **Settings →
Editor → Color Scheme → JX**. Other injected languages and the global color scheme
are unaffected.

Version 0.3.1 also fixes incremental Java/markup coloring after tag edits and
reuses unchanged compiler projections instead of rebuilding them for repeated
editor queries. See the [measured scope and remaining limits](../../docs/intellij-performance.md).

The host editor also provides **Structure / File Structure**, Java and markup
folding, context-aware comment actions, attribute quote pairs, closing tags, and
indentation when pressing Enter between paired tags. Tag insertion respects
IntelliJ's HTML/XML closing-tag preference. Structure entries navigate to the
authored `.jx` declaration and omit generated component helpers.

**Reformat Code** uses the bundled Java formatter for safely mapped Java whitespace
and a JX layout model for markup. Strings, comments and meaningful markup text are
preserved. This is not a claim that all Java refactorings or XML editing features
are supported: JX still uses a typed Java projection rather than the native
Java/XML expression PSI that IntelliJ's TSX implementation owns. The
[mixed-language architecture decision](../../docs/decisions/0004-intellij-mixed-language-editing.md)
records the research and remaining structural work. Use the
[tooling capability matrix](../../docs/tooling.md) for the verification boundary.

Open a `.jx` file in a trusted Maven project and choose **Tools → Start JX
Development Server**, or click the green Run gutter icon beside its matching class
declaration (for example `class App` in `App.jx`). The gutter action runs the
containing Maven application, not that component in isolation or a JVM `main`.
JX selects the nearest `pom.xml` above that file and finds
the Maven Wrapper in that module or a parent directory. Output appears in the
**Run** window as **JX Dev · module-name**, with clickable server URLs and
the default address `http://127.0.0.1:4173` unless a port is configured. It includes
Stop/Rerun controls. Starting again focuses the existing process; stop it before
switching applications. **Tools → Stop JX Development Server**, the console Stop
button, and closing the project terminate the owned server and active build.

In **Settings → Languages & Frameworks → JX**, set the Java 25 executable to the
JDK's `bin/java`. Development arguments use one argument per line, without shell
quoting. For example:

```text
dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev
-Djx.port=4299
```

The selected source determines the module, so `-f` and `--file` are rejected in
these arguments. An optional repository override accepts an absolute path or a
path relative to the Wrapper directory. By default, an existing
`.cache/maven-repository` beside the Wrapper is reused; turn off that option to
use Maven's standard repository. Changes apply to the next development run.

The Maven development server owns browser updates and state-preservation rules.
The editor starts it only through an explicit action; opening `.jx` files does
not run project build scripts. Start requires a file within the project's content
roots. One server is allowed per IntelliJ project, and process launch runs off the
UI thread. Runtime failures remain visible in the Run console.
