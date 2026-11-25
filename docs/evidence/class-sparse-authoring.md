# Explicit nullable and sparse class state authoring

R-28-03h4, 25 November 2026. **Source preparation; no tests have run for this
slice.** The runtime contracts are already represented by `withNullableState`,
`ClassSchema.optionalField` and `Presence`. This slice compiles explicit `.jx`
declarations to those APIs; it does not change native React state merging.

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
  behavior and native defaults. The existing checkpoint admission guard still
  rejects nullable aggregate or sparse state before acquisition/teardown. No
  presence-aware checkpoint codec is added.

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
4. Exercise the checkpoint guard on generated descriptors and keep required
   props strict. No source annotation may bypass that guard.
5. Run the focused browser/compiler checks after the coordinated mutation
   window. Full integration, a scoped compiler mutant, push gate and Verify
   remain separate acceptance steps. No pair credit follows from coexistence.

Source/test implementation and any results must be added below with actual
commands and outcomes. Native IDE qualification, arbitrary generic schemas,
nullable props, general state deletion and portable hot codecs remain open.

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
generated descriptor's checkpoint rejection for nullable and non-null sparse
models without invoking either codec. These checks have **not run yet**.

The `class-sparse-authoring` differential case uses actual authored normal/Pure
classes and the native React reference. Its native-ref observer only reads the
public instance's `state` and own keys. The shared assertions cover null-to-empty,
absent-to-defined, own undefined-to-null, repeated equal-shaped Pure updates,
queued partial merges, old handler snapshots, nullable previous lifecycle input
and retained DOM/instance identity. Static mode compares initial HTML. The case
is registered for both class features without adding an interaction pair.

The targeted `class-authoring-nullable-lost` compiler mutant is registered but
unrun. A source review found no concrete blocker; it is not execution evidence.
Compilation, browser checks, full integration, mutation, gate and Verify remain
pending while the separate exclusive mutation campaign owns local execution.
