# Astra Ultra implementation prompt: native JX support in TurboJ

Implement this task in TurboJ. Deliver working code, native editor integration,
tests, documentation, and a locally runnable packaged application. Do not stop at
an architecture proposal, syntax highlighting, or a disconnected language service.

## Objective and product expectation

Make `.jx` a first-class source language in TurboJ: ordinary Java development
with embedded HTML and typed component markup, in the existing native editor.
The experience must include completion, navigation, diagnostics, documentation,
refactoring, formatting, project integration, and the JX development workflow.

We own TurboJ's editor. Use that control to provide one coherent document and
editing experience. The existing JX IntelliJ and VS Code integrations are useful
references, but their adapter limitations are not the desired native experience.
Do not implement a separate web editor, require either IDE, or merely associate
the extension with Java or XML. Hidden semantic projections are acceptable;
generated Java must not become the document the developer has to edit or navigate.

Use `@Component`, imported from `jx.Component`. The runtime descriptor is
`ComponentType<P>`. Do not introduce new examples using `@JxComponent`; preserve
existing compatibility where applicable. Spring's `@Component` is a distinct
annotation and must not cause a class to be treated as a JX component.

## Workspace and initial inspection

Local reference checkouts:

- TurboJ: `/Users/luca/Workspace/turboj` — primary implementation repository.
- JX: `/Users/luca/Workspace/jx` — compiler, runtime, Maven tooling and examples.
- Public JX repository: https://github.com/lmrrcc/jx
- JX reference baseline when this prompt was written: commit `80bb39b`.

These paths describe this workstation; never hard-code them in shipping code.
Inspect the current commits, working trees, applicable agent instructions, skills,
module boundaries and build commands before editing. Preserve unrelated work.
Read current implementation as well as documentation: older TurboJ intelligence
documents describe an earlier project model and may omit current MavenJ support.

Verified architectural starting points, which you must inspect and adapt:

| Area | Existing TurboJ entry points |
| --- | --- |
| Native editor | `app/src/main/java/dev/turboj/ui/EditorComponent.java` |
| Source storage | `core/src/main/java/dev/turboj/editor/document/PieceTable.java` |
| Highlighting | `core/src/main/java/dev/turboj/editor/highlighting/JavaLexer.java` |
| Syntax analysis | `core/src/main/java/dev/turboj/language/JdtSyntaxAnalyzer.java` |
| Shared semantics | `core/src/main/java/dev/turboj/intelligence/JavaIntelligenceService.java`, `JdtJavaIntelligenceService.java` |
| Editor intelligence | `app/src/main/java/dev/turboj/intelligence/`, including `CompletionCoordinator`, `IntelligenceController`, `KeyboardJavaController`, `QuickFixController` |
| Maven boundaries | `core/src/main/java/dev/turboj/project/MavenSemanticAdapter.java`, `MavenCompilationWorkspace.java` |
| Compilation | `core/src/main/java/dev/turboj/compiler/` |
| Supporting libraries | `refactorj/`, `javafmt/`, `mavenj/`, `commandj/`, `turbotest/` |

TurboJ is a Java 25 Swing/Java2D application with immutable source snapshots,
a custom editor, a serial semantic worker, and native command/keymap handling.
Java intelligence shares the RefactorJ workspace. Its standalone JDT integration
uses ASTParser and bindings; Eclipse Java-model completion APIs cannot simply be
called on arbitrary strings without their workspace infrastructure. Respect the
actual API constraints and keep completion and refactoring on coherent semantics.

Read these JX sources and guides before selecting the integration boundary:

- `jx-compiler/src/main/java/jx/compiler/`: `JxCompiler`, `EditorProjection`,
  `GeneratedSource`, `SourceMap`, component metadata and recovery parsing.
- `jx-language-server/` and `docs/language-server.md`: existing editor contracts.
- `docs/language.md`, `docs/tooling.md`, `docs/maven.md`, `docs/browser.md`.
- `docs/decisions/0002-native-java-editor-services.md` and
  `docs/decisions/0003-state-preserving-development.md`.
