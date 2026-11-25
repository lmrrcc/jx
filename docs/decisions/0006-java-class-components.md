# Java class components on the React runtime

Date: 24 November 2026. Status: implementation decision for R-28-01; verification
pending. The broader R-28 task remains incomplete.

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
Full conformance, scoped mutation, push-gate and Verify checks are pending. No
class feature or pair receives completion credit from this focused run alone.

The first run exposed an existing signed-zero defect in the general scalar
bridge. A separate prerequisite reproduces three independent hook/dependency/
context failures and repairs the native scalar path; that prerequisite must land
before this slice. Its focused checks do not establish full bridge parity.

This slice requires non-null initial props/state and applies one conservative
reserved-name rule to both schemas, including React's props-only `key`/`ref`
names. Native class rendering may return null; the existing functional
`ComponentType.render` null restriction is a separate unresolved contract.
The consumer proves keyed remount and an unaffected Pure sibling, not reordering
two already mounted class peers. Advanced lifecycle options, class context,
compiler-generated schemas, mixed numeric wrapper types and class checkpoints
remain unqualified.
