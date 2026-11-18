---
name: testing
description: Design and run meaningful regression, contract, integration, browser, and concurrency checks across Java, Kotlin, Rust, and React. Use for changed behavior or verification gaps.
---

# Testing

Read the repository guide and actual test configuration. Identify the changed
contract and the smallest observable outcome that proves it; do not assume a
particular runner, wrapper, framework, profile, or service is installed.

| Boundary | Useful evidence |
| --- | --- |
| Values, algorithms, state transitions | Fast tests with real values and invalid/edge inputs |
| Use-case orchestration | Real subject with doubles at collaborator boundaries; meaningful outcomes/effects |
| Public library API | Compiling consumer example exercising documented behavior and errors |
| Serialization/protocol | Round trip, malformed/bounded input, versions, null/absent semantics |
| Framework wiring | Focused slice/context tests; full startup when full wiring is the subject |
| Persistence/external adapter | Matching disposable engine or local protocol server; transaction/error behavior |
| User interaction | Realistic component interactions and browser tests where the browser matters |
| Concurrent lifecycle | Controlled scheduling proving order, cancellation, isolation, and cleanup |

## Implement and verify

- For a bug, prefer a regression that fails on the defect before production edits.
  For new behavior, derive tests from the contract. Documentation/low-impact edits
  do not need tests that merely match wording or mirror implementation.
- Use readable assertions and behavior names. One behavior may need several
  assertions; do not split it artificially or assert every internal call.
- Use real value objects and the real subject under test. Mock external collaborators
  when useful, not private methods or the operation being verified.
- Cover success and the failure that would reveal the defect. Add relevant auth,
  malformed-input, timeout, conflict, replay, limit, and isolation cases when that
  boundary changes; avoid imposing the same checklist on unrelated code.
- Control clocks, seeds, IDs, and temporary resources. Use barriers, latches,
  coroutine test dispatchers, controlled executors, or bounded eventual assertions
  instead of sleeps. Fix races and leaks instead of hiding them with retries.
- Use JUnit Jupiter/AssertJ or the configured JVM tools, Rust's configured test
  ecosystem, and React's configured interaction tools. Do not add a framework
  merely to conform to the examples in this skill.
- Assert persisted state and rollback with the real adapter/engine where database
  semantics matter. Do not silently substitute H2 or mocks for those guarantees.
- Browser claims need browser events and visible outcomes. A screenshot, snapshot,
  direct service call, or HTTP 200 alone does not prove an interactive journey.
  Cover focus, keyboard, and composition input when those behaviors change.
- Check disposal, stale async work, repeated subscriptions, and two-user/two-tab
  isolation when lifecycle/shared state changes.
- Use property tests for algebraic/codec/diff invariants when helpful. Review
  golden fixtures; do not bless changed snapshots without understanding them.
- Routine tests must not mutate live systems or call paid services. Local servers
  and appropriate doubles are valid; report which boundaries were actually real.
- Run the narrowest configured target first. Broaden for shared contracts, wiring,
  failures, or release preparation. Do not repeatedly run unrelated passing suites.
- Never disable a failing check to claim success. Separate environmental blockers
  and pre-existing failures from patch failures. Report commands and actual results.

Coverage helps find missing behavior; it is not proof of correctness. Test the
contract and failure modes that matter, not trivial getters or framework internals.
