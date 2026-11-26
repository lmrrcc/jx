# Explicit nullable and sparse class state authoring

R-28-03h4, scoped on 25 November and qualified locally on 26 November 2026.
**Source bd27ef7 passes compiler/package and eight focused projects; integrated
qualification and publication remain pending.** The runtime contracts are already represented by `withNullableState`,
`ClassSchema.optionalField` and `Presence`. This slice compiles explicit `.jx`
declarations to those APIs; it does not change native React state merging.

## Selected publication integration

The selected branch preserves accepted main's explicit nullable/sparse checkpoint
codec contract and imports only legacy runtime, legacy authoring and sparse
compiler changes. It does not import creator changes or hot protocols 4/5.
The test-only `acedb6d` admission update exposed one further dependency: its
normal/Pure loop presumed Pure checkpoint support from the broader historical
branch. The current integration reproduces the mismatch (one error in five sparse
compiler tests), then distinguishes the contracts explicitly: normal descriptors
must admit the supplied codec and preserve schema/nullability; Pure descriptors
must still reject checkpoint admission. All schema, builder, source-mapping and
normal/Pure compiler assertions remain enabled. No runtime guard is relaxed.
The RED log is `/private/tmp/jx-sparse-authoring-integration-red.log`.

The corrected selected source `ef3c5ec` passes **188 JVM tests** (84 core,
11 React, 68 compiler, 15 Maven plugin, 10 language server), plus TeaVM compilation,
in 33.072 seconds. The browser run passes **16/16 Chrome checks** in 22.7 seconds:
the eight sparse projects and all eight legacy-authoring regression projects.
`CheckBaseline` validates 155 registered cases and the unchanged 62 pair witnesses.
The complete result tables are unchanged. Full integration, compiler mutation,
publication gate and Verify remain pending.

The GREEN build log is `/private/tmp/jx-sparse-authoring-integrated-build.log`.
Focused command: `BENCH_PORT=59185 node node_modules/@playwright/test/cli.js test
-c tests/conformance/playwright.config.js -g 'class-sparse-authoring|class-legacy-authoring'
--workers=1`. Historical numbers below remain evidence for their named source only.

## Scope fixed before implementation

- A source-retained `@jx.NullableState` marker opts a direct `@Component` class
  into null aggregate state. There is no inference from constructor bodies and
  no nullable props opt-in. Functional, unmarked and indirect class declarations
  may not use the marker silently.
- A state record field declared `Presence<T>` becomes an optional native field.
  `T` must be one of the existing supported boxed scalar, String or reifiable
  reference/array value types. Raw, wildcard and nested parameterized Presence
  forms fail with original-source diagnostics. Ordinary required state and all
  required props retain their existing checks.
- Generated schemas use `optionalField` and `Reader.find`. The state record owns
  the presence value, preserving absent, own undefined and defined null. No Java
  zero, false, null or empty object is invented for an absent value.
- `statePatch().withName(T)` assigns a defined value; `undefinedName()` assigns
  own undefined for a Presence field. Omitting a builder field preserves the
  native previous property. No generated deletion or state-null-reset operation
  is introduced. Generated method-name collisions fail explicitly.
- Compiler metadata uses a distinct `StateField` layout; `Prop.optional` keeps
  its existing optional-prop meaning. Existing metadata constructors remain and
  derive the strict state layout. New record components change record patterns
  and enumeration before GA; this compatibility boundary is documented.
- Descriptor composition preserves context, derived/error/legacy methods, Pure
  behavior and native defaults. The accepted journal admits explicit typed
  codecs preserving nullable aggregate and sparse presence; generated descriptors
  retain that contract. No automatic checkpoint codec or relaxed commit guard is
  added.

## Required evidence

1. Compile normal and Pure `.jx` consumers against the actual core, with an
   explicit null initial state and both Presence and required fields where valid.
   Type-check executable/editor projections and map signature/body diagnostics
   and editable Java expressions to original source.
2. Verify generated partial and undefined updates, immutable builder behavior,
   source-resolved JX annotations/Presence types, import shadowing, invalid
   marker placement, unsupported nested/wildcard types and generated collisions.
3. In native React and JX, demonstrate null to empty aggregate, absent fields,
   defined values, own undefined and defined null. Compare native own-property
   shape, callback order, Pure shallow decisions, retained class refs/DOM and
   nullable previous-state lifecycle inputs. Static mode compares initial HTML.
4. Exercise explicit-codec checkpoint admission on generated descriptors and keep required
   props strict. No source annotation may bypass that guard.
5. Run the focused browser/compiler checks after the coordinated mutation
   window. Full integration, a scoped compiler mutant, push gate and Verify
   remain separate acceptance steps. No pair credit follows from coexistence.

Source/test implementation and any results must be added below with actual
commands and outcomes. Native IDE qualification, arbitrary generic schemas,
nullable props, general state deletion and automatic portable hot codecs remain open.

