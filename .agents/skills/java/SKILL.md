---
name: java
description: Implement and refactor Java with explicit types, invariants, error semantics, resource ownership, and concurrency. Use for Java source changes; follow the repository's JDK and build baseline.
---

# Java

Inspect the repository guide, compiler release/toolchain, affected API, and tests.
Use features supported by that baseline; do not upgrade the JDK or enable preview
features just to apply an example from this skill.

## Types and invariants

- Prefer records for value semantics when supported, ordinary classes for identity,
  resources, and mutable lifecycle, and sealed hierarchies for genuinely closed
  variants. Public extensibility needs a deliberate alternative to sealing.
- Validate invariant-bearing objects at construction. Bean Validation annotations
  do not execute on a plain constructor call.
- Records are shallowly immutable. Copy mutable collections/arrays on entry and
  protect them on exit; consider whether their elements also need protection.
- Define null semantics. Return empty collections for no elements and use Optional
  for meaningful absent results; do not apply it mechanically to every parameter
  or field. Follow the repository's nullness annotation/checker policy.
- Use meaningful types for IDs, units, and modes. Booleans are valid binary values;
  use an enum or named operation for ambiguous modes or multiple side effects.
- Make numeric conversion, overflow, charset, locale, and time-zone behavior
  explicit at boundaries. Prefer java.time and an injectable Clock where time
  affects behavior. Use decimal arithmetic when exact decimal values are required.
- Prefer standard Java and explicit code before adding processors or reflection.
  Do not introduce Lombok without an established repository decision; preserve
  existing conventions during focused changes. MapStruct or Immutables needs a
  concrete benefit and explicit compiler processor configuration.

## Errors and resources

- Use specific exceptions or typed outcomes suited to the API. Preserve causes
  and distinguish absence, invalid input, conflict, and infrastructure failure.
  Avoid blanket catches and successful empty defaults after a required failure.
- Keep known schemas typed. Do not hide mismatches with raw types, unchecked casts,
  or Map<String, Object>. Deliberately dynamic boundaries need explicit validation.
- Close owned resources with try-with-resources. Document who closes borrowed
  streams, executors, clients, subscriptions, and sessions.
- Propagate interruption when possible; otherwise restore the interrupt flag and
  terminate meaningfully. An interruption is not a retryable ordinary error.
- Libraries must not terminate the JVM, reset logging, or mutate global application
  configuration. Leave those policies to the host.

## Concurrency

- Define the state owner before sharing mutable values. Prefer immutable messages
  and a clear synchronization or serialized-update model.
- On a compatible JDK, virtual threads can help blocking I/O; they do not remove
  races, CPU limits, database pool limits, admission control, or deadlines.
- Keep blocking/CPU-heavy work off UI/reactive event threads. Avoid holding locks
  across remote operations or callbacks; account for reentrancy.
- Choose executors deliberately. Do not put blocking work on the common pool by
  accident. Close executors the application/library owns.
- Propagate cancellation/deadlines and observe exceptional futures. Late work
  cannot update a disposed/replaced owner. Do not equate a thread with a user
  session or retain requests accidentally in statics/ThreadLocals.

Keep visibility narrow and follow the actual formatter. Document public contracts
with useful Javadoc, including non-obvious lifecycle/threading/error behavior.
Verify with [testing](../testing/SKILL.md); use
[spring-boot](../spring-boot/SKILL.md) for proxy and framework wiring concerns.
