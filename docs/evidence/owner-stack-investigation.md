# R-29-04: development owner-stack depth

Investigated on 25 November 2026 with React/React DOM 19.3.1, TeaVM 0.16.0,
Java 25 and Chrome. The R-29 implementation/evidence commit is `3eed5a0`.
This is an open compatibility limit, not an accepted reduction of the GA goal.

## Reproduction and supported behavior

The first `runtime-utilities` case expected a parent name from a Java component's
layout effect. The React reference produced that name; JX returned an empty
string in client, hydration and lazy modes. This was initially classified as
absent by the fixture, which incorrectly conflated empty string and null.

The API now preserves the raw React result. A real native React `NativeOwner`
creates the exported Java `DiagnosticOwner` consumer in `OwnerStackInterop.java`;
that consumer captures the actual owner name. Outside a React-controlled call,
and in an actual production bundle, the adapter returns null. The final focused
run passed 14 checks and the full bench passed 801; neither proves complete deep
Java owner chains. React documents development-only owner capture separately
from component/error stacks, including conditional namespace access when its
production export is absent. [React captureOwnerStack](https://react.dev/reference/react/captureOwnerStack)

## Why source maps do not repair the missing chain

The installed React sources provide the immediate cause:

- `react/cjs/react.development.js`, `createElement`, lines 1126–1129, temporarily
  limits each element's captured creation stack to ten frames.
- `react/cjs/react-jsx-dev-runtime.development.js`, exported `jsxDEV`, also captures
  ten frames; its public entry point has no caller-supplied owner-stack parameter.
- `react-dom/cjs/react-dom-client.development.js`, `formatOwnerStack`, lines
  682–702, discards a captured stack when its React rendering frame is missing.
- JX currently converts the returned Java `View` recursively while the Java
  component callback, TeaVM functor and conversion frames are still on the stack.
  Those frames can consume the limit before the rendering frame is captured.

The generated `tests/conformance/target/browser/application.js.map` already names
`DiagnosticOwner.java` and `RuntimeUtilities.java`. TeaVM's installed
`org.teavm.debugging.information.SourceMapsWriter` writes generated positions and
source file/line mappings; it does not change the JavaScript call chain or restore
frames React never retained. Source maps remain useful for existing locations and
debugger navigation, which TeaVM documents as a separate facility.
[TeaVM debugging](https://www.teavm.org/docs/tooling/debugging.html)

## Boundary for a future correction

No small public React setting resolves this. Increasing the application's usual
stack limit cannot override React's local ten-frame capture. Switching to the
public JSX development runtime has the same limit. Replacing the owner chain with
a Java exception stack or a component stack changes its meaning.

A candidate architectural correction is to separate typed Java view description
from native element materialization. Java would finish building the description
and unwind; a shallow native component function would then materialize React
elements through public APIs, with bounded traversal stack depth. This requires
an explicit representation/schema and preserved component ownership, keys, refs,
contexts, foreign elements, class components and server semantics. It is a
renderer design change, not an unverified diagnostic patch.

Acceptance for that work must include deep ordinary Java-authored trees and
exported/foreign components, meaningful owner names, original-source locations,
no added reconciliation boundaries, existing lifecycle/SSR regression checks and
production absence. Investigate source-position attribution as well as frame
depth; a generic native materializer frame alone is insufficient. Do not mutate
React's private element fields, impersonate internal frame names, patch global
Error/stack hooks, or claim the interoperability fixture repairs pure-Java chains.

R-29-04 and R-31 remain open until a supported correction is verified or Luca
explicitly accepts the limitation.
