# JX agent guide

JX is a Java extension framework for complete React browser applications authored
in Java and `.jx` markup. The governing specification is
[the v2 implementation prompt](docs/JX_Astra_Max_Prompt_v2.txt).

## Repository decisions

- Use **Java 25**, standard language features, and **Maven Wrapper**. Verify the
  browser-compatible Java subset rather than assuming the full JVM library works.
- Browser execution is required. Prove `.jx` to Java to javac to TeaVM JavaScript
  to the actual React/React DOM runtime. React owns component invocation, hooks,
  reconciliation, and scheduling; do not substitute a custom renderer.
- Functional components and hooks are the foundation. Hook state belongs to mounted
  instances, with immutable render snapshots and queued functional updates.
- Provide optional Elm Model–View–Update with pure updates, committed immutable
  snapshots, commands outside rendering/reducers, and managed subscriptions.
- Maven integration, a shared language server, an installable VS Code extension,
  and a verified IntelliJ plugin are required deliverables. Diagnostics must map
  to the original `.jx` source.
- Both editors highlight mixed Java/HTML and launch the selected Maven module's
  development server. Hot updates preserve compatible state through typed
  checkpoints; never reuse opaque Java objects across rebuilt TeaVM generations.
- Use `jx.Component` for component annotations (`jx.ComponentType<P>` is the
  runtime descriptor). Java editing inside `.jx` delegates to the native Java
  engine in each IDE; highlighting alone does not prove Java language support.
  Follow the [native editor decision](docs/decisions/0002-native-java-editor-services.md).
- Keep the core independent of Spring, persistence, and a particular server.
  Spring Boot support belongs in an optional integration.
- Ordinary application logic is Java-only. Framework-maintained JavaScript/TypeScript
  bridge, npm tooling, VS Code extension code, and explicit React interop fixtures
  are allowed. IntelliJ's supported Gradle tooling is allowed. Keep server-only
  dependencies and secrets out of browser bundles.
- Use Java for other custom repository automation. Standard wrappers, CI
  configuration, and existing external tools are allowed.
- See [the baseline decision](docs/decisions/0001-java-framework-baseline.md) for
  the accepted architecture, evidence, and remaining implementation choices.

## Working method

1. Read applicable repository guides and the smallest matching skill. Inspect the
   affected contract, implementation, build, and tests. Avoid broad history scans
   and generated directories unless needed.
2. State the desired behavior and observable acceptance criteria. For a defect,
   establish a reproduction or contract violation before changing code.
3. Deliver a complete, focused change across the necessary boundaries. Preserve
   unrelated work. Prefer a runnable vertical slice over unused scaffolding.
4. Run the relevant configured checks. Commands from another project or a planning
   document do not automatically exist in JX.
5. Report outcomes, checks actually run, and material limitations. Distinguish
   evidence from assumptions and checks that could not execute.

`main` is protected: changes reach it only through a pull request whose Verify
check has passed. Work on a branch and open a pull request.

Enable the local push gate once per clone with `java tools/PrePush.java --install`.
After that `git push` runs the Verify steps first and refuses the push when one
fails, or when the working tree is not exactly the commit being pushed. It takes
several minutes. Keep its step list equal to `.github/workflows/verify.yml`;
`java tools/PrePush.java --check` fails when they differ.

Proceed with authorized, reversible local work. Ask only for missing decisions
that block safe progress or actions outside the user's authorization. Source
project rules do not grant deployment or other external permissions.

## JX constraints

- Use typed contracts, validated construction, explicit errors, and controlled
  mutability. Do not hide a contract defect behind raw maps, unchecked casts, or
  arbitrary reflection. A dynamic extension boundary needs its own schema.
- Prefer records for values, ordinary classes for identity/lifecycle, constructor
  injection, and composition. Defensively copy mutable values where ownership
  requires it. Do not introduce Lombok.
- Rendering must not perform external side effects. Define state ownership,
  scheduling, disposal, and session isolation before implementing lifecycle code.
