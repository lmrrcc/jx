# Native class default props and typed input

R-28/R-31 work, 25 November 2026. Runtime/schema and ordinary `.jx` authoring
are implemented locally on source `7195939`. The final package passes **164 JVM
tests** (72 core, 11 React, 7 Elm, 59 compiler and 15 Maven), including three new
core contracts and five compiler tests. All **16 focused mode checks** and
**32 runtime browser checks** pass. The full bench passes **969 checks, 199
inapplicable cells, 146 cases**: client 144 reference /143 JX, hydration 108,
static HTML 114 and lazy mode 119 per side. Existing **62/6328** pair witnesses
remain unchanged. These are the original source-branch measurements; publication
integration and the subsequent mutation campaign are recorded below.
This does not close all class-component or hot-reload qualifications.

## Publication integration, 26 November

The isolated source `1c989c7` builds on the published nullable-state merge
`30742a6` (PR71), including the published native Writable consumers. It retains the
defaultProps API, raw input and clone semantics, compiler lowering and both
complete differential consumers. Seventeen capability/consumer files and all
three native mutation target regions are byte-identical to `7195939`.

The original source had been developed above the unpublished class journal.
This publication deliberately defers its checkpoint-only adapter changes:
`ClassComponentType` checkpoint-copy parameters and admission checks, the
`ClassCheckpoints` owner `overrides` argument and clone, and the checkpoint branch
of `ReactClasses.element`. The ordinary native constructor/encoder/clone path is
unchanged. The journal follow-up must restore those adapter changes before its
defaults/ref-copy regression can compile and run. This is not whole-file identity
for `ClassComponentType` or `ReactClasses`, and does not qualify class checkpoints.

After a passing baseline, the earlier eight-mutant campaign at `e296d4a`
(production `cc929bb`) killed `class-defaults-dropped`,
`class-default-null-omitted` and `class-clone-default-eager`. The first two were
detected by both default-props cases; the last was detected by `class-defaults`.
This slice owns three of those eight kills, with zero survivors or unusable
mutants. These are historical scoped mutation results, not a new campaign on the
isolated source. The unchanged native target regions and consumers were compared
explicitly. The isolated source passes **66 core, 10 React and 59 compiler JVM
checks**, including the five real-javac defaults tests. The final integrated
package, full push gate and Verify remain pending.
No new interaction-pair credit is claimed.

## Authoring

```java
@Component
public final class Counter extends ClassComponent<Counter.Props, Counter.Model, Void> {
    public record Props(String id, @OptionalProp String label, @OptionalProp int initial) {}
    public record Model(int count) {}
    public static final ClassProps<Props> defaultProps = propsInput()
            .withLabel("Counter").withInitial(4).build();
    public Counter(Props props) { super(props, new Model(props.initial())); }
    @Override public View render() {
        return <button onClick={event -> setState((pending, next) ->
                statePatch().withCount(pending.count() + 1).build())}>
            {props().label()}: {state().count()}
        </button>;
    }
}
```

`<Counter id="first" />` omits label/initial at the native input boundary;
React resolves them before the constructor sees its checked `Props` record.
`<Counter id="first" label={null} initial={0} />` supplies explicit values:
neither receives a default. The compiling repository consumer is
[`AuthoredDefaultCounter.jx`](../../tests/conformance/src/main/jx/jx/conformance/cases/AuthoredDefaultCounter.jx),
with a host exercising nested children, record forwarding, class refs and key changes.

The declaration must be one `public static final ClassProps<Props> defaultProps`
field. For this authoring form, `@OptionalProp` means omission is permitted and
requires a defined native default. A nullable field's null is defined; missing or
undefined defaults fail when the descriptor initializes. Unmarked fields remain
required by markup diagnostics. `propsInput()` creates an immutable checked
builder with `withX`, `undefinedX`, `omitX` and `build` methods. Its schema holder
does not read the outer `JX` field, so it is safe during static initialization.
Generated helper-name collisions are checked only when the helper is generated.

Existing classes without native `defaultProps`, and functional components, retain
their existing `@OptionalProp` Java-constructor convention. Compact-constructor
null coalescing also replaces an explicit null, so it is not the same operation as
native defaulting. A whole `Props` record forwarded through `{...props}` continues
to supply its actual field values. Native defaults are not inferred from an
arbitrary Java constructor body.

