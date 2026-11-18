# Agent rule and skill source audit

Inspected on 2026-11-18. The requested “Stack Sensei” project is `stack-sensei/`;
the older `stack-sensei-repo/` checkout was also searched for additional material.
Source projects were read without editing their rules or running their operational
scripts. JX was empty at the start of this setup.

## Inventory

The [source catalog](agent-sources.tsv) records project-relative paths and SHA-256
hashes for 677 guides, skill entrypoints, and topic-rule files (385 distinct file
contents). It includes hidden and ignored instruction directories and archived
JVM/Spring skills. Hashes describe the files at inspection time, not a maintained
dependency on the other repositories.

| Checkout | Catalog entries | AGENTS guides | SKILL.md entrypoints | Flat skill files |
| --- | ---: | ---: | ---: | ---: |
| tetra | 135 | 10 | 15 | 12 |
| stack-sensei | 203 | 5 | 77 | 12 |
| stack-sensei-repo | 114 | 4 | 55 | 0 |
| turboj | 0 | 0 | 0 | 0 |
| medusa | 105 | 4 | 40 | 12 |
| gandalf | 120 | 2 | 30 | 12 |

The broad discovery found 3,576 candidate Markdown/rule files, including supporting
examples, prompts, and detailed rule fragments. The catalog lists all discovered
AGENTS.md/CLAUDE.md/SKILL.md entrypoints, flat skill files, and topic rules up to
four path components; it does not duplicate their thousands of linked fragments.
Relevant source content was read selectively, not every example in those trees.
Git metadata, dependency trees, build outputs, caches, local runtime data, and
conversation/log directories were excluded.

TurboJ had no matching agent/skill/rule entrypoints in that search. Its README.md
and pom.xml supplied concrete Java 25, Maven Wrapper, module, and JUnit evidence;
those ordinary project files are not counted as agent instructions. Tetra's
AGENT_HARNESS.md and `.agent/` policy material were also identified; its harness
README explained computational checks and project-specific frozen paths.

## Reused ideas and portable destinations

Paths below are relative to the source checkout named in the first column. These
are provenance references, not commands or instructions to run in JX.

| Source | Material | Result |
| --- | --- | --- |
| Tetra | AGENTS.md; `.agent/README.md`; `.augment/rules/`; `.claude/rules/` | Focused checks, honest command inventory, dependency boundaries, explicit invariants |
| Stack Sensei | `.codex/skills/kotlin-spring/`, `data-migrations/`, `auth-payments/`, `testing-review/`; `.cursor/skills/` | Kotlin/Spring boundaries, validation, migrations, authorization, regression testing |
| TurboJ | README.md; pom.xml | JX-local Java/Maven baseline; pinned tools and real consumer/testing evidence |
| Medusa | `.cursor/archive/jvm-spring/skills/`; `.codex/skills/rust-authoring/`; `.cursor/skills/` | Java values, constructor injection, JPA, Spring tests, typed Rust authoring |
| Gandalf | AGENTS.md; `.claude/standards/` compact rules; `.claude/skills/` | Portable architecture, Rust ownership/cancellation, security, APIs, testing, evidence-based reviews |

The resulting pack has twelve unprefixed skills: architecture, api-design, java,
kotlin, rust, spring-boot, react, components, testing, security, build-engineering,
and code-review. React and component guidance adapts the frontend/lifecycle ideas
to a reusable form. Java browser runtime guidance addresses JX's stated product
requirement without choosing an unimplemented execution model.

## Conflicts resolved during adaptation

- **Languages and versions:** Kotlin freezes, Rust-only tooling, Java 17/21/25,
  and differing Spring versions are repository policy. Generic skills read that
  policy; JX's own guide sets its initial Java-only defaults.
- **Documentation:** low-value comment avoidance is useful, but blanket one-line
  restrictions do not fit a public framework. Public API documentation must state
  relevant ownership, lifecycle, threading, errors, and compatibility.
- **Validation:** annotations do not enforce invariants on every constructor call.
  Core types validate construction; framework boundary validation must actually run.
- **Framework mechanics:** records/final classes are not universally suitable for
  advised Spring services or JPA entities. Skills require checking proxy/ORM behavior.
- **Mappings:** schema names follow the real naming strategy, not a presumed
  uppercase convention. Null-skipping mappings must not erase explicit clear actions.
- **Dependencies:** Lombok avoidance and explicit code are preferred for new code;
  Immutables, MapStruct, Feign, Vault, and a particular database are not universal
  requirements. JX's no-Lombok policy remains local and explicit.
- **HTTP and data models:** error/status conventions belong to the actual contract.
  Transport DTOs are justified by trust/evolution; neither always-expose-domain nor
  always-duplicate-every-model is a universal rule.
- **Tests and reviews:** narrow meaningful evidence replaces arbitrary test/coverage
  quotas, blanket keyword rejection, forced E2E for every edit, or automatic
  production-ready claims. A review identifies concrete failures and gaps.
- **Source quality:** archived rule packs contain mechanical cross-language
  translations, including Spring concepts in Rust variants. These were not copied
  as valid Rust guidance.
- **Operations:** deployment commands, live endpoints, local credentials, service
  names, wallet/custody policies, and approval flows were not imported.

## Maintenance

Keep skills generic and self-contained within the pack, with relative sibling and
reference links. Keep product facts and command inventories in the consuming
repository's guide. Compatibility links must point at the one canonical copy.
Do not automatically synchronize later source changes: reassess their applicability
and contradictions first. The source catalog is historical evidence, not an active
policy layer or a promise that every source rule was adopted.