- `examples/counter-app`, `examples/todo-app`, `examples/todo-api`.
- `docs/JX_Astra_Max_Prompt_v2.txt`: product background; current source and later
  decisions supersede obsolete API names or unimplemented aspirations in it.

## Architecture requirements

Write a concise ADR establishing the chosen boundary, then implement it.

1. Keep the authored `.jx` document, its URI and its version as the source of
   truth. Retain the existing caret, selection, undo, autosave, external-change
   protection, recovery, accessibility and input-method behavior.
2. Reuse JX parsing, typed component metadata and source mappings. Prefer an
   in-process compiler dependency when compatible with TurboJ's architecture.
   Do not fork the language grammar or build a second regex semantic engine.
   A lightweight highlighting lexer must agree with compiler grammar fixtures.
3. Integrate Java analysis into TurboJ's existing JDT/RefactorJ workspace. A
   generated or editor-specific Java projection is an internal representation,
   not a separately editable file or a second independent semantic workspace.
4. Define source identity and mapping contracts for text, diagnostics, symbols,
   navigation and edits. Use UTF-16 offsets consistently. Account for CRLF,
   supplementary Unicode, URI escaping, file renames and unsaved buffers.
5. Distinguish display/navigation anchors from editable source ranges. A synthetic
   generated span cannot authorize an edit. Map arbitrary Java-engine edits only
   when exact and safe; use explicit semantic transformations for markup edits.
   Handle duplicated or reordered projection contexts without editing the wrong
   authored occurrence or applying an edit twice.
6. Recognize markup structurally: nested elements, fragments where supported,
   components, props, children, quoted values, expressions and Java lambdas.
   Distinguish markup from Java comparisons, generics, strings, text blocks and
   comments. Recover from incomplete source without changing strict build parsing.
7. Use immutable versioned snapshots and background analysis. Reject stale results
   using document, workspace, project-model and request identities as appropriate.
   Bound caches and queues, coalesce edits, and define cancellation and disposal.
   No parsing, dependency resolution or compiler work on the Swing EDT. Avoid
   interrupt-based cancellation that can corrupt shared compiler initialization.
8. Preserve module ownership and dependency direction. Extend existing contracts
   where warranted; introduce a new module only for an actual dependency boundary.
   Shared JX compiler changes belong in JX with compatibility tests and reproducible
   consumption by TurboJ, not copied source or unexplained locally installed JARs.

## Native editing capabilities

### Highlighting and structural editing

Use the current theme's semantic colors for Java keywords, types, methods,
annotations, comments and strings, alongside HTML tag names, component names,
attributes, delimiters, text and embedded expressions. Preserve usable colors in
Turbo Classic and light/dark themes; do not hard-code the promotional palette.

Implement context-aware indentation, closing tags, bracket matching, selection,
comments and completion insertion. Java expressions inside attributes and children
must behave as Java. Text containing braces or angle brackets must follow JX's
actual escaping rules. Typing an incomplete tag must not recolor the entire file
permanently or suppress all unrelated Java intelligence.

### Completion and documentation

Expose the same native completion UI, keyboard behavior and cancellation as Java:

- Java locals, fields, methods, types, imports, constructors, overloads, parameter
  hints, Javadoc and expected types in ordinary code and embedded expressions.
- HTML tags and supported attributes; typed JX components and their actual props.
- Record-prop names and types, required-prop diagnostics, children and special
  attributes according to the current compiler/runtime contract.
- Correct prefix replacement, caret placement, snippets and auto-import edits.
- Event lambdas, extracted handlers and method references supported by the runtime.
- Semantic completion retains priority over optional AI ghost text.

Use bindings and compiler metadata. Do not infer component props from names alone
or advertise arbitrary HTML/event support beyond what the JX runtime accepts.

### Navigation, symbols and references

Make normal TurboJ commands work without a separate JX command family:

- Go to declaration from Java identifiers, opening/closing component tags and props.
- Navigate `.jx` to `.java`, `.java` to `.jx`, and between `.jx` files, including
  unsaved changes and legal cross-module dependencies.
- Find usages across Java expressions and component markup; distinguish unrelated
  symbols with identical spellings and HTML names from user component names.
