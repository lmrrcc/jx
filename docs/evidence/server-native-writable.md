# Native Writable and HTTP delivery

R-31 qualification, 25 November 2026. The existing
`ReactServer.PipeableStream.pipe(JSObject)` delegates to React's actual Node
Writable adapter. This slice adds differential consumers for that boundary; it
does not add an application server or change stream ownership.

The package passes 56 core, 10 React, 54 compiler and 15 Maven tests, including
TeaVM compilation of the two new `.jx` consumers. The focused Node comparison
passes **6 checks, 18 inapplicable cells**, covering three cases on React and JX.
Integrated source `5643be9` also passes the full bench: **943 checks, 217 skipped,
145 cases** (client 143 reference/142 JX; hydration 104, static HTML 110 and lazy
115 per side). Only the three new case rows change; all prior results and all
62/6328 interaction witnesses remain green. The scoped mutant is killed.
PR [#70](https://github.com/lmrrcc/jx/pull/70) merged as
`8da61ba4ea479c7ad109dfd0350845cf6554fc4f` after the 15-step gate passed in
368 seconds and both [PR Verify](https://github.com/lmrrcc/jx/actions/runs/45775162282)
and [push Verify](https://github.com/lmrrcc/jx/actions/runs/45775023854) passed on
`02d2ddf`. The final integration preserves the counts above and also passes all
ten language-server tests. No new feature-pair credit or production HTTP
qualification is claimed.

The [Java host consumer](../../tests/conformance/src/main/java/jx/conformance/CombinedServerExports.java)
passes the actual supplied Node destination through the public Java API. The
[specification tests](../../tests/conformance/specs/server-native-writable.spec.js)
exercise:

- Five completed Suspense boundaries with a destination whose first write is
  deliberately unacknowledged. Later boundary bytes stay queued until drain;
  all five segments and their multibyte UTF-8 payloads arrive after release.
- Asynchronous failures from the first write, a late write and finalization.
  Node's completion promise rejects with the original native Error, writes stop,
  and a separate request completes with its own data.
- Two real loopback HTTP connections. Closing one client before completion
  rejects server delivery with `ERR_STREAM_PREMATURE_CLOSE`; the host aborts only
  that renderer. Late application data cannot complete the closed response;
  the other client receives its complete body. Every connection/server/request
  has explicit teardown even if an assertion fails.

An initial one-boundary probe expected the number of public write calls to remain
one after rendering completed. Both native React and JX wrote four chunks: React
can finish buffering a boundary after `write(false)`. The replacement test uses
five independently completed boundaries to observe the actual suspension and
resumption of output. A high-water mark is not a hard memory or payload bound;
this evidence does not claim one. Production hosts still need limits on work,
payloads, response time and retained request state.

The host uses `finished(destination, {cleanup: true})` for delivery observation;
`shellReady` and `allReady` describe rendering, not network completion. Premature
response closure triggers the fixture's explicit per-request cancellation policy.
These are distinct responsibilities in the
[Node stream contract](https://nodejs.org/api/stream.html#streamfinishedstream-options)
and [HTTP response lifecycle](https://nodejs.org/api/http.html#class-httpserverresponse).
The synchronous Java `HtmlSink` overload has separate terminal-delivery evidence;
its promise does not describe this borrowed native destination.

## Scoped mutation evidence

The isolated campaign on source `5643be9` (evidence commit `eb536cd`) first
passed the unmodified complete bench, then killed all five selected server-option
mutants, with zero survivors or unusable mutations. Replacing the native
`stream.pipe(writable)` operation with `writable.end()` fails the existing
`combined-server-stream` case and all three new native-destination cases.
The other four mutants exercise resource headers and import maps, documented in
their own evidence pages. This is a five-mutant campaign, not a rerun of the
entire historical catalog. The command exited successfully:

```sh
java tools/MutateBench.java --only server-hint-header-empty,server-hint-budget-ignored,server-import-scopes-dropped,server-import-integrity-dropped,server-native-destination-ended --keep
```

Log: `/private/tmp/jx-server-options-mutations.log`. Retained scratch:
`jx-mutation-11888686568552705847` under the system temporary directory.
