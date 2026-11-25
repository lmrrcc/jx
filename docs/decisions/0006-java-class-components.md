# Java class components on the React runtime

Date: 24 November 2026. Status: R-28-01 merged in PR #48 on 25 November after
local verification, scoped mutation, push gate and Verify passed. R-28-02a merged
in PR #50 as `3e1db97` after the 15-step gate and both Verify checks passed;
the broader R-28 task remains incomplete.

Java classes may own React component identity through a descriptor separate from
the existing functional `ComponentType`. The browser creates an actual subclass
of `React.Component` or `React.PureComponent`; each native instance creates one
Java peer. React owns reconciliation, updater evaluation, shallow state merging,
callbacks, refs and lifecycle order. A functional wrapper retaining a Java object
does not satisfy this contract.

`ClassComponent<P,S,X>` has immutable props/state values and a typed commit
snapshot `X`. `ClassComponentType<P,S,C>` retains the concrete peer type for refs;
the snapshot type stays within the peer's lifecycle callbacks. `PureComponent`
uses React's own shallow comparison. `jx.Component` remains the annotation.

Explicit `ClassSchema<T>` field descriptors project props and state into native
objects with one property per declared field. Typed readers reconstruct values
through a supplied constructor; codecs validate scalar and opaque-reference
boundaries without raw maps, unchecked casts or reflective field access. Scalar
conversion follows the existing browser boundary, including exact-range longs,
NaN and signed zero. Nested Java values compare by identity, not record equality.
No aggregate Java props/state wrapper participates in native Pure comparison.

`StatePatch<S>` is an immutable set of typed field updates for one state schema.
Absent fields are omitted; a present nullable field may be set to null. An empty
patch remains an empty object update, whereas a null updater result means no
update. React evaluates updater functions against pending state and next props.
Callbacks run after commit. Field names, duplicates, types and schema ownership
are checked explicitly.

The peer reads current props/state from its native instance even when
`shouldComponentUpdate` skips rendering. Lifecycle presence is declared by the
descriptor. Snapshot capture occurs before DOM mutation and preserves the typed
snapshot until native `componentDidUpdate`. Ref attachment identifies the actual
Java peer with a checked decoder and clears on detachment. Constructors, rendering
and updaters remain pure; React may discard or repeat them in development or
concurrent rendering. Instance resources belong to commit lifecycle callbacks.
One bounded cache per current props/state snapshot preserves repeated accessor
identity and is released on unmount. A normal class that rejects rendering moves
the cache to its next values; Pure skips retain the same field identities by
definition. Historical aggregate identity across concurrent discard or Strict
Mode's simulated unmount is not yet qualified. Initial Java props/state are
released on binding, and the native initialization object is cleared after its
state is handed to React. The bridge always installs its own unmount cleanup;
the user's lifecycle is dispatched only when declared by the descriptor.

R-28-01 uses ordinary Java `Views` and explicit descriptors. Compiler-generated
class descriptors, markup and editor semantics belong to R-28-02. Advanced
lifecycles, context and the complete option matrix remain later slices.

Class rendering must not call `HotReload.commitNative`, which invokes a hook.
The initial implementation has separate logical render paths and explicitly
resets class state during hot replacement. No opaque Java peer crosses TeaVM
generations. A future checkpoint must observe committed state even when rendering
is skipped; a didUpdate-only checkpoint is insufficient. Functional descendant
checkpoints require distinct paths and must not share their parent's hook frame.

Acceptance uses the [Java consumer](../../tests/conformance/src/main/java/jx/conformance/cases/ClassRuntime.java)
and a native React reference: queued
increments, partial/null updates, commit snapshots/callbacks, typed instance refs,
keyed remounts, skipped-render state access, forceUpdate and native Pure shallow
comparison of scalars and nested identities. Executed checks are recorded only
after they actually pass; this decision is not evidence of parity.