- Outline and workspace symbols for components, nested records, fields and methods.
- Library/JDK source and documentation follow existing read-only attachment rules.
- Navigation history returns to the authored location with undo history intact.

After Maven generation, definitions must still resolve to authored `.jx`, without
duplicate symbols from generated Java. Model generated-source provenance in the
native workspace; do not delete build outputs to conceal duplicate declarations.

### Diagnostics and quick fixes

Combine JX syntax/prop diagnostics and real Java diagnostics in original source
coordinates. Missing imports, wrong expression types, invalid method calls and
incorrect props must be actionable. Deduplicate overlapping errors and keep
generated helper names out of normal messages where a source-level explanation
exists. Incomplete-code analysis must remain useful during editing.

Integrate applicable existing quick fixes, especially add import and safe Java
symbol fixes. Map and validate every resulting edit. A fix must not insert Java
into HTML text or modify a generated file. Publish diagnostics only for the
snapshot they describe, and clear obsolete diagnostics when corrected.

### Refactoring and formatting

Reuse RefactorJ previews, source-version checks, cross-file transactions and undo.
Implement rename for locals, parameters, fields and methods within Java islands;
component classes/files across imports and matching tags; and record props across
declarations, Java accessors and markup attributes. Check collisions and reject
transformations that would reinterpret a component as an HTML element. Preserve
comments and unrelated same-spelling identifiers.

The user's handler-extraction use case must work: extracting an event lambda into
a typed helper, or navigating/completing that helper when written manually. Expose
Extract Method where safe through the existing refactoring machinery. For other
existing RefactorJ operations, publish explicit eligibility; never apply a partial
unsafe transformation or claim all operations work merely because rename works.

Provide Format Document and Format Selection for mixed source, using javafmt where
appropriate and structural markup formatting. Do not send raw `.jx` to a Java-only
formatter. Preserve meaningful markup whitespace, strings, text blocks, comments,
line endings and expression semantics. Formatting is idempotent, version-checked,
uses minimal edits, and is undoable in one operation. Respect format-on-save.

## Projects, builds and browser development

Use MavenJ's effective project model: configured JX roots, generated roots,
profiles, reactor dependencies, release/toolchain settings and main/test scopes.
Support the actual JX Maven plugin configuration rather than only one hard-coded
example folder. Main code must not resolve test-only dependencies or unrelated
sibling modules. New File/project flows and file filters must recognize `.jx`.

Connect native build/run actions to the existing JX Maven workflow. JX browser
applications must not be launched as if each component had a JVM main method.
Keep ordinary Java Run/Test behavior intact. Offer an explicit development launch,
module selection where needed, clickable original-source diagnostics, browser
opening and Stop. Reuse existing project trust and process ownership rules.
Typing or hovering must not silently start Maven, download dependencies, execute
project code or launch a server. Dispose owned processes on the documented lifecycle
without killing unrelated servers.

Hot reload must preserve compatible mounted component state. Integrate JX's
existing typed-checkpoint mechanism across rebuilt TeaVM generations; do not reuse
opaque Java objects across generations or substitute unconditional page reload.
Use real React/React DOM. Verify independent counters, focus and route state,
effect cleanup/restart and no repeated application commands caused by a refresh.
When hook signatures or model codecs are incompatible, reset only as supported
and explain the reason. Failed compilation keeps the previous app usable; a later
successful edit recovers. Exercise the todo example as well as a counter.

Do not promise a debugger already exists: TurboJ currently documents its debugger
as open work. This task requires source-mapped build/runtime diagnostics and the
development loop, not inventing Java-to-browser breakpoint support. Document the
remaining source-map chain and a separate verified milestone for browser stepping
if not implemented. Do not represent ordinary JVM debugging as TeaVM debugging.

## Required acceptance fixture

Use the actual runtime types and verify this source compiles; adapt only to real
current API signatures and explain any necessary API change:

```java
package demo;

import jx.Component;
import jx.EventHandler;
import jx.Hooks;
import jx.State;
import jx.View;

@Component
public final class Counter {
    public record Props(int initial, String label) {}

    public static View render(Props props) {
        State<Integer> count = Hooks.useState(props.initial());
        return (
            <button onClick={increment(count)}>
                {props.label()}: {count.value()}
            </button>
        );
    }

    private static EventHandler increment(State<Integer> count) {
        return event -> count.update(value -> value + 1);
    }
}
```

