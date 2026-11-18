# JX for IntelliJ IDEA

Install the ZIP built by `./gradlew buildPlugin` in IntelliJ IDEA 2026.1.3
(`IU-261.25134.95`) through **Settings → Plugins → Install Plugin from Disk**.
The shared language-server JAR must be built first; see the repository's tooling
guide for the Maven build and verified compatibility limits.

Inside `.jx`, HTML/component tags, attributes, quoted attribute values, entities,
and `{Java expressions}` use the IDE theme's markup and Java colors. Nested
components and fragments keep their surrounding Java context; text, comments,
and Java strings containing angle brackets stay text. Highlighting is lexical;
compiler diagnostics and navigation come from the shared language server.

Open a `.jx` file in a trusted Maven project and choose **Tools → Start JX
Development Server**. JX selects the nearest `pom.xml` above that file and finds
the Maven Wrapper in that module or a parent directory. Output appears in the
**Run** window as **JX Dev · module-name**, with clickable server URLs and
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
