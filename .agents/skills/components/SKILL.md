---
name: components
description: Design component identity, state ownership, rendering, events, lifecycle, accessibility, and browser-runtime boundaries. Use for component frameworks or substantial UI lifecycle changes across stacks.
---

# Component systems

Read the repository's component/runtime conventions and consumer API. Apply this
skill to lifecycle and state semantics; do not redesign an existing UI framework
or select a new rendering stack for an ordinary component edit.

## Contract and ownership

- Demonstrate the consumer API in a working example. Define typed inputs, events,
  state transitions, and error behavior in the host language.
- Distinguish identity from list position and value equality. Reordering children
  must preserve the intended state and dispose removed children.
- Separate inputs from owned state. Choose an immutable-transition, reactive, or
  other explicit model; do not accidentally share mutable state between instances.
- Rendering computes a view from state/inputs. Move I/O and external effects into
  explicit event/effect boundaries so repeated rendering is safe.
- Specify mount/update/failure/disposal ordering and nested ownership. Register
  cleanup with the owner; remove listeners, subscriptions, and pending work on exit.
- Define scheduler/thread affinity, batching, reentrancy, and stale completions.
  An older asynchronous result cannot overwrite newer state accidentally.
- Distinguish component, tab, request, session, and application lifetimes. Server
  singleton state must not leak one user's data to another.
- Bound queues, retained trees, subscriptions, and idle sessions. Define expiration
  and disconnect behavior; implement reconnect/state recovery only when required.

## Interaction and rendering

- Use semantic elements, form labels, keyboard support, and predictable focus.
  Preserve selection and composition input during reconciliation and navigation.
- Model loading, empty, validation, and failure states according to the actual
  operation. Do not convert a failed operation into apparently successful UI state.
- Escape content by output context and validate URL schemes. Trusted/raw HTML needs
  an explicitly named API and trust policy; a generic string must remain text.
- Authorize server-owned events using the current identity and resource scope.
  Component IDs and client-side checks are not proof of permission.
- For a wire protocol, define type/schema/version, limits, ordering, duplicates,
  reconnect, and retry behavior. Avoid arbitrary method-name reflection from input.
- If SSR/hydration exists, test stable identity and matching initial output. If
  code compiles to a browser target, test the supported language/library subset,
  diagnostics, and interop. Do not promise a mode that has no implementation.

For a Java-authored browser framework, read
[Java runtime choices](references/java-runtime.md) before selecting execution or
compilation technology. For an existing React app, use [react](../react/SKILL.md).
Verify state/lifecycle and real browser outcomes with [testing](../testing/SKILL.md),
including isolation, disposal, and late completion cases when those change.