Render two instances with different initial values in `App.jx`. Include a Java
helper used inside markup, a Java consumer of a JX declaration, a typed prop error,
an unresolved import, and a separate module with a test-only dependency.

The actual native editor walkthrough must demonstrate:

1. Open the Maven project and `.jx`; Java and markup receive proper colors.
2. Type `count.` and `props.` inside markup; accept real typed completion using
   the active keymap, then undo. Check Enter/Tab/Escape and focus behavior.
3. Navigate a tag to its component, a prop to its record declaration, an event
   helper to its method and a Java use back to `.jx`; return through history.
4. Find usages and rename a method, prop and component across files, preview,
   apply and undo atomically. Verify behavior after Maven generation too.
5. Correct a type error and add an import using the native diagnostic/quick-fix
   UI. Verify original ranges and absence of generated-source edits.
6. Leave an expression/tag incomplete, continue editing elsewhere, and recover
   intelligence after fixing it. Race a delayed response against a newer edit.
7. Format mixed source, check semantic whitespace, and undo in one action.
8. Launch development, increment both counters, edit markup and verify preserved
   independent state. Test offline counter interaction after assets load.
9. Run the todo workflow, preserving appropriate state through refresh, and test
   compilation failure/recovery and explicit Stop.
10. Repeat representative Java-only completion, navigation, refactoring, Run and
    Test checks to prove the integration has not regressed existing workflows.

## Verification and delivery

Build targeted automated tests for parsing/recovery and mappings, semantic
completion/navigation, module isolation, diagnostic versions, transactional edits,
formatting, process lifecycle and stale/cancelled requests. Cover Unicode, CRLF,
path spaces, open/closed files, missing dependencies, malformed markup and duplicate
generated sources. Tests must exercise production wiring rather than only helpers.

Extend TurboJ's existing native Swing smoke harnesses, editor tests and evidence
conventions. Inspect `IntelligenceSmoke`, current keyboard/refactoring tests and
`docs/evidence/` before designing another harness. Exercise the real window and
command dispatch; screenshots alone and lexer unit tests do not prove editing.
Use existing browser checks for state preservation and record what ran. Distinguish
programmatically dispatched UI events from physical keyboard/manual verification.

Measure warm/cold semantic latency and editing responsiveness against the existing
Java baseline on the same machine and fixtures. Record p50/p95, file sizes, sample
counts and methodology; investigate regressions and unbounded work. Do not invent
performance numbers or claim incremental parsing if using full recovery parses.

Verify clean dependency resolution and reproducible packaging, not just a build
that succeeds due to unpublished artifacts already in the local Maven cache.
Inspect current scripts; documented starting points include `./mvnw package`,
`./scripts/test.sh` and the native application launcher `./turboj`. Run the actual
appropriate commands and record exact versions and any required JX dependency build.

Deliver in this sequence, continuing through all required capabilities:

1. A short audit, capability matrix and ADR with concrete acceptance fixtures.
2. A working native vertical slice: open, highlight, complete, diagnose and navigate
   one `.jx` component through the existing editor and semantic workspace.
3. Cross-file/project semantics, refactoring, formatting and recovery behavior.
4. Maven development integration, state-preserving refresh, native/browser checks
   and a packaged TurboJ artifact containing the integration.
5. User documentation, reproducible commands and evidence, with each capability
   marked implemented/tested/limited rather than a blanket claim of IDE parity.

Keep changes focused and reviewable. Make required shared-library changes with
compatible callers and tests. Use applicable repository instructions for commits;
do not publish releases, deploy, or modify unrelated repositories without scope.
Do not stop for approval of ordinary reversible implementation choices. If an
external restriction prevents a check, report it precisely and continue all
independent work; never label an untested capability verified.

Final handoff must state what works in the native editor, exact artifact paths,
build/test commands and results, measured limitations, and remaining explicit
capability gaps. Native `.jx` support means the developer edits Java plus markup
in TurboJ's normal editor and commands, with semantics attached to their source.