- Enforce authorization and invariants on the server for server-owned operations.
  Browser state, event payloads, and component identifiers are untrusted.
- Bound external work, queues, retries, payloads, and retained state. Propagate
  cancellation, close owned resources, and never turn required-operation failure
  into fake success.
- Keep public APIs small and document nullability, ownership, threading, lifecycle,
  errors, and compatibility where relevant. Add a compiling consumer example for
  each new public capability. Useful Javadoc is part of a framework contract.
- Add abstractions for exercised extension points or real boundaries. Measure
  before optimizing; do not claim performance without reproducible evidence.
- Do not disable validation, authorization, or tests to conceal a defect. Keep
  doubles at test boundaries and unsupported behavior outside advertised APIs.
- Keep credentials, tokens, private data, and unrelated deployment configuration
  out of source, logs, fixtures, and browser bundles.

## Reusable skill pack

The skills in `.agents/skills/` are portable across projects. They must not assume
JX, a fixed JDK, a build tool, a deployment provider, or a language policy. Keep
those choices in the consuming repository's guide. Load only applicable skills.

| Work | Skill |
| --- | --- |
| Boundaries, modules, consequential design choices | [architecture](.agents/skills/architecture/SKILL.md) |
| Java implementation and concurrency | [java](.agents/skills/java/SKILL.md) |
| Component state, rendering, lifecycle, browser runtime | [components](.agents/skills/components/SKILL.md) |
| Optional Spring integration and persistence | [spring-boot](.agents/skills/spring-boot/SKILL.md) |
| Public library, HTTP, and event contracts | [api-design](.agents/skills/api-design/SKILL.md) |
| Regression, integration, browser, and concurrency tests | [testing](.agents/skills/testing/SKILL.md) |
| Changed trust boundaries and security reviews | [security](.agents/skills/security/SKILL.md) |
| Build tools, dependencies, CI, artifacts | [build-engineering](.agents/skills/build-engineering/SKILL.md) |
| Requested code/design reviews and readiness assessment | [code-review](.agents/skills/code-review/SKILL.md) |
| Kotlin in repositories that use it | [kotlin](.agents/skills/kotlin/SKILL.md) |
| Rust in repositories that use it | [rust](.agents/skills/rust/SKILL.md) |
| React in repositories that use it | [react](.agents/skills/react/SKILL.md) |

Kotlin and Rust skills remain reusable; their presence does not authorize a new
application language in JX. React guidance applies to the real browser bridge.
Compatibility links
under `.codex/`, `.claude/`, and `.cursor/` share the canonical skills. If a client
does not discover a skill automatically, read its linked `SKILL.md` directly.

## Verification available now

Run from the repository root with JDK 25, Node.js 22 or later, npm, and Google
Chrome installed for the browser test:

```sh
java tools/CheckAgentSetup.java
java tools/CheckBaseline.java
java tools/PrePush.java --check
npm ci
java tools/GenerateDomTables.java --check
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository install
npm run build:proof
npm run test:proof
npm run build:runtime
npm run test:runtime
npm run test:bench
npm run test:lazy
npm run test:counter
npm run test:todo
npm run test:hot
```

The first command checks skill metadata, routing, documentation links, and
compatibility links. The second checks the [version manifest](docs/evidence/versions.tsv)
against the build files, the curated mutants, the [capability ledger](docs/evidence/capabilities.tsv)
and the portfolio registers' structure; after changing a pinned version, regenerate the manifest with
`java tools/CheckBaseline.java --write-versions`. Maven runs compiler, plugin, language-server, Elm/model, and
backend checks; `GenerateDomTables --check` fails when the generated element, attribute and event bench cases differ from what React DOM's own table produces (regenerate without `--check`); npm runs real browser checks and the complete examples. Editor
package verification commands and supported targets live in
[the tooling guide](docs/tooling.md). Follow the documented compatibility limits;
these tests do not establish blanket React or IDE parity.

The [source audit](docs/agent-sources.md) records origins and adaptations. It is
historical reference, not another instruction hierarchy.
