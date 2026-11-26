# M1-27: packaged MV3 and Rust/WASM feasibility

Status: bounded M1-27 feasibility proof accepted in PR83 after the publication gate and Verify. This is the G0
runtime/packaging proof, not the later portfolio extension qualification.

## Executed boundary

[`App.jx`](../../examples/mv3-app/src/main/jx/jx/example/extension/App.jx) goes through
JX generation, javac, TeaVM and the real React/React DOM runtime. Its event handler
calls an example-only typed Java binding to the existing upstream Rust
[`brotli-wasm`](https://github.com/httptoolkit/brotli-wasm) 3.0.1 package. No new
application language, framework WASM compiler or replacement renderer is added.
The npm artifact's integrity is pinned in `package-lock.json` and the version is
checked by `CheckBaseline`.

The normal Maven package creates the Java application and web assets. Java
[`PackageExtension`](../../tools/PackageExtension.java) bundles the small interop
bootstrap, copies the local WASM binary and licenses into a fixed, bounded ZIP,
then extracts the ZIP again. Browser tests consume that extraction, not a source
directory. Extraction rejects unknown files left by an earlier build and removes
known previous outputs so a stale file cannot satisfy a missing package input.
Archive paths are fixed; symlinks in inputs/output paths are rejected.

The manifest has no host permissions or general extension permissions. Its
script policy allows only self and `wasm-unsafe-eval`; object sources are disabled
and connections/styles/default resources stay on the extension origin. Chrome
documents the distinction between WASM compilation and general evaluation in
its [extension CSP contract](https://developer.chrome.com/docs/extensions/reference/manifest/content-security-policy).
No CSP-disabling launch option is used.

The framework bootstrap loads WASM before invoking the Java entry point. Ordinary
component state and click behavior stay in Java. The binding bounds demonstration
input, uses a declared result schema and checks that decoded Unicode text equals
the input. It only decompresses the bytes it just compressed; arbitrary hostile
compressed streams are outside this API/example.

## Evidence

- Selected Maven reactor: **150 JVM checks** (66 core, 10 React, 59 compiler,
  15 Maven plugin) and actual TeaVM compilation pass. Final run: **16.672 seconds**.
- Complete extension suite: **3/3 Chrome checks in 2.6 seconds**, Google Chrome
  **158.0.8127.37**, macOS arm64. No retries, skips or running developer server.
- The first check invokes `Extensions.triggerAction` on the actual browser tab,
  observes the new popup target, runs Java→Rust/WASM, closes that target and opens
  a different target whose hook state starts at zero. It does not substitute tab
  navigation for action-popup creation. Playwright does not expose this popup as
  a `Page`, so a bounded test-only protocol session observes its DOM and triggers
  the button. This tests computation/lifetime, not trusted keyboard or pointer
  events in the toolbar popup.
- A separate declared-popup document check uses ordinary Playwright clicks,
  verifies the Unicode round trip and compressed/input byte lengths, observes
  two successful packaged `.wasm` responses over reopen, and records no remote
  HTTP requests or browser errors.
- The negative CSP check installs a copy without `wasm-unsafe-eval`. WASM
  initialization fails visibly with Chrome's policy error and Java UI never
  mounts. The positive archive remains unchanged.

The initial action-driver attempts found two test-harness distinctions: the
action command requires a `tab` target rather than the page/frame target, and
Playwright's `page` event does not expose this popup. The corrected driver uses
the published [Chrome extension protocol](https://chromedevtools.github.io/devtools-protocol/tot/Extensions/)
in a fresh temporary profile with extension debugging enabled, using the pipe
already owned by Playwright. It does not change the product manifest or policy.

Final locally checked archive SHA-256:
`dba8edfe7f859a1e7cf5d7700d1d0f7d3262aefff542d358881b4ba7ce0eeca5`.
This identifies this build; it is not a claim of reproducibility across machines
or a signed release. The packaging tool prints the hash for every build.

## Limits and reproduction

Run the commands in the [example guide](../../examples/mv3-app/README.md).
`npm run test:extension` is added to Verify and the matching local push gate.
The original macOS source qualification is supplemented by the publication Verify evidence below. The Chrome
protocol is test infrastructure with a version-sensitive experimental surface;
unsupported commands fail the test rather than silently skipping the proof.

This demonstrates packaged JX under actual MV3 CSP and one existing Rust/WASM
binding. It does not qualify arbitrary Java class-library APIs, arbitrary WASM
packages, service-worker suspension, extension storage, message authorization,
content scripts, privileged host access, wallet protocols, Web Store submission
or cross-browser behavior. Those remain explicit later milestone work. Resource
and domain ownership do not survive popup closure by accident: persistent state
needs a separately specified storage/message contract.

## Publication acceptance

[PR83](https://github.com/lmrrcc/jx/pull/83) merged as `c13bfd8e65ccfb7ed7c483a46fba9e37795455f6`. Exact head `c1432dc` passed all 19 local gate steps in 461 seconds and both [PR Verify](https://github.com/lmrrcc/jx/actions/runs/45846065539) and [push Verify](https://github.com/lmrrcc/jx/actions/runs/45845915260). The integrated reactor passes 206 JVM checks plus TeaVM; 47 runtime checks, 975 conformance checks with 217 explicit exclusions, four Todo scenarios, two packaged scenarios, two authentication hosting modes, three protected SSR scenarios, four real hot workflows and all three extension checks pass. The extension checks take 2.5 seconds in this local run. The 149-case inventory and 62/6328 interaction count remain unchanged.

This integrated ZIP has SHA-256 `785b346b770b51452370e8166a53ec233886232690953040e1c9aa03fb828084`. It is a build identifier, not a claim of reproducibility or a signed marketplace release. These publication results preserve all feasibility and platform limits above.
