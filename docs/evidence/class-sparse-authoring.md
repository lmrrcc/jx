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
