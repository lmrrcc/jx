# 0003: Preserve browser state across Java development builds

Status: accepted, 2026-11-18.

The Maven development command must apply Java and `.jx` changes without losing
compatible component state. Both editor plugins launch this same command for the
active Maven module. Production builds do not contain the development client.

Rebuilding TeaVM creates new Java class identities. Passing an old Java record,
collection, lambda, or service into new code would violate Java type and lifecycle
assumptions. The bridge therefore captures committed hook values and registered
Elm stores into portable checkpoints, disposes the old roots/resources, and
reconstructs those values using the new generation's codecs before mounting.
React still owns rendering and hooks. This is checkpoint-based hot replacement;
it is not an implementation of React Fast Refresh's internal transform protocol.

Strings and primitive wrappers have built-in codecs. Records, collections, and
application stores use explicit `StateCodec<T>` contracts. A compiler-generated
signature identifies compatible hook structure. A changed signature or schema
resets affected state; unresolved external custom hooks conservatively disable
restoration for their component. Keys and component paths identify instances.
Effects and refs restart, while committed state, controlled text, and compatible
store models survive. Restored stores do not replay initialization commands.

The Todo example demonstrates a record/list codec. If a build interrupts pending
HTTP work, its restored model asks the developer to reload the list; it never
replays a mutation whose server outcome may be unknown.

The development server publishes complete successful asset snapshots, retaining
the previous output when compilation fails. The client applies a new generation
only after its script loads. Failure before application leaves the old UI live;
failure after teardown retains the checkpoint for a subsequent corrected build.
Focus, text selection, scroll, and browser history are preserved where the new
markup still provides a matching element. CSS assets refresh after application.

Development applications share a single React/React DOM vendor runtime. New
Java generations use removable classic-script bundles rather than permanent
native ES module cache entries. Old roots, store subscriptions, commands, and
script references are released. Application effects must still honor their
normal cleanup contracts. A changed dependency or vendor runtime requires
restarting development and loading the page again.

Evidence belongs in compiler signature tests, runtime tests spanning independent
Java generations, and `npm run test:hot`, which edits isolated copies of real
`.jx` sources and waits for actual Maven builds. The latter verifies new code and
CSS, retained counters/forms/Elm models, compile failure and recovery, signature
resets, and absence of document navigation during updates.
