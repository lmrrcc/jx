# JX

JX is a Java application authoring platform for the browser: Java and `.jx` markup,
the actual React runtime, functional components and hooks, and optional Elm-style
Model–View–Update state. Spring Boot provides backend services when needed.

**Status: working prototype.** The repository implements a `.jx` compiler,
Maven generation and browser builds, a typed React bridge, hooks, Elm-style state,
browser HTTP and routing, and a shared language server. Counter and todo examples
exercise the complete Java → TeaVM → React path. See the documented compatibility
limits before treating this as a production framework.

The [v2 specification](docs/JX_Astra_Max_Prompt_v2.txt) defines the complete scope.
The [baseline decision](docs/decisions/0001-java-framework-baseline.md) records the
architecture and distinguishes product requirements from verified behavior.

## Build and run

Requirements: JDK 25, Node.js 22 or later, npm, and Google Chrome for browser
tests. Maven Wrapper downloads Maven 3.9.11. Dependencies are pinned: TeaVM 0.16.0,
React/React DOM 19.3.1, esbuild 0.28.2, and Playwright 1.65.1.

```sh
npm ci
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository install
java -jar examples/todo-api/target/todo-api-0.1.0-SNAPSHOT.jar
```

Open http://127.0.0.1:8181/todos. Run the server from the repository root so it can
serve `examples/todo-app/target/site`. The Java/.jx app includes a controlled form,
validation, route parameters and filters, browser history, context, a custom hook,
an Elm store, cancellable HTTP, loading/error states, and a Spring Boot API. H2
persists tasks under `.local/`; Flyway owns schema changes. This is a single-workspace
demo bound to loopback, without accounts or multi-user authorization.

For the small counter and its state-preserving development workflow:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -f examples/counter-app/pom.xml dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev -Djx.port=4180
```

Open http://127.0.0.1:4180. Saving Java, `.jx`, or web assets rebuilds the application;
successful builds preserve compatible component state while applying the new code.
Scalar hooks are automatic; complex Java models use typed codecs. Effects restart,
and incompatible hook changes reset affected state. See [development details](docs/maven.md).

## Verification and tooling

After the Maven build:

```sh
java tools/CheckAgentSetup.java
npm run build:proof
npm run test:proof
npm run build:runtime
npm run test:runtime
npm run test:counter
npm run test:todo
npm run test:hot
```

Maven runs compiler, plugin, language-server, Elm-model, and backend tests. Browser
tests exercise actual React semantics, offline interaction, both interoperability
directions, HTTP cancellation, history, and the todo application. The initial
handwritten Java proof remains under `proof/react-bridge` as a small diagnostic
fixture; `node proof/react-bridge/serve.js` serves it on port 4173.

- [Language and source mappings](docs/language.md)
- [Maven builds and development workflow](docs/maven.md)
- [Runtime, hooks, Elm, and React compatibility](docs/runtime.md)
- [Browser services and routing](docs/browser.md)
- [Language-server behavior and limits](docs/language-server.md)
- [VS Code and IntelliJ packages, installation, and verification](docs/tooling.md)
- [Implementation status and follow-up work](docs/status.md)

Application behavior lives in Java and `.jx`. JavaScript/TypeScript files are
framework adapters, editor implementation, and explicit browser/interop fixtures.
Artifacts use local snapshot versions; none is published to Maven Central, npm,
or editor marketplaces.

## Repository policy and portable skills

[AGENTS.md](AGENTS.md) contains JX-specific decisions. The twelve skills in
[.agents/skills](.agents/skills) are reusable across Java, Kotlin, Rust, Spring Boot,
and React projects; none has a project-name prefix.

| Category | Skills |
| --- | --- |
| Languages/frameworks | java, kotlin, rust, spring-boot, react |
| Design | architecture, api-design, components |
| Delivery and quality | testing, security, build-engineering, code-review |

Each skill follows the consuming repository's versions, build tools, architecture,
language restrictions, and operational permissions. Its presence does not change
that repository's stack. JX remains Java-only for application authoring.

- Codex: root guide plus canonical skills; `.codex/skills` is a compatibility link.
- Claude Code: [CLAUDE.md](CLAUDE.md) imports the root guide; `.claude/skills` links
  to the same pack.
- Cursor: [the JX rule](.cursor/rules/jx.mdc) points to the root guide;
  `.cursor/skills` links to the same pack.

Skill discovery differs between clients. The guide also links each SKILL.md for
direct reading. No global agent settings or permission bypasses are installed.

Example tasks: “Use `$java` and `$testing` to fix this state transition,” “Use
`$spring-boot` to add an optional integration,” or “Use `$code-review` to assess
this API change.”

## Reuse in another project

Copy the canonical `.agents/skills/` tree into the destination, preserving each
skill's references and sibling links. Inspect an existing pack before merging;
do not overwrite its custom skills blindly. Keep one canonical copy and recreate
relative compatibility links only for the clients the project uses.

Add task routing to that project's own AGENTS.md. Keep its JDK/compiler targets,
build commands, language policy, frozen paths, deployment rules, and product design
there. Do not copy JX's guide as a universal policy. The skills do not reference
this checkout or require the sibling source repositories.

## Validate

From the repository root with the Java 25 baseline installed:

```sh
java tools/CheckAgentSetup.java
```

This dependency-free checker validates skill metadata, routing, internal file
links, and compatibility links. It checks instruction-pack structure, not framework
correctness, architecture compliance, or application tests.

The [source audit](docs/agent-sources.md) records what was found in Tetra, Stack Sensei,
TurboJ, Medusa, and Gandalf and how their rules were adapted.

## License

JX is available under the [MIT License](LICENSE). Third-party dependencies and
the Maven Wrapper retain their respective licenses.
