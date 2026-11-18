# JX

JX is a Java application authoring platform for the browser: Java and `.jx` markup,
the actual React runtime, functional components and hooks, and optional Elm-style
Model–View–Update state. Spring Boot provides backend services when needed.

**Status: early implementation.** A Java 25 → TeaVM → React browser proof runs and
has passed a real browser test. The full `.jx` language, application toolkit, Maven
plugin, language server, and installable editor plugins are required milestones,
not capabilities already delivered by this initial proof.

The [v2 specification](docs/JX_Astra_Max_Prompt_v2.txt) defines the complete scope.
The [baseline decision](docs/decisions/0001-java-framework-baseline.md) records the
architecture and distinguishes product requirements from verified behavior.

## Run the browser proof

Requirements: JDK 25, Node.js 20 or later, npm, and Google Chrome for the browser
test. Maven Wrapper downloads Maven 3.9.11. Dependencies are pinned: TeaVM 0.16.0,
React/React DOM 19.3.1, esbuild 0.28.2, and Playwright 1.65.1.

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository package
npm ci
npm run build:proof
npm run test:proof
node proof/react-bridge/serve.js
```

Open http://127.0.0.1:4173/. Each counter click queues three functional increments.
The test verifies independent component state, captured render snapshots, cleanup,
and interaction with networking disabled after the assets load. Tests use port
4273, so the interactive proof can stay open on 4173.

Application behavior in this proof lives in Java. Its JavaScript files are
framework-owned bundling and browser-test fixtures. No artifacts are published to
Maven Central, npm, or editor marketplaces.

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
