# Nullable class state and property presence

R-28-03h1, 25 November 2026. Sources `50fde0a` and `1ff6ac6` on `36aa9dd`; package and focused
class regressions pass. Integration, full bench, mutations and Verify are pending.
This extends the manual typed class runtime. Compiler-generated sparse `.jx`
records, nullable props and committed hot checkpoints remain separate work.

## Contract

`ClassComponentType.withNullableState()` creates a new native type descriptor
that permits null aggregate state. Existing descriptors and schemas remain strict.
The constructor may supply null, and the renderer validates the opt-in before
mounting. Props remain non-null. All descriptor modifiers preserve the opt-in.

`Presence<V>` has three immutable alternatives: `Absent<V>`, `Undefined<V>` and
`Present<V>`. `Present(null)` is distinct from the other two; it is valid only when
the field's `ValueType` permits null. A record using `optionalField` stores this
presence directly. `Reader.find` preserves it, while existing `Reader.get` fails
on absence/undefined. Required fields reject both instead of inventing primitive
defaults. `ClassSchema.validate(null)` still fails: aggregate null belongs to the
descriptor's state policy and is never a nullable props schema.

Every declared source field is read once in schema order and validated before the
record constructor is called. Reader access uses an immutable schema-known snapshot.
A constructor cannot hide an invalid or missing required source by ignoring Reader
and returning a valid default record. This strengthens the old lazy reader behavior;
constructors relying on that bypass now fail rather than synthesize a valid input.

```java
record Model(Presence<Integer> count, Presence<String> note) {}
ClassSchema.Field<Model, Integer> count =
    ClassSchema.optionalField("count", ClassSchema.INTEGER, Model::count);
ClassSchema.Field<Model, String> note =
    ClassSchema.optionalField("note", ClassSchema.STRING.nullable(), Model::note);
ClassSchema<Model> schema = new ClassSchema<>(List.of(count, note),
    reader -> new Model(reader.find(count), reader.find(note)));
StatePatch<Model> first = schema.patch().set(count, 1);
StatePatch<Model> ownUndefined = schema.patch().undefined(note);
StatePatch<Model> explicitNull = schema.patch().set(note, null);
```

Only native React merges and schedules state. A null direct patch or updater
result does not change pending state; an empty patch merges to an object even
when state was null. Unlisted patch fields are preserved. `undefined(field)`
assigns an own undefined property and cannot delete a field. It accepts optional
fields only. A null derived patch leaves state unchanged rather than resetting it.
Partial state must remain representable by the declared schema: after a merge,
every required field still has to exist with a valid defined value.

`StatePatch.Entry` retains its two-argument constructor, `field()` and `value()`.
Its new third record component `undefined()` distinguishes explicit undefined
from a defined null; the canonical constructor rejects an undefined assignment
with a non-null Java value or a required field. Record component enumeration and
the generated `toString()` shape change. No record serialization compatibility is
promised. Existing strict field and patch APIs preserve their meaning.

## Consumers and evidence

[ClassNullableState](../../tests/conformance/src/main/java/jx/conformance/cases/ClassNullableState.java)
is a Java consumer of all new contracts. It pairs with native React classes in
two shared browser cases. A test-only public React ref observer records native
class instances and reads `Object.keys`/own-property values; it neither reads
Fiber nor updates state. Ordinary class state and lifecycle logic remain Java.

- `class-nullable-state`: null queues and callbacks, null to empty object to
  partial fields to own undefined to explicit null, sequential updater inputs,
  native Pure comparisons, SCU-false current-state reads, null previous-state
  snapshots, retained DOM identity and keyed reset to null.
- `class-nullable-derived`: initial and pending null in derivation, React's
  development warning, derivation after queued updates and before Pure/SCU,
  partial/undefined/null-result merges, and an error boundary deriving sparse
  fallback state from null.
- Seven JVM tests cover construction, immutable modifier composition, required-field
  strictness, field/schema ownership, explicit presence, patch ordering/duplicates,
  reference identity and callback failure propagation.

Package passes **63 core, 54 compiler and 15 Maven-plugin tests**. The class
regression passes **96/96 checks**: twelve cases in eight React/JX projects,
including the two new cases. Client, hydration and lazy projects run the
interactions; static mode compares initial HTML only. The existing null-patch,
next-context, custom Pure policy, context/Activity, class authoring, error lifecycle,
derived props, keyed class and class-runtime cases remain green.

The first package identified the old direct-constructor null rejection assertion;
it now checks the same default rejection at the explicit descriptor boundary.
The first derived browser attempt rejected both sides' console warning format:
the harness retains printf placeholders and their arguments. The final assertion
checks exactly two native warnings, their class names and null-state content
without assuming interpolation. No runtime behavior or warning was suppressed.

Source review reproduced a required-field bypass in the record factory: one new
JVM regression failed because the factory could ignore absent/undefined/invalid
source values. Prevalidation and single-read snapshotting fix it. The full package
passes, and the twelve field/nullable JVM tests pass again after strengthening
foreign-field membership assertions with otherwise valid source values. The
derived consumer requires exactly one child-error report beyond its two expected
initial warnings, as well as exactly one committed catch callback.

The two curated mutants, `class-null-state-emptified` and
`class-own-undefined-deleted`, were subsequently killed after a passing baseline
in the first eight-mutant campaign on `e296d4a` (production `cc929bb`), with zero
survivors or unusable mutations. Both new nullable cases detect both mutations;
`class-defaults` additionally detects loss of own undefined in that larger
integration. This is the nullable slice's quota of two, not a full-catalog run.
Log: `/private/tmp/jx-react-options-mutations-first.log`; retained scratch suffix
`jx-mutation-3306759765052529558`.

Publication preparation on 26 November retains the same eleven nullable
core/runtime/consumer/spec files as `80f2e57`, rebased onto the native Writable
candidate `02d2ddf` as source `266b05f`. The two all-mode result rows are carried
from the already executed `e296d4a` integration as expected results for the
publication gate; a new combined run on this exact publication tree is still
pending. No new interaction pairs, gate or Verify completion is claimed. Real React
development mode may repeat pure evaluation; assertions distinguish committed
callbacks and observable skipped rendering from an exact speculative render count.

Logs: `/private/tmp/jx-null-state-source-validation-red.log`,
`/private/tmp/jx-class-null-state-final-build.log`,
`/private/tmp/jx-class-null-state-field-tests.log` and
`/private/tmp/jx-class-null-state-final-regression.log`. `CheckBaseline`,
`CheckAgentSetup` and `git diff --check` pass. Reproduce:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl tests/conformance -am package
npm run build:bench
npm run build:bench-server
npm run build:bench-lazy
BENCH_PORT=59184 node_modules/.bin/playwright test -c tests/conformance/playwright.config.js -g 'class-.*:' --workers=1
```

## Remaining limits

No automatic nullable/sparse record inference or `.jx` class declaration sugar is
offered. Null initial props, arbitrary dynamic keys and general object state are
not supplied by this explicitly declared schema. `Presence` does not serialize
objects, encode native undefined in JSON, or make retained values safe across
TeaVM generations. A future checkpoint codec must preserve null aggregates,
absent fields, own undefined and nullable defined values independently; the
unresolved hidden/SCU-false commit-proof requirements are unchanged.

The native merge and derivation behavior follows the pinned React implementation
in `react-dom-client.development.js` (`getStateFromUpdate`,
`applyDerivedStateFromProps` and class initialization) and the public
[Component reference](https://react.dev/reference/react/Component). Null initial
state with `getDerivedStateFromProps` emits React's warning; the fixture checks
that warning rather than suppressing it or changing the initial state shape.
