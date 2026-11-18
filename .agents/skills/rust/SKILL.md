---
name: rust
description: Implement and refactor Rust with explicit ownership, typed errors, async cancellation, safe boundaries, and deliberate public APIs. Use for Rust code in repositories that permit it.
---

# Rust

Read the repository guide, Cargo workspace, edition, minimum supported Rust version,
features, and tests. Follow frozen/reference boundaries; this skill does not
authorize a language migration or new Rust tooling in another stack.

## Ownership, types, and errors

- Model ownership before cloning or adding Arc<Mutex<_>>. Borrow when appropriate;
  use owned values when transfer/lifetime requires them. Clones are valid when
  semantically useful, but inspect large/hot-path copies.
- Accept useful borrowed inputs such as &str and slices; do not fabricate lifetimes,
  leak memory, or add 'static bounds to hide ownership defects.
- Use validated newtypes and enums for invariants. Match owned closed enums
  exhaustively; distinguish public extensibility from internal closed state.
- Use Result/Option and typed errors with preserved sources at public boundaries.
  Application-level context wrappers are useful where callers need diagnostics.
- Avoid panic/unwrap/expect on fallible external input. Document truly infallible
  invariants when an assertion is warranted. Do not discard Results or return fake
  defaults after a required operation fails.
- Use checked conversions and an explicit overflow policy for external lengths,
  counters, and arithmetic. Validate bounds before indexing or allocating.
- Keep public surface small. Derive traits only when semantically valid; redact
  secrets from Debug/Display. Avoid generics without an exercised use.

## Async and resources

- Do not block an async executor with synchronous I/O, sleeps, or long CPU work;
  use the runtime's blocking facilities or async APIs as appropriate.
- Avoid holding synchronous guards across await. Async-aware locking still needs
  bounded critical sections and a clear shared-state owner.
- Treat futures as cancellable at await points. Define partial-effect, retry,
  idempotency, and cleanup behavior when a select branch or task is dropped.
- Observe task completion/errors, bound queues and concurrency, propagate shutdown,
  and avoid detached tasks that outlive their state/resources.
- Use RAII where possible; async shutdown may need an explicit awaited operation.
  Drop alone cannot guarantee delivery or asynchronous cleanup.

## Unsafe and public boundaries

- Prefer safe implementations. Every necessary unsafe block/impl must explain its
  safety invariants; safe APIs must enforce them for every caller input.
- FFI/WASM needs explicit ownership, pointer/length validation, lifetime, panic/
  unwind, encoding, and release conventions. Add focused boundary tests and tools
  such as Miri/sanitizers only when supported and useful.
- Keep Cargo features composable and defaults small. Consider MSRV, API stability,
  and feature combinations when changing dependencies or exposing new types.

Define the contract/errors, add meaningful tests, implement, then run the configured
crate/workspace checks. Use cargo fmt/clippy/test with the actual target and feature
policy. Do not claim cargo audit or other extensions ran when they are not installed.
See [testing](../testing/SKILL.md).
