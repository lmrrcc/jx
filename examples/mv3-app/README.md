# JX MV3 and Rust/WASM proof

This browser extension renders a `.jx` component through the real React runtime
and calls the pinned `brotli-wasm` Rust library from Java. The application has no
server, remote code, host permissions or privileged wallet behavior.

From the repository root, with Java 25, Node and Google Chrome installed:

```sh
npm ci
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl examples/mv3-app -am package
npm run test:extension
```

The Java packaging tool creates `target/jx-extension.zip` in this module and
extracts it into `target/mv3-extracted`. The tests load that extracted archive in
a temporary Chrome profile, never in the developer's profile. To inspect it
manually, use Chrome's extension developer mode and **Load unpacked**, selecting
`examples/mv3-app/target/mv3-extracted`, then open its action popup.

The package contains local Java/React output, the upstream WASM binary and glue,
HTML/CSS, a Manifest V3 manifest and license notices. Source maps are omitted.
The Java binding accepts at most 4096 UTF-16 units and round-trips text it has
just compressed; it is not an untrusted compressed-data decoder.

The manifest permits packaged scripts and WebAssembly compilation with
`'wasm-unsafe-eval'`. It does not permit inline scripts or general `'unsafe-eval'`.
All other resources are restricted to the extension origin. Bootstrap waits for
the packaged module before starting Java; initialization failure remains visible
and does not start a fake working app.

The suite exercises the actual Chrome action popup, closes its target and opens
a new instance, then independently checks Unicode data, real WASM responses and
the absence of remote HTTP requests in the declared popup document. A stricter
manifest without WASM permission must reject compilation. See
[the evidence and limits](../../docs/evidence/mv3-wasm.md).
