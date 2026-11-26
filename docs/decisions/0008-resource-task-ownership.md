# ADR 0008: owned resource tasks

Status: M1-15 and M1-16 have local JVM/browser evidence; publication remains open.

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
and independent-generation checks. Two scoped mutants are killed below;
publication gates remain open. No broader React interaction credit is inferred.

## Scoped mutation evidence

Source `85e9e9d` includes a synchronous-handle assertion before explicit disposal
and a layout-effect witness for the first committed snapshot after a key change.
The isolated scratch commit `106bb14` has the identical source tree. Its unmutated
runtime reactor passes 80 JVM checks and TeaVM in 4.443 seconds; all six resource
Chrome checks pass in 2.8 seconds. The strengthened six `TaskOwnerTest` checks
then pass in a separate 1.211-second Maven run.

| Mutation, applied alone | Direct killing observation |
| --- | --- |
| Remove `cancel()` from `TaskOwner.complete`, immediately before `recipient.accept(...)` | `TaskOwnerTest` has two assertion failures: synchronous start has released zero handles instead of one before returning, and ordinary completion publishes two results instead of one. Compilation succeeds; there are no test errors. |
| Replace the last return in `ResourceHook.use` with `return state.value().snapshot();` | Runtime package and all 80 JVM checks still pass. The `resource keys own` Chrome test fails because the layout-effect history contains `C:ready:B`, exposing the previous key's value in the new key's first commit. |

Both mutations were run exclusively, one at a time, with unchanged test sources.
Result: **2 killed, 0 survived, 0 unusable**. Restore the changed production line
between runs. After restoration the scratch tree is clean, runtime package again
passes 80 JVM checks and TeaVM in 4.515 seconds, and all six resource Chrome checks
pass in 2.8 seconds. Reproduction commands, from an isolated source checkout:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl tests/runtime-browser -am package
npm run build:runtime
npm run test:runtime -- tests/runtime-browser/tests/resource.spec.js --workers=1
# First mutant: run the six JVM owner checks after applying only that mutation.
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl jx-core -Dtest=TaskOwnerTest test
# Restore it, apply only the second mutation, rebuild the runtime and its bundle,
# then repeat the resource browser command above. Restore and rerun afterwards.
```

These are scoped lifecycle/key-isolation checks, not a fresh run of the curated
React conformance mutation catalog and not a claim of complete mutation coverage.

## Integration with the class journal, 26 November

Publication assembly `f1cb11a`, on journal candidate `4c02583` and the merged
Cancellation PR73, preserves the resource capability and consumer files from
`4324d62`. Its selected package passes **174 JVM checks** (80 core, 11 React,
eight Elm, 60 compiler and 15 Maven-plugin tests) and actual TeaVM compilation
in 14.637 seconds. The complete integrated runtime passes **45/45 Chrome checks
in 10.3 seconds**: the six resource checks plus all 39 journal/previous runtime
checks. The browser entry retains compiled-runtime attestation while exposing
the independent native Activity probe. No new mutation or conformance-pair credit
is claimed from this integration; the publication gate and Verify remain pending.

## Router integration (M1-16)

The compiling `RouteResourceApplication` composes `useSyncExternalStore(router)`
with `useResource(location, loader)`. The router's immutable, stable location is
the request identity, avoiding a second data/state owner or a new router API.
Unchanged locations do not reload; path/query/history changes replace the task.
Applications with independent authentication, tenant or retry inputs must include
them in a stable key as well. Hash changes also replace this complete-location
key. A future cache may choose a narrower explicit key; this proof does not.

Two Chrome checks pass in 1.6 seconds, exercising a real deep link, decoded path
parameters, query replacement, back/forward, obsolete replies, typed failures,
not-found, unmount/remount and another still-subscribed router observer. The
external data task is deliberately controlled; real transport abort is covered by
M1-15. No server authorization or SSR qualification is inferred. After making
fixture reporting compatible with TeaVM's JSBody parser, the selected reactor
passes 80 JVM checks (62 core, 10 React, 8 Elm) and TeaVM in 3.882 seconds, without
JSBody warnings. The complete runtime suite passes **24/24 in 5.9 seconds**.

The earlier roadmap reference to ADR 0003 was stale: ADR 0003 already covers
state-preserving development, so this decision is ADR 0008.
