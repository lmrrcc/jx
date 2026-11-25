# Server import maps

R-31 option-level work, 25 November 2026. Integrated source `8c5c337` passes the
package checks (56 core, 10 React, 54 compiler and 15 Maven tests) and the complete
bench: **937 passed, 199 skipped, 142 cases**. Client mode passes 140 reference /
139 JX checks; hydration 104, static HTML 110 and lazy mode 115 per side. All
62/6328 registered interaction pairs remain green; this option adds no pair credit.
Mutations and publication gates remain pending; this is not completed parity.

`ReactServer.StreamOptions.withImportMap` accepts a typed immutable `ImportMap`
containing imports, scopes and integrity metadata. It preserves null versus
empty top-level maps, nested insertion order, and explicit null import targets
that block resolution. Input maps are copied recursively. Null scope bodies or
integrity values, empty module-specifier keys and empty integrity keys are
rejected. Empty scope keys remain valid references to the document base URL.
Integrity uses URL-like module-specifier resolution, which excludes empty or
bare relative keys; scope keys use ordinary URL parsing. Browser URL validation
otherwise remains native.
One map retains at most 4096 entries
(scope entries included) and 1 Mi UTF-16 code units. The framework creates native
dictionaries without a prototype: `__proto__` and `constructor` are real own
specifier keys, not special setters.

```java
var modules = new ImportMap(
        Map.of("shared", "/assets/shared.mjs"),
        Map.of("/admin/", Map.of("shared", "/assets/admin-shared.mjs")));
var options = ReactServer.StreamOptions.defaults()
        .withImportMap(modules)
        .withBootstrapModules(List.of("/entry.mjs"));
```

Relative URL resolution, prefix and scope matching, and subresource integrity
belong to the browser. The Java type does not emulate WHATWG URL parsing on the
JVM. This is trusted build configuration: callers must not redirect executable
imports using unchecked request data. React performs JSON serialization and
script-context escaping; JSON text is not assembled by application concatenation.
The null target used by the browser's resolution model can produce an authoring
warning in Chrome; the fixture checks that warning and the actual denied import.

Four initial renderer variants accept the option. Pinned React ignores it on
all four resume paths, so JX rejects it there instead of promising an effective
replacement. Existing four-, six- and eight-argument options constructors remain;
the pre-GA record shape has expanded and consumers should rebuild. Null
`withImportMap` removes the script. Non-null empty configuration still emits it.

The compiling [Java consumer](../../tests/conformance/src/main/java/jx/conformance/ImportMapExports.java)
and [differential browser case](../../tests/conformance/specs/server-import-map.spec.js)
verify exact React output, ordering before bootstrap modules,
request isolation, global versus scoped mappings, prefix mappings, special own
keys, script terminator escaping, blocked imports, nonce propagation and a real
integrity allow/deny check. The case runs in its
own browser document rather than bench-root hydrate/static/lazy modes; no new
interaction pair is credited merely by using these features together.

The focused run passes 2 checks with 6 inapplicable cells. After independent
review, the integrity-denial phase also requires exactly one expected integrity
error, checks both expected import-map warnings and proves the independent inline
module still runs. Both focused checks passed again before integration. The
integrated full run adds only the `server-import-map` row to the recorded case
table; all previous rows and the interaction table are unchanged. The run also
qualifies the parent header/resume, bootstrap, synchronous act and sink consumers
alongside all three pending class-policy slices. Those slices retain their own
publication and mutation evidence; no full mutation-catalog claim is made.

The pinned React 19.3.1 implementation serializes the typed object through its
public `importMap` option. The public documentation marks some nonce behavior
Canary; support is qualified against the pinned runtime rather than asserted for
every React release. The first focused run exposed the distinction between empty
scope and integrity keys on both native React and JX; the Java schema and fixture
were corrected rather than suppressing the unexpected warning. Primary references:
[React streaming options](https://react.dev/reference/react-dom/server/renderToPipeableStream)
and the [HTML import-map processing model](https://html.spec.whatwg.org/multipage/webappapis.html#import-maps).
