# 0002 — Native Java editing inside `.jx`

Status: accepted architecture; supported operations are recorded with verification
evidence in [the tooling guide](../tooling.md).

## Requirement

An application developer writes ordinary Java declarations, methods, lambdas and
expressions inside `.jx`, together with HTML/component markup. Java completion,
navigation, type feedback and source edits must use the editor's Java semantic
engine and the application's dependencies. Syntax coloring and component-only
language-server requests do not satisfy that requirement.

The public authoring annotation is `jx.Component`. The stable runtime descriptor
is `jx.ComponentType<P>`; `jx.JxComponent` remains a deprecated annotation alias.
The annotation is unrelated to Spring's `org.springframework.stereotype.Component`.

## Shared compiler projection

The compiler produces an in-memory Java view of the current `.jx` snapshot. It
retains imports, declarations, scopes, Java expressions and typed callback
contexts. Generated wrappers express markup in Java; they are not application
source. Editor recovery can retain incomplete syntax, while the build compiler
continues to reject malformed input.

Editor projections preserve the authored order of Java slices. When props appear
in a different order from the record constructor, each expression gets its own
typed constructor context with synthetic defaults for the other parameters.
These contexts are for analysis only; build output and evaluation semantics are
unchanged. This avoids invalid native PSI pointers across reordered fragments.
Expansion budgets bound these editor-only contexts.

All offsets use UTF-16. Linear source-map slices identify unchanged authored
Java; synthetic slices provide diagnostic anchors. An editor edit must fit an
exact editable slice. A diagnostic anchor never grants permission to edit the
source. Adapters must reject stale snapshots and edits that cross generated
wrappers. Markup symbols need explicit references to their authored ranges.

## IntelliJ IDEA

Depend on the bundled `com.intellij.java` plugin. A `PsiLanguageInjectionHost`
child spans the `.jx` file. The supported `MultiHostInjector` API assembles a
complete injected Java compilation unit from the compiler's copied slices and
generated prefixes/suffixes. This provides native Java PSI and lexical context
inside methods and markup expressions. The platform maps edits through its
injected document. A Java element finder and reference/search integration expose
JX declarations to the surrounding Java project.

The injection host also registers an `ElementManipulator`: `updateText` alone is
insufficient for native Java intention/ModCommand transactions. Host replacements
must not schedule whole-file reformatting during a local rename or quick fix.
Native host formatting, structure, folding, comments and typing are tracked in
[the mixed-language decision](0004-intellij-mixed-language-editing.md); they are
separate contracts rather than automatic consequences of Java injection.

Maven-generated counterparts otherwise become duplicate Java declarations.
For the standard Maven output directories, a language substitutor treats only
manifest-owned counterparts as plain text in the IDE. Ownership requires an
`outputs.tsv` entry and a `.jxmap` pointing to an existing authored source in the
module. Physical `.java` files and compiler source roots remain unchanged.
Unowned Java retains its normal Java language. Ownership changes trigger targeted
PSI/index refresh; no project-wide cache reset is required.

The markup lexer remains responsible for HTML-style colors. The shared JX
language server supplies component/prop semantics and compiler diagnostics.
Java parser internals are not replaced through reflection or classloader patches.
The supported IDEA build remains pinned because Java PSI and LSP integration
compatibility must be tested against each target SDK.

## VS Code

Depend on the official Red Hat Java extension. Its supported `javaExtensions`
contribution loads a small JDT LS bundle with explicit delegate commands. The
bundle maintains owned Java working copies within the imported Java project and
calls JDT's semantic handlers. Working copies contain the compiler projection;
they are never committed to disk. Authored Java files and externally owned open
working copies must not be replaced.

The TypeScript adapter maps completion, navigation, diagnostics and safe edits
between these working copies and versioned `.jx` snapshots. It does not change
the Maven classpath, create hidden dirty editor documents, or depend on private
language-client access. Closing a source or stopping the adapter releases the
working copies it owns. The bridge pins its compatible Java extension/JDT LS
versions; those internal semantic handlers are not a promise of compatibility
with arbitrary future extension versions.

The official Java extension does not expose a public result-mapping middleware
for Java-origin navigation. JX therefore provides **JX: Go to Definition** and a
scoped F12 binding for Java files in JX Maven modules. It uses the same native JDT
definition handler and translates projected destinations to authored `.jx`.
Other stock Java extension actions are not silently intercepted.

## Verification boundary

Test actual editor completion, navigation, diagnostics, searches and edits in
ordinary Java and `{...}` expressions, including cross-file dependencies and
incomplete code. Test packaged artifacts as well as compiler/protocol contracts.
Preserve markup text during Java rename and reject unmappable generated edits.
Passing a lexer test or producing an installable archive does not establish Java
feature parity. Formatting, arbitrary refactorings and browser debugging require
their own implementation and acceptance evidence.
