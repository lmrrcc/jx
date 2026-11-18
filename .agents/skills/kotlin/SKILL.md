---
name: kotlin
description: Implement and refactor Kotlin with explicit nullability, value semantics, coroutine ownership, Java interoperability, and JVM boundaries. Use for Kotlin source changes in repositories that permit them.
---

# Kotlin

Read the repository guide, source sets, Kotlin/JVM targets, compiler flags, and
tests. Some repositories keep Kotlin as a frozen reference; this skill does not
authorize changes to frozen paths or a migration to Kotlin.

## Models and boundaries

- Prefer val and immutable API surfaces; data classes and List are only shallowly
  immutable/read-only. Copy mutable caller-owned data when ownership requires it.
- Validate invariants in constructors/factories. Data-class copy must not bypass
  them. Avoid public mutation that creates partially valid objects.
- Handle external nulls explicitly; avoid `!!` and unchecked casts to repair a
  contract mismatch. Normalize Java platform types at the boundary.
- Use sealed types for closed outcomes and exhaustive when expressions. Avoid
  catch-all branches that silently accept new variants in owned types.
- Use value classes with awareness of boxing, generic/JVM erasure, Java callers,
  serialization, ORM, and framework support. Do not assume zero allocation.
- Keep Java-facing APIs deliberate: overloads/default arguments, exception
  declarations where useful, null annotations, and binary compatibility.
- Favor named arguments and clear functions over clever operator DSLs or dense
  scope-function chains. Preserve an established style during local fixes.

## Coroutines and resources

- Define an owning CoroutineScope and its lifetime. Cancel child work when that
  owner ends; avoid GlobalScope and detached fire-and-forget jobs.
- Suspend does not make blocking work nonblocking. Select the appropriate dispatcher
  or adapter for I/O and CPU work; follow the host's execution model.
- Preserve CancellationException. Broad catches or runCatching must not transform
  cancellation into ordinary failure or successful fallback.
- Define timeout, exception propagation, supervision, and backpressure deliberately.
  A SupervisorJob does not excuse unobserved failures or leaked work.
- Protect shared state with an appropriate mutex/actor/atomic model; coroutines
  are not automatically confined to one thread. Avoid locks around callbacks.
- Flow collection has a lifetime. State hot/cold behavior, replay/buffer limits,
  sharing policy, and cleanup. Do not expose mutable state streams to consumers.
- Close resources with use or structured cleanup. Use non-cancellable cleanup
  narrowly when necessary; do not mask cancellation for normal work.

## Framework and verification

- Kotlin classes are final by default. Verify actual Spring/JPA compiler plugins,
  proxy behavior, constructors, and annotation use-site targets.
- Prefer constructor injection and appropriate framework boundaries. Use
  [spring-boot](../spring-boot/SKILL.md) when that framework is involved.
- Test behavior with configured JVM and coroutine test support. Control dispatchers
  and time; verify cancellation and errors without sleeps. See
  [testing](../testing/SKILL.md).
- Run the configured target for the affected source set. Do not invent detekt/ktlint
  tasks, change language targets, or rewrite Kotlin in another language as a side
  effect of a focused change.
