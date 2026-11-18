# Contributor tooling and reusable skills

[AGENTS.md](../AGENTS.md) is the repository guide for JX. It owns the project's
Java baseline, architecture, language policy, build commands, and operational
boundaries. The engineering skills in [.agents/skills](../.agents/skills) supply
reusable guidance for focused tasks. Read the guide and the smallest matching
skill before changing a contract, implementation, or build.

## The portable skill pack

The twelve skills keep their plain names, without a project prefix. They can be
used across Java, Kotlin, Rust, Spring Boot, and React projects:

| Work | Skills |
| --- | --- |
| Languages and frameworks | [java](../.agents/skills/java/SKILL.md), [kotlin](../.agents/skills/kotlin/SKILL.md), [rust](../.agents/skills/rust/SKILL.md), [spring-boot](../.agents/skills/spring-boot/SKILL.md), [react](../.agents/skills/react/SKILL.md) |
| Architecture and contracts | [architecture](../.agents/skills/architecture/SKILL.md), [api-design](../.agents/skills/api-design/SKILL.md), [components](../.agents/skills/components/SKILL.md) |
| Delivery and quality | [testing](../.agents/skills/testing/SKILL.md), [security](../.agents/skills/security/SKILL.md), [build-engineering](../.agents/skills/build-engineering/SKILL.md), [code-review](../.agents/skills/code-review/SKILL.md) |

Each skill follows the consuming repository's versions, build tools, architecture,
language restrictions, and permissions. Its presence does not select that
repository's stack. JX application authoring remains Java and `.jx`; Kotlin and
Rust guidance is included for reuse elsewhere.

Example tasks include “Use `$java` and `$testing` to fix this state transition,”
“Use `$spring-boot` for the optional backend integration,” and “Use `$code-review`
to assess this API change.” The guide links every `SKILL.md` directly when a
client does not discover it automatically.

## Agent integrations

Keep one canonical skill tree. The repository's client adapters point to it:

| Client | Repository integration |
| --- | --- |
| Codex | Root guide and canonical skills; `.codex/skills` is a compatibility link. |
| Claude Code | [CLAUDE.md](../CLAUDE.md) imports the root guide; `.claude/skills` links to the same pack. |
| Cursor | [The repository rule](../.cursor/rules/jx.mdc) points to the root guide; `.cursor/skills` links to the same pack. |

Discovery varies between clients. These files install no global settings and
grant no permission bypasses. Skills also do not authorize deployment,
publication, or changes to unrelated repositories.

## Reuse in another repository

1. Inspect the destination's existing guide and skills before merging. Preserve
   local customizations.
2. Copy the canonical `.agents/skills/` tree, retaining its supporting references
   and sibling links.
3. Keep one canonical copy and recreate relative compatibility links for the
   clients that repository actually uses.
4. Add task routing to the destination's own `AGENTS.md`. Keep its compiler
   versions, build commands, permitted languages, deployment rules, and product
   architecture in that guide.

Do not copy JX's project decisions as universal policy. The reusable skills do
not require this checkout, a particular build tool, or the source repositories
from which earlier guidance was adapted.

## Validate repository instructions

From the JX repository root with JDK 25:

```sh
java tools/CheckAgentSetup.java
```

This dependency-free checker validates skill metadata, task routing, local
documentation links, and the three compatibility links. It checks instruction
structure; it does not establish framework correctness, architectural compliance,
or application behavior.

Run the relevant build and browser commands in [the README](../README.md#contributing)
for application changes, and the [tooling guide](tooling.md) for editor changes.
The initial handwritten Java bridge proof remains under
[proof/react-bridge](../proof/react-bridge) as a diagnostic fixture;
`node proof/react-bridge/serve.js` serves its built assets on port 4173.

The [source audit](agent-sources.md) records what was found in Tetra, Stack Sensei,
TurboJ, Medusa, and Gandalf and how their rules were adapted. It is historical
reference, not an additional instruction hierarchy.
