---
name: architecture
description: Design module boundaries, dependency direction, extension points, and consequential architecture changes in Java, Kotlin, Rust, and web projects. Use for structural or cross-module contract decisions.
---

# Architecture

Read the consuming repository's guide and build/module map. Its product scope,
language, compatibility, and runtime decisions take precedence over examples in
this reusable skill.

## Work from the consumer

1. Describe the behavior and a small consumer example. Distinguish public API,
   internal implementation, and optional integrations.
2. Trace entry point, state transition, external effects, response, and cleanup.
   Identify state owners, trust boundaries, and relevant failure modes.
3. Choose the smallest complete implementation. Record an architecture decision
   for consequential execution, dependency, persistence, or compatibility choices;
   ordinary local fixes do not need a design document.
4. Implement and verify the path, including registration/wiring and failure.
   Files that compile but cannot be reached do not prove integration.

## Dependency and state boundaries

- Keep domain invariants independent of HTTP, ORM, UI, and deployment concerns.
  Adapters depend on the contracts they implement; avoid cycles and shortcuts
  from core logic into concrete infrastructure.
- Separate pure computation/state transitions from I/O, clock, randomness, and
  global configuration. Inject external capabilities at a useful boundary.
- Use a port/interface/trait for a real boundary or exercised extension point.
  Do not force an interface, service, repository, and DTO around every operation.
- A public library extension point may have one implementation when a consumer
  example proves its purpose. Avoid uncalled hooks for hypothetical variants.
- Prefer packages/modules within the established build until dependency isolation,
  ownership, publishing, or real consumers justify another artifact or service.
- Use immutable values and meaningful operations for invariants; define ownership
  and synchronization where mutable state is necessary. Do not force persistence
  entities or resource-owning components into value-object shapes.
- Separate transport/persistence models when trust, lifecycle, or evolution differs
  from the core model. Map deliberately; no accidental exposure of internal data.
- Constructor injection and explicit wiring make dependencies testable. Do not
  use service locators or global state to bypass a dependency cycle.

## Completing a design

- Specify ownership, lifetime, thread/executor affinity, ordering, cancellation,
  limits, and error behavior where they matter.
- Prefer composition and small contracts over deep inheritance, string-based
  dispatch, or arbitrary reflection. Generation needs deterministic inputs,
  diagnostics, and a clear source/output boundary.
- Consider source, binary, behavioral, and wire compatibility separately. Preserve
  contracts during refactoring; document intentional changes and migration paths.
- Choose verification at the changed boundary, including consumer examples for
  library APIs. Use [api-design](../api-design/SKILL.md) for contract details and
  [testing](../testing/SKILL.md) for meaningful evidence.

Deliver the decision and its tradeoffs, the implemented slice, and verified
acceptance criteria. Do not impose a microservice, event-sourcing, DDD, or frontend
framework migration merely because the skill discusses boundaries.
