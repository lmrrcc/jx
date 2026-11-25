# Server resource hint headers

R-31 option-level work, 25 November 2026. Implementation, focused differential
consumer and full bench pass; mutation and release gates remain pending. This is
not a completed parity gate.

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
They are registered but have not been executed; no mutation credit is claimed.

## Executed evidence

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
