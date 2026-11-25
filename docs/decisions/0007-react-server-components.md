# 0007 — React Server Components runtime and transport

25 November 2026. Implementation direction accepted for the R-30 feasibility
proof; no RSC capability is qualified by this decision alone.

## Consumer and boundary

A Java-authored server component must execute in a server-only compiled module,
produce the actual React Flight protocol, and compose an interactive Java-authored
client component loaded by React's client decoder. Server code and server-only
dependencies must be absent from browser artifacts and source maps. Ordinary
application logic remains Java and `.jx`.

The first proof follows the existing Node-hosted TeaVM server execution model.
It does not make JVM-only APIs available in TeaVM, replace Spring services, or
claim Spring authentication integration. JVM backend operations remain behind
explicit contracts. React owns server component invocation and the Flight
protocol; JX does not invent a lookalike renderer or wire format.

## Runtime and build choice

Use the official `react-server-dom-webpack` transport at **19.3.1**, matching
the pinned React/React DOM versions. Its browser decoder and webpack client
reference plugin provide the real module-loading boundary. The proof pins
webpack **5.108.4** and webpack-cli **7.3.1**; the normal browser build remains
unchanged. The registry does not publish `react-server-dom-esm@19.3.1`.
The official Flight plugin fails to load with webpack 5.113.0 because its
`webpack/lib/Template` dependency moved. Pin the tested compatible version rather
than intercepting private module loading or modifying the vendor package.

The server process resolves React with the `react-server` condition. Client
bundles resolve the ordinary browser runtime. Do not bundle a client React copy
into the server or share one React dispatcher across both conditions. Separately
compiled Java runtimes exchange explicit serialized values and registered native
component references, never Java objects or closures.

The official webpack plugin produces the client manifest. Server code uses an
explicit build-owned reference registry; incoming request data must never select
an arbitrary module path, class, method or function. Validate manifest shape and
bound module counts, identifiers, props payloads and output sizes at the adapter.
The first fixture exposes read-only loopback rendering; it has no remote function
invocation endpoint or implicit access to credentials.

React's documentation distinguishes stable application RSC APIs from framework
and bundler integration APIs that can change between minor releases. Version
alignment and protocol round-trip tests are therefore release gates. Sources:
[Server Components](https://react.dev/reference/rsc/server-components),
[Server Functions](https://react.dev/reference/rsc/server-functions),
[cache](https://react.dev/reference/react/cache),
[cacheSignal](https://react.dev/reference/react/cacheSignal).

## Deliverable slices

| Slice | Observable acceptance | Status |
| --- | --- | --- |
| R-30-01 | Real Flight output from a Java server module; browser decodes and renders it through actual React; gated completion, error and abort are observable; server artifacts stay absent from the client | Implemented; local proof passes, Verify pending |
| R-30-02 | Typed client-component references resolve through generated manifests; a Java client counter remains interactive; malformed payloads and unknown references fail explicitly | Planned |
| R-30-03 | Request-scoped `cache` and `cacheSignal` behavior with explicit Java value mapping; concurrent requests, cached failure, cancellation and cleanup | Planned |
| R-30-04 | Java authoring equivalents of client/server directives, build separation and server-function references with a typed allowlist and request authorization | Planned |
| R-30-05 | Protocol/version-skew checks, error redaction, bounded transport, disposal and R-29 cache-refresh integration; qualification and updated parity ledger | Planned |

Client rendering, HTML SSR/hydration and Flight are separate execution modes.
Passing HTML SSR tests does not qualify RSC. Likewise, client React's pass-through
`cache` implementation is not server-cache evidence.

## Known prerequisites and limits

The first Node smoke reproduced an eager client error-boundary initialization in
`React`: server-conditioned React does not export the required client class API.
Lazy initialization fixes that failure. `ReactServerComponents.element(View)`
provides the server hook bootstrap without rendering the element itself. Seven
Node checks and four Chrome checks now cover the
[proof's explicit guarantees](../../tests/rsc-proof/README.md); the ordinary
browser gate still verifies client behavior. Client-only hooks and class
components remain invalid in server components, as in React.

The proof's Node transport is not a production deployment or a general remote
Java invocation mechanism. Spring session/CSRF wiring, protected SSR snapshots,
the shared-contract generator and external consumer packaging retain their own
G0 acceptance criteria. Record actual test results and remaining limits before
changing an RSC ledger row from excluded or untested.
