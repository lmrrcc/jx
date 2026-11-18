---
name: code-review
description: Review code or designs for concrete correctness, security, lifecycle, compatibility, and release risks across stacks. Use for requested reviews or deliberate readiness assessments.
---

# Code and design review

Read the repository guide, requested scope, changed contract, and relevant tests.
Load only the topic skills needed by the affected boundaries. Repository and user
requirements take precedence over generic preferences.

## Review from evidence

1. Identify the promised behavior, consumers, and checks that actually exercise it.
   Reproduce a suspected defect when feasible.
2. Trace changed entry points through registration, state transition, external
   effects, result mapping, and cleanup. Inspect callers, not only the edited method.
3. Prioritize incorrect behavior, data loss/leaks, authorization gaps, races,
   resource leaks, compatibility breaks, and misleading success over style.
4. Distinguish a proven defect, an unverified risk, and an optional improvement.
   Cite file/line, trigger, consequence, and a minimal corrective direction.

## Check the changed contract

- Does the advertised capability work through a real consumer and its wiring?
- Are dependency direction and host-app integration boundaries preserved?
- Do types and validation enforce the actual invariants at trusted/untrusted edges?
- Are ownership, threading, ordering, cancellation, and disposal matched by behavior?
- Can stale asynchronous work or shared mutable state cross users/tenants/instances?
- Are errors meaningful and safe, and does failure avoid partial/fake success?
- Are retry/idempotency and resource/payload limits appropriate to the operation?
- Are source, binary/ABI, behavioral, and protocol compatibility considered?
- Do tests prove outcomes through the boundary being claimed, including relevant
  failures? Are browser/database claims backed by the matching runtime/engine?
- Are dependencies, abstractions, generation, and optimization justified by an
  exercised requirement or reproducible measurement?

Report actionable findings ordered by impact with precise locations and the
conditions that cause the problem. Say when no findings were found and identify
meaningful verification gaps. Do not invent findings to fill a quota or demand
unrelated refactors.

For release readiness, assess actual build/consumer/integration evidence and name
remaining blockers. Keyword matches such as TODO, mock, and stub are review leads,
not automatic defects; fixtures and legitimate protocol stubs need context.
A passing compile does not prove release readiness, and a review conclusion does
not grant deployment/publication authorization.
