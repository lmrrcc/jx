---
name: security
description: Implement or review changed trust boundaries in HTTP, UI events, rendering, sessions, persistence, uploads, and external integration. Use for security-relevant changes or requested security review.
---

# Trust boundaries

Read the repository's security and data policies. Trace untrusted input to a
protected operation or resource. Apply relevant controls below; do not expand an
ordinary local edit into an unrelated audit or invent new approval requirements.

## Identity and authorization

- Authenticate protected operations and authorize the action/resource separately.
  Test an authenticated user addressing another user's or tenant's resource.
- Derive identity, roles, ownership, and entitlements from verified context.
  Client state, hidden fields, disabled controls, and object IDs are untrusted.
- Use established authentication/crypto libraries. For JWT/OAuth, validate the
  applicable signature, issuer, audience, expiry, state, and redirect requirements.
  Do not implement a new authentication subsystem for unrelated work.
- For cookie-authenticated requests, design CSRF protection and appropriate secure
  cookie settings. For WebSockets/events, validate origin, session binding, expiry,
  message limits/rate, and reconnect/replay behavior.
- Keep mutable request/session state out of application singletons and accidental
  thread-local retention. Recheck authorization where long-lived sessions need it.

## Input, rendering, and external systems

- Validate typed external data and bound sizes, nesting, counts, allocations,
  uploads, queues, and retained resources. Never invoke arbitrary methods/classes
  named by untrusted input or enable unsafe polymorphic/native deserialization.
- Escape text/attributes in their output context and validate URL schemes. Raw HTML
  needs a deliberate trusted-content/sanitization policy; JSON escaping is not HTML
  or script-context escaping.
- Keep credentials, personal data, and internal models out of client bundles,
  generated assets, source maps, logs, and serialized state as applicable.
- Parameterize database queries. Avoid mass assignment of privileged fields and
  preserve tenant/resource scope across persistence and cache boundaries.
- Validate filesystem paths against an owned root, considering symlinks and races
  where relevant. Use argument arrays for processes rather than shell interpolation.
- For user-controlled URLs, address SSRF, redirects, and internal-address access.
  Bound outbound work and preserve TLS verification.
- Configure CSP, CORS, and browser headers for the actual runtime and host policy.
  Do not weaken them broadly just to make a broken integration work.

## Failure and evidence

- Preserve diagnostic causes internally and return safe stable errors. Log useful
  context with an opaque correlation ID; avoid tokens, full session IDs, personal
  data, and raw payloads by default.
- Define idempotency and duplicate/stale/out-of-order handling for retried mutations
  and events. Timeout alone does not prove the remote operation failed.
- Test denial and non-mutation as well as success: ownership bypass, malformed
  input, XSS, expired sessions, and limits where those boundaries change.
- Use configured dependency/secret checks and assess actual findings. A clean scan
  or green suite does not prove the absence of vulnerabilities.

Prepare concrete fixes within the requested scope. Changes to live access controls,
credentials, publication, or deployment require authorization for that action;
respect authorization already given rather than repeatedly asking for it.
