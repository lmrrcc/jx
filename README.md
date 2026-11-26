<p align="center">
  <img src="docs/assets/jx-mark.png" width="160" height="160" alt="JX project mark: forest-green J and terracotta X on an ivory tile.">
</p>

<h1 align="center">JX — Java eXtended UI</h1>

<p align="center">
  <strong>React applications, authored in Java.</strong><br>
  Typed components. Familiar hooks. Expressive markup.
</p>

<p align="center">
  <a href="#components">Components</a> ·
  <a href="#quick-start">Quick start</a> ·
  <a href="#development">Development</a> ·
  <a href="#tooling">Tooling</a> ·
  <a href="#documentation">Documentation</a>
</p>

JX brings Java classes, records, and lambdas to interactive browser applications.
Write your interface in `.jx`, keep application behavior in Java, and let the
actual React runtime render it in the browser. Use hooks for local state,
an optional Elm-style architecture for application workflows, and Spring Boot
when you need a backend.

**Working prototype.** The compiler, browser runtime, Maven plugin, language
server, and local editor packages are implemented. Runnable examples and Chrome
tests exercise the complete Java → TeaVM → React path. The
[implementation status](docs/status.md) records verified behavior and open work.

## Components

A `.jx` file is a Java compilation unit with markup expressions.
`@Component` marks a component; a nested record defines its typed props.

**Counter.jx**

```java
package jx.example.counter;

import jx.EventHandler;
import jx.Hooks;
import jx.Component;
import jx.State;
import jx.View;

@Component
public final class Counter {
    public record Props(int initial, String label) {}

    public static View render(Props props) {
        State<Integer> count = Hooks.useState(props.initial());

        return (
            <button onClick={increment(count)}>
                {props.label()}: {count.value()}
            </button>
        );
    }

    private static EventHandler increment(State<Integer> count) {
        return event -> count.update(value -> value + 1);
    }
}
```

**App.jx**

```java
package jx.example.counter;

import jx.Component;
import jx.View;

@Component
public final class App {
    public static View render() {
        return (
            <main>
                <Counter initial={0} label="First counter" />
                <Counter initial={10} label="Second counter" />
            </main>
        );
    }
}
```

Each mounted counter owns its state. `count.value()` is the current render's
snapshot; `count.update(...)` queues a functional update. React invokes the
components and schedules their renders.

`increment(count)` creates the event handler during rendering; the returned
handler increments the counter only when clicked. The helper captures that
component instance's `State`, so the two counters stay independent.

The [runnable counter](examples/counter-app) also demonstrates queued updates,
parent rerenders, and remounting. Read the [language guide](docs/language.md) for
children, keys, fragments, collections, Java expressions, and source diagnostics.

## Quick start

Install **JDK 25**, **Node.js 22 or later**, and **npm**. Google Chrome is needed
for browser tests. The checked-in Maven Wrapper downloads Maven 3.9.11.

```sh
git clone https://github.com/lmrrcc/jx.git
cd jx
npm ci
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository install
```

This builds and installs the framework's current snapshot artifacts locally.
Run the following commands from the repository root.

### Counter

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository \
  -f examples/counter-app/pom.xml \
  dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev -Djx.port=4180
```

Open [127.0.0.1:4180](http://127.0.0.1:4180). After loading, both counters
continue working with networking disabled: their event handlers execute in the
browser.

With IntelliJ plugin 0.3.2, open `App.jx` and click the green Run gutter icon next
to `class App`, or choose **Tools → Start JX Development Server**. Open the URL
printed in the Run console (port **4173** by default). `Main.java` is a TeaVM
browser entry point; launching it with the ordinary Java Run action on the JVM
cannot execute the JavaScript interop methods.

### Todo application

The [Todo example](examples/todo-app) combines controlled forms, validation,
filters, route parameters, browser history, context, a custom hook, an Elm store,
and cancellable HTTP. Its [Spring Boot API](examples/todo-api) persists tasks in H2
under `.local/`, with Flyway managing schema changes.
Tasks are private to the authenticated account. Configure `JX_DEMO_PASSWORD_HASH`
with a Spring encoded password (for example a `{bcrypt}` hash); no default password
is supplied. The example account is `demo`; optional `JX_OTHER_PASSWORD_HASH`
enables the separate `other` account. Keep these values in server configuration.

Stop the counter server first; these development commands also use port 4180.

**Terminal 1 — start the API:**

```sh
java -jar examples/todo-api/target/todo-api-0.1.0-SNAPSHOT.jar
```

**Terminal 2 — start the frontend development server:**

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository \
  -f examples/todo-app/pom.xml \
  dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev \
  -Djx.port=4180 -Djx.apiOrigin=http://127.0.0.1:8181 \
  -Djx.apiSessionCookie=JXSESSION
```

