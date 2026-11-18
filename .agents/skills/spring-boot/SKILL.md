---
name: spring-boot
description: Implement Spring Boot applications, adapters, and starters in Java or Kotlin, including configuration, HTTP, transactions, and persistence. Use for Spring-specific behavior and wiring.
---

# Spring Boot

Read the repository guide and actual Boot version, dependency BOM, JVM target,
execution model, and tests. Match that version's APIs and namespace; do not copy
obsolete annotations or upgrade Spring as part of an unrelated fix.

## Wiring and configuration

- Use constructor injection and explicit dependencies. Keep bean graphs acyclic;
  avoid field injection, service locators, and static application contexts.
- Keep Spring, servlet, persistence, and security types outside a deliberately
  framework-independent core. Do not introduce that separation by rewriting an
  established application without a concrete need.
- Prefer immutable, validated @ConfigurationProperties for nontrivial settings.
  Follow the established file format; for a new application, YAML with two-space
  indentation and kebab-case keys is a reasonable default. Externalize secrets
  using the host's environment or secret-management facility.
- Verify that validation actually executes: @Validated/configuration registration,
  @Valid nesting, and Kotlin use-site targets matter. Keep core object invariants
  valid even when constructed outside Spring.
- Use proxy-compatible classes and method visibility where advice is needed.
  Records/final classes cannot receive class-based proxies; Kotlin plugin settings
  matter. Self-invocation does not apply proxy-based transaction/security advice.
- For starters, use version-appropriate auto-configuration registration, conditions,
  user-bean backoff, and configuration metadata. Document activation and overrides;
  do not take over a host application's security or database silently.

## HTTP, transactions, and integrations

- Controllers validate/map input, invoke an application operation, and map results.
  Keep application decisions and transaction coordination out of controllers.
- Use explicit boundary models where trust/serialization differs from domain data.
  Do not serialize ORM entities or expose raw exception details as a shortcut.
- Preserve the established HTTP/error contract; ProblemDetail is suitable when
  supported and intentionally adopted. Document actual statuses and validation
  errors; do not force another project's 422/envelope convention.
- Keep remote clients behind a focused boundary. Choose HTTP interfaces, RestClient,
  WebClient, JDK HttpClient, or Feign according to the actual stack and need.
  Configure deadlines, bounded retries, and error mapping. Retry mutations only
  with proven idempotency; a fabricated fallback is not resilience.
- Blocking JPA does not belong on a reactive/event loop. Suspend methods and
  reactive wrappers do not automatically change blocking persistence semantics.
- Put transactions around operations that need atomicity. Verify interception,
  rollback rules, propagation, and concurrent updates; avoid long remote calls
  inside a database transaction.
- Singleton beans must not retain per-user mutable state. Define component,
  request, session, and application ownership deliberately.

Read [persistence guidance](references/persistence.md) only when persistence is
involved. A starter or UI integration does not by itself justify a database.

## Verification

- Use focused context tests for auto-configuration activation, missing dependencies,
  disabled behavior, invalid configuration, and user overrides.
- Use MVC/JPA slices when sufficient; reserve full-context tests for actual full
  wiring. Select mocking annotations supported by the installed Spring version.
- Verify serialized requests/responses and security failures at the real boundary.
  Secure management endpoints according to host policy. Use
  [testing](../testing/SKILL.md) and [security](../security/SKILL.md) as applicable.