## Runtime boundary

`ClassProps<P>` is an immutable partial input owned by one exact `ClassSchema<P>`.
It can omit even required fields or assign own-undefined without constructing a
record. Defined values are checked immediately, including nullability, numeric
limits and reference identity. Foreign schema fields and overrides fail; repeated
assignments replace one entry rather than growing a retained history. `from`
copies a validated record's field presence. `overlay` preserves unspecified
fields and applies only explicit assignments. Reference values remain borrowed
identities, not deep copies.

`ClassComponentType.withDefaultProps` installs these values on the actual native
React constructor. It returns a distinct descriptor and retains other settings;
declare it once. Null removes defaults in this runtime API. Raw input uses
`Views.classPropsView(type, input, key, ref)`. The existing strict record factory
retains its contract. The final record still validates every field after React
resolves defaults: omitted/undefined required values without effective defaults
remain errors, never synthetic Java zero/null values. Empty input is the typed
equivalent of an empty native configuration; it does not make aggregate Java
props nullable.

`Views.cloneClassProps` preserves the initial input separately from clone
overrides. React's `createElement` applies defaults first; `cloneElement` then
preserves explicit undefined overrides on the native element until class render.
Consequently cloning an originally undefined input with no overrides retains the
already resolved native value; explicitly overriding it with undefined has a
different element-level result. Repeated copies flatten overrides without a
chain of retained views. A null override preserves props, a null replacement key
retains the key, and the explicit ref overload uses null to remove the ref.
Native React owns construction, state, reconciliation and ref commits.

The ordinary materializer preserves these two phases. The checkpoint-owner
adapter belongs to the subsequent journal integration; adding defaults is not
evidence of general state-preserving hot reload. This API exposes
immutable per-descriptor defaults, not native mutation/reassignment. Sparse
`Presence` record authoring, wider generic/indirect class authoring, arbitrary
opaque element copies, full editor qualification and these dynamic options remain
separate work. Required typed markup fields need explicit `@OptionalProp` metadata
when omission is permitted; there is no inferred default-field analysis of an
arbitrary runtime Java expression.

## Verification scope

The JVM tests exercise raw presence, nullable values, reference identity,
schema ownership, bounded immutable overrides and construction-free copies.
Real javac consumers check native and Pure descriptors, static initialization,
required props, invalid override/ref types, generated-name compatibility and
source-mapped diagnostics. The same source compiles through editor projection;
that does not establish installed IDE behavior.

Two actual React/JX cases cover absent/undefined/null/zero/false/empty values,
undefined and null defaults, constructor/derived props, reference default identity,
state and DOM retention, refs and key remounts, native element.props before class
render, source-authored defaults, children and record forwarding. They run in
client, hydration, static HTML and lazy modes. No interaction-pair credit is
claimed merely from these combinations. Curated mutations target missing native
defaults, omitted explicit null and premature clone-default resolution.

The original full result table added the two default cases plus the parent's two nullable
cases, already independently qualified in its 953-check run; no prior result
changes. That original candidate was based on the import-map/nullable/checkpoint stack,
not the separate native-Writable/component-copy candidate. Results from different
branches are not added together. Source-only independent review found no blocker
in raw-input/default/clone ordering or the core ownership contract.

Logs: `/private/tmp/jx-class-default-props-final-build.log` (41.749 seconds),
`/private/tmp/jx-class-default-props-authoring-focused.log` (26.9 seconds),
`/private/tmp/jx-class-default-props-runtime.log` (8.0 seconds), and
`/private/tmp/jx-class-default-props-full.log` (3.6 minutes). The initial runtime
package reported a Java parameter/lambda name collision; the parameter was
renamed before the successful builds. Later source checks add preservation of
ordinary application member names on classes that do not generate input builders;
the final package includes those regressions and invalid-ref source diagnostics.
Validation durations are not performance claims.

Primary contracts: [React class defaultProps](https://react.dev/reference/react/Component#static-defaultprops)
and [React cloneElement](https://react.dev/reference/react/cloneElement). The
element-level undefined distinction is verified against the pinned React 19.3.1
runtime; it is not implemented with private React fields.