## Prepared implementation and checks

`NullableState` is a source-only marker. The compiler resolves it explicitly,
checks placement, and emits `withNullableState()` on the generated descriptor.
`ClassSpec.stateLayout` contains state-specific `StateField` entries separately
from `Prop.optional`; every prior constructor retains its strict-state behavior.
Schema generation uses `optionalField`/`find` for Presence and the existing
checked codecs for its value type. The record shape expands before GA; source
constructors are retained, but record deconstruction shape is not unchanged.

The real-core compiler test prepares executable/editor checks for normal/Pure
classes, fully qualified annotations and Presence arrays/references, source
shadowing, invalid placement/types, builder collisions, mapped argument errors,
nullable previous-state signatures and strict required props. It also checks the
generated descriptor's explicit-codec admission for nullable and non-null sparse
models without invoking either codec. This assertion was updated by the test-only
`acedb6d` after the journal integration; the five original compiler tests passed
on their earlier source, and the selected integration is qualified separately below.

The `class-sparse-authoring` differential case uses actual authored normal/Pure
classes and the native React reference. Its native-ref observer only reads the
public instance's `state` and own keys. The shared assertions cover null-to-empty,
absent-to-defined, own undefined-to-null, repeated equal-shaped Pure updates,
queued partial merges, old handler snapshots, nullable previous lifecycle input
and retained DOM/instance identity. Static mode compares initial HTML. The case
is registered for both class features without adding an interaction pair.

The targeted `class-authoring-nullable-lost` compiler mutant was unrun at the
original qualification. Its selected-source campaign is recorded below. A source
review found no concrete blocker; it is not execution evidence. Full reference/JX
integration, gate and Verify remain pending.

## Executed source-specific qualification

All commands ran from the isolated worktree on source bd27ef7, with a private
Maven repository and no install into shared snapshot coordinates.

| Check | Actual outcome |
| --- | --- |
| `./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl jx-compiler -Dtest=ClassSparseAuthoringTest test` | Five tests passed, 6.725 seconds; real-core normal/Pure schemas, placement/shadowing, reference/array types, mapped arguments and editor projections |
| `./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl tests/conformance -am package` | 82 core, 17 React, 67 compiler and 15 Maven tests passed, 52.520 seconds; actual TeaVM browser and Node outputs built |
| `npm run build:bench && npm run build:bench-server && npm run build:bench-lazy`, then `BENCH_PORT=59191 node_modules/.bin/playwright test -c tests/conformance/playwright.config.js -g class-sparse-authoring --workers=1` | Eight checks passed in 22.7 seconds: reference and JX client/hydrate/lazy interactions plus static initial HTML |

Local logs are `/private/tmp/jx-class-sparse-authoring-compiler.log`,
`/private/tmp/jx-class-sparse-authoring-build.log` and
`/private/tmp/jx-class-sparse-authoring-focused.log`. These are local execution
artifacts, not committed CI evidence. The filtered run deliberately did not
overwrite either complete result table. It adds one case and no pair witness.
No independent full bench was run: the parent integration will qualify the
combined compiler/runtime stack and run the targeted mutation separately.

## Selected compiler mutation qualification — 26 November 2026

Source `a225add` passes the unmodified JX bench across client, hydration, static
HTML and lazy-route modes before either mutation is applied. The curated
`class-authoring-nullable-lost` then removes the generated `.withNullableState()`
call. It compiles successfully and **fails `class-sparse-authoring` in all four
modes**: the declared null initial state is incorrectly rejected, preventing the
client/lazy UI and failing server rendering with `Class props/state must be
non-null`. The final mutated run has 504 passing checks, four intended failures
and 112 structurally inapplicable cells across 155 case IDs. These are mutant
results, not a green conformance total.

Run in isolation:

```sh
java tools/MutateBench.java --only class-legacy-authoring-context-lost,class-authoring-nullable-lost --keep
```

The same campaign kills the preceding typed-context legacy generator mutation
with `class-legacy-authoring`: **2 killed, 0 survived, 0 unusable**, exit 0. This
section credits one nullable-authoring mutant. The tool builds actual TeaVM
client/server bundles for each candidate, deliberately skipping JVM tests while
constructing mutants; the 188 JVM qualification above remains separate.

The source/cache worktree is `/private/tmp/jx-class-authoring-mutants`. Scratch is
`/var/folders/y9/29rtk6bd2dz_xj36rw2jn6g40000gn/T/jx-mutation-14847958175598351148`.
Its final log is also retained as `/private/tmp/jx-class-authoring-final-mutant.log`.
The generator source is restored and compared byte-for-byte with the original,
but retained compiled scratch outputs still contain the final mutant. No original
source or Maven cache was changed. No fresh reference-side execution, complete
pair witness or whole-catalog mutation result is claimed. The tool's zero pair
count in this JX-only invocation reflects the intentionally absent reference
projects; the canonical pair/result tables are not overwritten.