Open [127.0.0.1:4180/todos](http://127.0.0.1:4180/todos). The development server
proxies `/api/` requests to the backend on port 8181 and applies frontend updates
as you save. The backend also serves the normally packaged application at
[127.0.0.1:8181/todos](http://127.0.0.1:8181/todos).

Use **Sign in** to open the native Spring form. The example remains bound to
loopback; authentication and CSRF are enforced by Spring in both hosting modes.
See the [session contract and limits](docs/evidence/session-csrf-todo.md) and
[Maven guide](docs/maven.md). A TLS deployment must set `JX_SESSION_SECURE=true`
and supply its own identity, rate and session-management policy.

## Development

Save Java, `.jx`, or CSS to trigger a Maven rebuild. A successful build applies
the new application while preserving compatible component state. Failed builds
report diagnostics at the original `.jx` location and leave the last successful
application interactive.

- **Scalar hook state is preserved automatically.** Records, collections, and
  application stores use a typed `StateCodec`; the Todo example includes one.
- **Effects clean up and restart.** State moves into the rebuilt Java generation;
  existing Java service objects are not carried across.
- **Incompatible hooks or codec schemas reset affected state.** The development
  client reports the reset. Uncontrolled DOM state is outside this contract.

The browser document and URL stay in place during supported updates. Restart
development and reload after changing vendor dependencies or the HTML document
shell. Production assets remain separate from development output.

Read the [development workflow](docs/maven.md#development-loop) and
[state-preservation contract](docs/runtime.md#state-preserving-development-replacement)
for details. Ctrl+C stops the server and its active build.

## Tooling

Both editor packages color HTML/component markup alongside Java: tags,
attributes, quoted values, and embedded expressions have distinct highlighting.
Java editing delegates to **IntelliJ's Java plugin** and **VS Code's Red Hat Java
extension**, including Java expressions inside markup. The shared JX language
server adds component/property semantics and compiler diagnostics. Updated editor
packages are version **0.3.2 for IntelliJ** and **0.2.0 for VS Code**; see the
tested operations and limits below.

| Tool | Build, edit, and run |
| --- | --- |
| **Maven** | Generate Java, report mapped diagnostics, bundle browser assets, and run the development loop. [Build guide](docs/maven.md) |
| **VS Code** | Install the local VSIX. Use **JX: Start Development Server** and **JX: Stop Development Server** from the Command Palette. [Setup](docs/tooling.md#vs-code) |
| **IntelliJ IDEA** | Install the local plugin ZIP. Use **Tools → Start JX Development Server** and **Stop JX Development Server**, with Stop/Rerun in the Run console. [Setup](docs/tooling.md#intellij-idea) |

Open a `.jx` file in a trusted project to select its nearest Maven module.
The editor finds the ancestor Maven Wrapper, saves files before starting, and
avoids duplicate development processes. Development arguments and the Java
executable are configurable.

Verified editor targets are **VS Code 1.138.0** on macOS ARM64 and unified
**IntelliJ IDEA 2026.1.3** (`IU-261.25134.95`). The
[tooling guide](docs/tooling.md) describes installation, tested features, and
compatibility boundaries. Editor packages and framework artifacts currently use
local versions; Maven Central, npm, and editor marketplaces have no JX release yet.

## Architecture and scope

`.jx` → generated Java → `javac` → TeaVM → React + React DOM

JX lowers markup into ordinary Java and retains source mappings. TeaVM compiles
browser-compatible bytecode to JavaScript. The typed bridge connects Java
components, props, events, and hooks to React, which owns component invocation,
reconciliation, lifecycle, and scheduling.

Use `useState` or `useReducer` for component state, context for shared
dependencies, and the optional Elm Model–View–Update layer for application
workflows. Pure updates produce immutable models and commands; commands execute
outside rendering, while subscriptions have managed lifetimes. React observes
the store's committed snapshots.

The core has no Spring or persistence dependency. Separate modules provide the
React bridge, browser HTTP, routing, and Elm state. The baseline is Java 25,
TeaVM 0.16.0, and React/React DOM 19.3.1.

Current boundaries are explicit:

- Browser Java follows the [supported runtime profile](docs/runtime.md);
  JVM threading, blocking I/O, reflection, and server libraries are outside it.
- React interop works through typed bindings. Individual component libraries
  need bindings and tests; portals, Suspense, SSR, hydration, and other advanced
  features remain tracked work.
- Native Java completion, navigation and mapped source edits are integrated with
  the editors' Java engines. Supported rename/refactoring operations are bounded
  by the [editor compatibility profile](docs/tooling.md#java-editing-matrix); whole-file formatting
  and end-to-end `.jx` browser debugging remain work in progress.

See the [compatibility and implementation status](docs/status.md) before choosing
features for an application.

## Documentation

| Guide | Covers |
| --- | --- |
| [Language](docs/language.md) | Components, props, markup, expressions, and diagnostics |
| [Maven](docs/maven.md) | Build configuration, generated files, and hot updates |
| [Runtime](docs/runtime.md) | Hooks, Elm state, React interop, and state migration |
| [Browser services](docs/browser.md) | Typed JSON HTTP, cancellation, routing, and history |
| [Editor tooling](docs/tooling.md) | VSIX/ZIP packaging, installation, and verification |
| [Language server](docs/language-server.md) | Analysis, project discovery, and editor feature limits |
| [Implementation status](docs/status.md) | Delivered capabilities, evidence, and next work |
| [Architecture](docs/decisions/0001-java-framework-baseline.md) | Accepted design and module boundaries |
| [Project specification](docs/JX_Astra_Max_Prompt_v2.txt) | Complete intended scope and acceptance criteria |

## Contributing

Start with [AGENTS.md](AGENTS.md) for repository conventions and
[implementation status](docs/status.md) for open work. Keep public contracts
typed, include a compiling consumer example for new capabilities, and add checks
that exercise observable behavior.

`java tools/CheckBaseline.java` checks the [version manifest](docs/evidence/versions.tsv)
against the build files and validates the [capability ledger](docs/evidence/capabilities.tsv)
and the portfolio registers;
after changing a pinned version, rerun it with `--write-versions`.

`main` accepts only pull requests whose Verify check passed. To catch failures
before a push, run `java tools/PrePush.java --install` once per clone: `git push`
then runs the same steps locally and refuses the push when one fails.

The Maven build runs compiler, plugin, language-server, model, and backend tests.
After building, run the browser suites relevant to your change:

```sh
npm run build:proof
npm run test:proof
npm run build:runtime
npm run test:runtime
npm run test:bench
npm run test:counter
npm run test:todo
npm run test:hot
```

`test:bench` runs the [React conformance bench](tests/conformance/README.md): each
case exists in plain React and in JX, and one spec must pass against both.

These checks cover real React behavior, offline interaction, interoperability,
HTTP and routing, the complete examples, and actual Maven-driven hot updates.
Editor verification has separate commands in the [tooling guide](docs/tooling.md).

The [contributor tooling guide](docs/contributor-tooling.md) explains the portable
skill pack, Codex/Claude Code/Cursor integration, reuse in other projects, and
validation of repository instructions.

## Roadmap to general availability

The [GA roadmap](docs/ga-roadmap.md) is a proposed one-year plan for complete
applications, shared Java/API contracts, Spring Boot integration, browser-library
coverage, React capabilities and migration of real products. It separates current
evidence from planned capabilities and includes release gates, a dependency-ordered
backlog and a daily review process. Work proceeds through the
[implementation sequence](docs/ga-tasks.md), one change-sized task at a time.

## License

[MIT](LICENSE). Third-party dependencies and the Maven Wrapper retain their
respective licenses.
