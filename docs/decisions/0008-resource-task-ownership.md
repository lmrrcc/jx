# ADR 0008: owned resource tasks

Status: M1-15 has local JVM/browser evidence; publication and M1-16 remain open.

## Contract

`Task<T, E>` is a cold description: construction starts no work, each start owns
one cancellation handle, and completion produces either `Task.Success<T,E>` or
`Task.Failure<T,E>`. The source chooses a typed error schema. HTTP preserves its
existing `Http.Failure<T>` with kind, status and body, so a server/domain rejection,
network failure, timeout and decoding failure do not become one generic string.
Unexpected exceptions from task construction/start propagate; they are not
presented as a declared failure or a successful empty value.

`Hooks.useResource(key, loader)` creates a task only in a committed passive effect.
Its immutable snapshot is `Resource.Loading`, `Resource.Ready` or
`Resource.Failed`. An invocation owns at most one active operation. The owner
invalidates completion before cancellation, drops duplicate/stale callbacks and
releases the handle once, including when completion happens before start returns.
Cancelled owners release their completion closure rather than retaining a state
setter until a non-cooperative source eventually responds.

The key is the complete request identity, compared by the same scalar/identity
rules as other hook dependencies. Include path, query, authentication/tenant
generation and an explicit retry generation where relevant. A new inline loader
lambda does not restart an unchanged key. This makes ordinary Java authoring safe
without requiring `useCallback` around every request. Changing only loader logic
without changing the key does not reload an already mounted request.

On a key change, the rendered snapshot is loading even before the old passive
effect cleans up. An old completion can never be displayed as data for the new
key. Cleanup runs on replacement, unmount and Activity effect disconnection.
Reconnection starts fresh work. Strict Mode can start/cancel/start the same task;
the source must tolerate that lifecycle. There is no promise that cancellation
reverses a mutation already committed on a remote server. Resource effects are for
repeatable reads; user-triggered writes belong in events or explicit commands.
React documents the client-only lifecycle and development setup/cleanup replay in
[`useEffect`](https://react.dev/reference/react/useEffect). The internal memoized
request token is not a permanent cache guarantee: if React discards it, the hook
safely reloads rather than showing another key's result, consistent with
[`useMemo`](https://react.dev/reference/react/useMemo).

All lifecycle operations run on the owner's browser/event-loop thread; no
scheduler, thread-safe sharing, global cache or implicit retry is introduced.
Resource state has one React hook owner. Request closures, opaque values and
pending tasks do not cross TeaVM generations; a new generation starts a fresh
resource effect. This is separate from typed application-model checkpoints.

## Suspense and server rendering

This effect-owned API does not suspend, start requests during rendering or fetch
on the server. `Hooks.use(Promise)` remains the explicit React Suspense API, with
a promise owned outside the potentially abandoned render. A future shared cache
or server data loader must specify session identity, eviction and invalidation
before connecting those two execution models. Returning a loading snapshot does
not claim SSR data-fetching or React Server Component parity.

## Compiling consumer and verification

[`ResourceApplication`](../../tests/runtime-browser/src/main/java/jx/fixture/ResourceApplication.java)
uses the public hook with controlled Java tasks and real browser HTTP. The intended
checks cover independent instances, inline lambdas, unchanged-key parent pulses,
Strict Mode, key changes, late and duplicate completion, typed failure, Activity
cleanup/reconnect, unmount and a suspended render abandoned before commit.
A real held HTTP response additionally proves that cleanup aborts transport;
in-memory callback tests alone do not establish this.

The selected runtime/compiler reactor passes 135 JVM checks (62 core, 10 React,
8 Elm, 55 compiler) and actual TeaVM compilation. The first four browser checks
produce three passes and one failed expectation: Activity reconnection in Strict
Mode starts the task three times cumulatively for key B, rather than the expected
two. An independent native React effect reproduces the same setup/cleanup count.
The corrected test compares those counts and proves that completion releases the
last active task as well. No production workaround changes React's replay.

Two additional browser checks cover synchronous completion under Strict Mode and
unexpected loader/start exceptions caught by an actual React boundary, including
attempted completion after failure. Final selected reactor: 135 JVM checks and
TeaVM in 10.971 seconds; log `/private/tmp/jx-resource-lifecycle-final-build.log`.
All six new Chrome checks pass together in 2.8 seconds. The complete runtime suite
then passes **22/22 in 4.3 seconds**, including the prior HTTP, hook, MVU, interop
and independent-generation checks. Mutation coverage and publication gates remain
open; no broader React interaction credit is inferred.

The G0 router loader will consume the same hook/task/cancellation contract using
the router's immutable location. That integration and its navigation/late-response
proof remain M1-16 work. The earlier roadmap reference to ADR 0003 was stale:
ADR 0003 already covers state-preserving development, so this decision is ADR 0008.
