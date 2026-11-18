---
name: react
description: Implement and refactor React components, state, hooks, forms, routing, and data loading. Use in existing or explicitly requested React applications while preserving their build and rendering model.
---

# React

Read the repository guide, package manifest, React version, framework/router,
TypeScript settings, and tests. Follow the existing client/server rendering and
data-loading model; this skill does not authorize adding React to another stack.

## Components and state

- Keep render pure and props immutable. Place state at the narrowest shared owner;
  derive values during render rather than synchronizing redundant state with effects.
- Use stable keys tied to identity for reorderable collections. Do not define
  component types inside render when it unintentionally resets state.
- Prefer composition and explicit props. Introduce global state, context, or a
  state library for a concrete cross-component need, not every local interaction.
- For updates based on previous state, use the appropriate functional update.
  Keep updater functions pure and account for batched/interruptible rendering.
- Follow the Rules of Hooks. Keep dependencies truthful; do not suppress hook lint
  warnings to hide stale closures or a flawed ownership model.
- Effects synchronize with external systems, not compute derived values or replace
  event handlers. Clean up subscriptions/timers and handle remounting/Strict Mode.
- Cancel or ignore stale asynchronous work. A late request must not overwrite a
  newer selection or update an owner that has been disposed.
- Memoization is an optimization, not a correctness guarantee. Profile before
  adding memo/useMemo/useCallback widely or changing component boundaries.

## Browser and server boundaries

- Use the host framework's loader/cache/mutation conventions. Avoid duplicate fetch
  layers and request waterfalls caused by independent effects.
- Server components, server actions, and client components have different trust
  and serialization rules. Keep secrets/server-only modules off the client and
  authorize mutations on the server; a server-action identifier is not permission.
- Keep controlled/uncontrolled form behavior consistent. Preserve focus, selection,
  labels, keyboard operation, composition input, and useful validation feedback.
- Use semantic HTML and accessible interaction patterns. Define loading, empty,
  error, and retry states matching the real operation; avoid silent fake data.
- Escape normal content; dangerouslySetInnerHTML needs an explicit trusted-content
  or sanitization policy. Validate URLs and never rely on hidden/disabled controls
  as an authorization boundary.
- For SSR/hydration, ensure deterministic initial output; avoid browser-only globals
  during server rendering. Do not suppress hydration warnings to conceal a mismatch.
- Keep TypeScript contracts precise when TypeScript is used. Avoid any/casts to
  paper over a server/client mismatch; validate untrusted JSON at a useful boundary.

## Verification

Use the configured lint, typecheck, build, and test commands. Test observable
interactions with role/label queries and realistic events; avoid assertions on
private component state. Use browser tests for actual navigation, focus, rendering,
and hydration behavior. Apply [components](../components/SKILL.md),
[testing](../testing/SKILL.md), or [security](../security/SKILL.md) when relevant.