Primary contracts: [React Component](https://react.dev/reference/react/Component)
and [React PureComponent](https://react.dev/reference/react/PureComponent).

## Initial local evidence and limits

On 25 November 2026 the runtime package passed 33 core, 41 compiler and 15 Maven
plugin tests. The focused `class-runtime` differential case passed all eight
projects: React/JX client, hydration, static HTML and lazy route. The interactive
assertions run in client, hydration and lazy modes; static mode checks initial
HTML only. The case also proves null class output without unmounting, current
instance props/state after a skipped render and stable current accessor identity.
That focused run did not establish full conformance or pair evidence by itself.

After inheriting the scalar prerequisite and Promise observer repair, the package
passes 34 core, 41 compiler and 15 Maven-plugin tests. The complete local bench
passes 825 checks with 151 inapplicable cells across 122 cases after integration
over SSR merge `7759933`: client 120 React / 119 JX, hydration 92 per side,
static HTML 98 per side and lazy route 103 per side.
The additional [keyed class consumer](../../tests/conformance/src/main/java/jx/conformance/cases/ClassKeyed.java)
proves two already mounted Pure peers retain their real class refs, DOM nodes and
state through both reorder directions without rendering equal projected props.
Both class cases pass all eight projects. Two explicit interaction witnesses add
evidence for PureComponent with keys and instance refs. Activating PureComponent
increases the current universe to 105 features / 5,460 pairs, of which 54 have
complete local evidence. It does not shrink the eventual 118-feature scope.
The unmutated scratch baseline passed, then all three targeted mutants were
killed: `class-pure-renders` by `class-keyed` and `class-runtime`, and
`class-snapshot-lost` / `class-ref-detach-lost` by `class-runtime`. The result is
3 killed, 0 survived, 0 unusable. This scoped run does not replace the historical
100-entry full campaign; that runtime slice's catalog contains 115 entries.
The local push gate passed all 14 steps, and both Verify checks passed on tested
head `b8657cb`: [push](https://github.com/lmrrcc/jx/actions/runs/45560759038) and
[PR](https://github.com/lmrrcc/jx/actions/runs/45560907924).
[PR #48](https://github.com/lmrrcc/jx/pull/48) merged as `ccfcb2d`.

The first run exposed an existing signed-zero defect in the general scalar
bridge. A separate prerequisite reproduces three independent hook/dependency/
context failures and repairs the native scalar path; that prerequisite landed in
PR #46 as `03d9190`, before this slice. Its focused checks do not establish full bridge parity.

This slice requires non-null initial props/state and applies one conservative
reserved-name rule to both schemas, including React's props-only `key`/`ref`
names. Native class rendering may return null; the existing functional
`ComponentType.render` null restriction is a separate unresolved contract.
Direct null patches are rejected; functional updaters returning null are supported.
The consumers prove keyed remount, an unaffected Pure sibling and keyed reorder.
Advanced lifecycle options, class context,
compiler-generated schemas, mixed numeric wrapper types and class checkpoints
remain unqualified.

## R-28-02a authoring decision — 25 November 2026

The first authoring slice accepts `@Component` on a concrete, non-generic Java
class directly extending `ClassComponent<Counter.Props, Counter.Model, Snapshot>`
or `PureComponent` with the same type arguments. The author declares public,
non-generic nested records for `Props` and the selected state type, a public
constructor accepting Props, and a public instance `render()` containing `.jx`
markup. The constructor initializes state through ordinary `super(props, model)`;
no injected fields are required in the constructor.

The compiler generates the native class `JX` descriptor, private typed field
schemas, and an immutable `statePatch().withCount(value).build()` builder whose setter
methods follow the state record. Schema fields live in a private lazy holder so
an authored static initializer may call the patch builder before the generated
JX declaration is reached. No map, reflection or unchecked decoder is introduced.
Instance lifecycle declarations enable only the already implemented callbacks.
Snapshot without an update lifecycle, unsupported lifecycle names and Pure's
custom should-update policy receive explicit diagnostics.

This slice supports int, long, float, double, boolean, String and corresponding
boxed values, plus reifiable reference/array types. Reference fields are nullable;
primitive fields are not. Parameterized field types, byte/short/char fields,
generic class/state declarations and indirect class inheritance remain explicit
authoring limits. The lower-level descriptor API is still available for schemas
outside this generated subset. Reserved framework field and generated member
names are rejected at source locations.

Fluent setters use `with` followed by an uppercased first field character, leaving
the remaining spelling unchanged. This avoids Object-method signature collisions;
two state fields mapping to the same setter name are rejected before generation.

Class tags construct typed Props and pass optional key and `ClassRef<Counter>`
separately to `Views.component`. Functional component tags keep their current ref
as an ordinary Props field. The shared native-Java editor projection emits the
same class members and type contexts; projection/javac tests do not qualify the
complete IDE experience. That editor acceptance remains R-28-02b. Class hot
replacement still resets state, and this syntax slice adds no lifecycle parity.

Integrated validation after rebase onto PR #48's merge `ccfcb2d`: 34 core,
48 compiler and 15 Maven-plugin package tests; 10 LSP protocol tests; the authored
class consumer passes all eight React/JX projects. The complete bench passes
833 checks with 151 skipped across 123 cases: client 121 React / 120 JX, hydrate
93 per side, static 99 per side and lazy 104 per side. The recorded case table
adds only `class-authoring`; interaction evidence stays 54 / 5,460. The generated
`class-authoring-snapshot-lost` mutant was killed by `class-authoring` after the
unmutated scratch baseline passed: 1 killed, 0 survived, 0 unusable. The catalog
has 116 entries; the historical full campaign still covers 100. Native IDE
qualification remains pending. PR #50 passed the 15-step local gate in 278 seconds
and [Verify](https://github.com/lmrrcc/jx/actions/runs/45565741808) before merging.

## R-28-03a native context — 25 November 2026

`ClassComponentType.normalWithContext` and `pureWithContext` add one typed
`Context<T>` to an explicit descriptor. Their factory receives the initial props
and a `Supplier<T>` that reads the native instance's current context. The reader
is available in the constructor and remains usable in render, lifecycle callbacks
and browser events. The renderer installs the actual React class `contextType`
and passes React's constructor context through its normal base constructor.
No hook, context subscription or functional wrapper implements class context.

For example, an explicit descriptor may use `(props, theme) -> new Panel(props,
theme)` as its factory. The peer stores that reader and calls `theme.get()` where
a React class reads `this.context`. A missing provider uses the declared default;
an explicit null provider stays null. Provider Java references retain their
identity through the existing context wrapper. React schedules context changes,
including those bypassing `PureComponent` or a false `shouldComponentUpdate`.

The reader uses the same owner as the peer's existing props/state host. Retaining
the Java peer or its reader intentionally retains the same native class instance,
including its last context after unmount. This slice introduces neither a new
instance registry nor a disposal promise. React can invoke layout cleanup during
Strict Mode checks and when Activity hides a class without destroying its state;
the bridge therefore adds no unmounted flag and keeps hidden instance updates
available. Context remains readable during `componentWillUnmount` itself.

The [Java consumer](../../tests/conformance/src/main/java/jx/conformance/cases/ClassContextCase.java)
and native React reference prove constructor/default/nested/null context,
reference identity, skipped state followed by a context-driven render, Pure
bailouts, real DOM identity, and a retained instance updated while Activity is
hidden. The latter is retained explicitly by the hide event: a parent mount
effect need not observe a ref inside an Activity that has not yet hydrated.
The consumer also checks context during cleanup and after intentional retention,
with balanced mount/cleanup under Strict Mode and Activity reappearance.

Integrated over utility merge `8fdbed0`, package verification passed 37 core,
48 compiler and 15 Maven-plugin tests. The complete bench passes **855 checks,
169 skipped, across 128 cases**: client 126 React / 125 JX, hydrate 95 per side,
static 101 and lazy 106. The context consumer and three existing class cases
pass all eight projects each. Static compares initial HTML only; the interactive
modes exercise the assertions. Case evidence adds only `class-context`, and all
54 existing pair witnesses remain green among 6,328 active candidates.

The scoped `class-context-captured` mutant was killed by this consumer in client,
hydrate and lazy modes after a passing unmutated scratch baseline: 1 killed,
0 survived, 0 unusable. Its runtime/consumer/spec source at `6c3aa63` is identical
to rebased source `d897468`; scratch `jx-mutation-6746600491109191949` is retained.
The catalog now has 121 entries; the historical full campaign still covers 100.
These checks are Chrome-only and do not qualify native IDEs. The final push gate
and Verify remain pending.

Derived state from props, derived error state and committed error information are
separate R-28-03b/03c slices. Compiler-generated context declarations and a typed
third next-context argument for should-update remain R-28-03d; they are not
provided by these factories. Existing `.jx` class syntax and class hot-reload
reset behavior remain unchanged.
