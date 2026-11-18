---
name: api-design
description: Design or evolve typed library, HTTP, and event APIs with explicit errors, compatibility, validation, and documentation. Use when changing a public or cross-component contract.
---

# API contracts

Read the repository guide, existing contract, callers, schemas, and tests. Define
the consumer-visible behavior before filling in the implementation. Preserve
existing conventions unless the requested change requires a deliberate migration.

## Shared contract design

- Use meaningful types and names. Keep required/optional, null/absent, units,
  ranges, ownership, ordering, lifetime, and errors explicit where relevant.
- Prefer named operations and enums for ambiguous modes. Booleans are valid for
  actual binary properties and filters; avoid flag combinations that trigger
  unrelated workflows or silently grant authority.
- Keep known schemas typed; dynamically extensible data needs an explicit schema
  and validation boundary, not unchecked maps/casts throughout the implementation.
- Define failure semantics before the happy path. Preserve causes internally while
  exposing stable, useful, safe errors. Never turn required-operation failure into
  an empty successful result.
- Separate source, binary, behavioral, serialization, and protocol compatibility.
  An overload, enum value, default, optional field, or changed ordering can still
  break consumers. Document intentional breaks, versioning, and migration steps.
- Keep library APIs small. Document non-obvious nullability, ownership, threading,
  cancellation, resource release, exceptions, and examples in the language's API
  documentation. Do not impose a blanket Javadoc/KDoc/rustdoc ban or one-line limit.

## HTTP and event boundaries

- Use verbs/statuses according to actual semantics. Keep reads free of mutations;
  document creation, conflicts, validation, authorization, and asynchronous work.
- Authenticate protected operations and authorize the resource/action separately.
  Derive identity and permissions from verified context, not request fields.
- Use explicit DTO/schema boundaries when domain/persistence types have different
  trust or evolution. Avoid accidental field exposure and mass assignment.
- Bound collection responses, body sizes, nesting, rate, and retention. Define
  pagination/filter/sort behavior and reject invalid values predictably.
- Follow one established structured error contract. Include safe field errors
  where useful; do not leak stack traces, SQL details, or internal object graphs.
- Define idempotency, deduplication, event ordering, retry, and version behavior
  when asynchronous or reconnecting transports need them. A retry is not safe
  merely because the caller timed out.
- Keep OpenAPI or another chosen schema aligned with actual serialization and
  routing. Document relevant statuses, validation, auth, and realistic examples.
  Avoid introducing a schema tool into a library that has no HTTP API.

Verify the contract using a real consumer, codec, HTTP request, or event exchange
as appropriate. Assert outcomes and error shape, not only successful status.
Use [testing](../testing/SKILL.md) and [security](../security/SKILL.md) for the
changed boundaries; do not generate SDKs without a concrete consumer need.
