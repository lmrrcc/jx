# Server resource hint headers

R-31 option-level work, 26 November 2026. The bounded header slice merged through
[PR 68](https://github.com/lmrrcc/jx/pull/68) as `ef7e9d3`. Implementation, focused
differential consumer, full bench and both scoped mutants pass. This does not
complete the broader parity gate or production HTTP qualification.

`ReactServer.StreamOptions.withHeaders` exposes an immutable `ResourceHeaders`
snapshot of React's Link field. `link()` is null when React supplies no hints.
The adapter normalizes the Node renderer's `{Link}` object and the Web renderer's
`Headers` instance without exposing an untyped application map. Other HTTP fields
and status are not part of this resource metadata contract.

```java
var options = ReactServer.StreamOptions.defaults()
        .withMaxHeadersLength(1024)
        .withHeaders(headers -> {
            if (headers.link() != null) responseHeaders.setLink(headers.link());
        })
        .withError(errors::record);
```

The illustrative `responseHeaders` and `errors` are host adapters, not new JX
types. The compiling Java consumer is
[`ResourceHeadersExports`](../../tests/conformance/src/main/java/jx/conformance/ResourceHeadersExports.java).
It renders a real Java component through React, gathers per-request immutable
snapshots, and observes callback order and original thrown Java error identity.

The positive optional limit is measured in UTF-16 code units, as React specifies,
not UTF-8 bytes. Null selects React's default; zero and negative values are
rejected. Overflow hints may remain in the HTML. A limit without a callback has
no effect. Callback null removes it. Builder copies retain both settings.

Initial `renderToReadableStream`, `renderToPipeableStream`, `prerender`, and
`prerenderToNodeStream` accept these options. All four resume APIs reject them
before entering the native runtime: pinned React's resume paths do not forward
`onHeaders` or `maxHeadersLength`. This avoids promising a callback that will
never run. Existing four- and six-argument Java constructors remain, with the new
options absent; the record shape changes before GA, and consumers should rebuild.

The HTTP host owns timing, delivery, 103 support and failures. In pinned React,
throwing from `onHeaders` invokes `onError` as a recoverable renderer error;
successful body completion therefore does not establish successful header
delivery. A host must record and handle its delivery failure explicitly. Header
values reject CR, LF, NUL, DEL and other controls except horizontal tab; use an
HTTP library to transmit them. This is not authorization, cookie or CSP policy.

The differential case covers four initial renderers, duplicate preconnects,
font/image hints, empty headers, a one-unit budget, a partial budget, concurrent
request isolation, original callback failure, and exact React HTML/snapshot/order
comparison. It is a Node server case: hydrate/static/lazy browser-mode rows do not
apply. Eight pair witnesses are registered, each with its own assertions for
preconnect deduplication or font/image preload emission under one of the four
renderers. Registration alone earns no credit: the full run must execute them
successfully on both React and JX. Production HTTP/103 adapter qualification
remains separate work.

Two curated mutants target dropped Link values and ignored explicit budgets.
Both were killed by `server-resource-headers` after a passing unmutated baseline
on the combined server-option candidate `eb536cd`. The exclusive campaign killed
five mutants total, with zero survivors or unusable mutants; this slice receives
credit for its two mutations only. The mutated header helpers and focused
consumer/spec are unchanged; later import-map options elsewhere in
`ReactServer.java` are outside this slice. This is not a fresh full-catalog run.
Scratch: `jx-mutation-11888686568552705847`; log:
`/private/tmp/jx-server-options-mutations.log`. Repeat only in an exclusive slot:

```sh
java tools/MutateBench.java --only server-hint-header-empty,server-hint-budget-ignored --keep
```

## Executed evidence

Final integration over resume PR67 passed all 15 local gate steps in **346 seconds**
on `426cc08`. Both [PR Verify](https://github.com/lmrrcc/jx/actions/runs/45767190940)
and [push Verify](https://github.com/lmrrcc/jx/actions/runs/45766891658) passed on
that exact head before the confirmed squash merge. The final bench passes
**935 checks /193 inapplicable cells /141 cases**: client 139 React/138 JX,
hydrate 104/static 110/lazy 115 per side. Both result tables match and all 62/6328
pair witnesses pass, leaving 6266 open. Maven passes 56 core/7 React/54 compiler/
15 plugin/10 language-server checks, plus the configured Elm/backend/examples;
proof, runtime 16, lazy 4, Flight 28 Node+4 Chrome, counter, Todo and both real hot
checks pass. These counts describe the merged header slice; the earlier local
qualification below is retained as history. Summary:
`/private/tmp/jx-headers-426cc08-gate-summary.txt`.

- Package passes **47 core, 7 React, 52 compiler and 15 Maven-plugin tests**,
  including the compiling Java consumer. The initial sandbox attempt could not
  open loopback sockets in six existing plugin tests; the authorized rerun passes
  all of them. No checks were disabled.
- Focused differential case: **2 passed, 6 mode cells inapplicable**, 11.0 seconds.
  All four renderer variants, two complete simultaneous responses, header budgets,
  empty hints, original Java callback failure and exact native output pass.
- Full regression: **911 passed, 193 skipped, 138 cases**, 3.3 minutes. Client:
  136 React /135 JX; hydrate: 101 each; static: 107 each; lazy: 112 each. This also
  qualifies the separately added `server-resume-nonce` case in the complete run.
- Eight separately marked renderer/resource witnesses and all 54 prior witnesses
  pass in the full run: **62/6328**, with 6266 active pairs still without complete
  evidence. Both result tables were generated from that successful full run.
- Logs: `/private/tmp/jx-server-resource-headers-build.log` and
  `/private/tmp/jx-server-resource-headers-focused.log`, and
  `/private/tmp/jx-server-resource-headers-bench.log`. Package time under
  concurrent validation is not a performance measurement.

Primary references: React's
[Web streaming options](https://react.dev/reference/react-dom/server/renderToReadableStream),
[Node streaming options](https://react.dev/reference/react-dom/server/renderToPipeableStream),
and [Node prerender options](https://react.dev/reference/react-dom/static/prerenderToNodeStream).
The installed React 19.3.1 server sources also establish the recoverable callback
failure and ignored resume-option behavior; no private React fields are used by
the implementation.
