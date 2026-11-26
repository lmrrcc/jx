# Java class components on the React runtime

The later [R-28-04a checkpoint journal evidence](../evidence/class-checkpoint-journal.md)
qualifies a conservative opt-in foundation for visible committed state only.
It does not supersede the full hidden/concurrent class checkpoint requirement.

The later [explicit nullable/Presence checkpoint slice](../evidence/class-checkpoint-presence.md)
qualifies those state shapes through declared typed codecs and exact descriptor
validation, including independent-generation restoration over a different
constructor value. Pure/hidden behavior and candidate decoder preflight remain
separate R-28-04 requirements; readiness failure after teardown is not preflight.

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
These checks are Chrome-only and do not qualify native IDEs. [PR #53](https://github.com/lmrrcc/jx/pull/53)
merged as `198c0b8` after its 15-step local gate passed in 308 seconds and
[push Verify](https://github.com/lmrrcc/jx/actions/runs/45598507275) and
[PR Verify](https://github.com/lmrrcc/jx/actions/runs/45598573765) passed on `30a8911`.

Derived state from props, derived error state and committed error information are
separate R-28-03b/03c slices. Compiler-generated context declarations and a typed
third next-context argument for should-update remain R-28-03d; they are not
provided by these factories. Existing `.jx` class syntax and class hot-reload
reset behavior remain unchanged.

## R-28-03b derived state from props — 25 November 2026

`descriptor.withDerivedStateFromProps((nextProps, pendingState) -> patch)` returns
a new immutable descriptor. Declare its result once, as for any component type:
the new descriptor has its own native React type identity. The original remains
unchanged. Existing factory, context, Pure and lifecycle settings are preserved;
applying the method again replaces the derivation in the returned descriptor.

The callback is static component behavior: it has no peer instance, must remain
pure and must not call hooks, schedule updates or perform effects. React invokes
the actual native `Type.getDerivedStateFromProps` after construction and when it
processes subsequent updates. Queued state updaters run before derivation, and
native should-update/Pure policy runs afterward. React can repeat or discard
these calculations. This bridge does not emulate that ordering in Java.

The callback consumes checked props and state values and returns a `StatePatch`
owned by the descriptor's state schema. A null result preserves pending state;
an empty patch remains an empty object merge. Omitted fields survive, while a
present field declared nullable can explicitly become null. A foreign-schema
patch fails explicitly. Callback failures propagate unchanged through the Java
dispatcher and through the native callback; no catch produces a successful value.
The public contract requires non-null initial state, matching the existing class
slice. It does not yet add nullable initial state or compiler-generated derived
lifecycle declarations.

The [Java consumer](../../tests/conformance/src/main/java/jx/conformance/cases/ClassDerivedProps.java)
and native React reference test constructor state followed by derived initial
state, queued local updates, prop-triggered partial resets, explicit null fields,
derivation after local updates, false SCU with updated current state, forceUpdate,
and Pure bailouts for null and empty derivation patches. Real DOM handles survive
these updates, and commit snapshots observe the previous DOM. No exact count is
assumed for repeated render-phase invocations under Strict Mode.

Integrated over functional merge `adcf22b`, package verification passes 42 core,
48 compiler and 15 Maven-plugin tests. The complete bench passes **879 checks,
169 skipped, across 131 cases**: client 129 React / 128 JX, hydrate 98 per side,
static 104 and lazy 109. The new case and four prior class cases pass all eight
projects each. Client, hydration and lazy execute interactions; static compares
initial HTML only. JVM contracts also check schema rejection and unchanged
callback failure identity. Browser error-boundary qualification for derivation
failures remains part of R-28-03c.

Recorded case evidence adds only `class-derived-props`; all 54 existing pair
witnesses remain green among 6,328 active candidates, with no new pair credit.
This is Chrome-only evidence. The scoped `class-derived-props-dropped` campaign
passed its unmutated baseline, then killed the mutation in all four JX projects:
one killed, zero survived and zero unusable. It ran at `7c78a3d`; the derived
runtime and fixture source is byte-identical after integration onto PR #54.
Scratch evidence is `jx-mutation-2026455379713343602`. The catalog now has 124
entries, including the two functional-contract mutants, while the historical
full campaign still covers 100. PR [#55](https://github.com/lmrrcc/jx/pull/55)
merged as `84eb8cd` after the 15-step gate passed in 377 seconds and both
[PR Verify](https://github.com/lmrrcc/jx/actions/runs/45611633361) and
[push Verify](https://github.com/lmrrcc/jx/actions/runs/45610807085) passed on `021bc02`.

## R-28-03c native error lifecycle — 25 November 2026

`descriptor.withDerivedStateFromError(error -> patch)` adds React's static native
`getDerivedStateFromError`, preserving the descriptor's existing context, prop
derivation, factory and lifecycle settings. It returns a new descriptor identity
which applications declare once. The callback is pure render-phase work and may
be repeated or discarded. Its schema-owned partial patch is merged by React;
null means no derived change. Foreign-schema patches fail explicitly. Logging
and other reporting effects belong in the committed callback instead.

Declaring `Lifecycle.CATCH` enables the Java peer's protected
`componentDidCatch(Throwable, ComponentErrorInfo)`. React invokes the native
prototype method at its own commit boundary. `ComponentErrorInfo.componentStack`
is React's original string, including an empty string when React supplies one;
null is not accepted. This is not the Java exception stack or a mapped Java
source trace, and no complete Java owner-chain guarantee is made.

Compiled Java exceptions retain their original Throwable identity through both
error derivation and reporting. Foreign JavaScript throws become a `ReactError`
with an explicit kind and the original borrowed `JSObject` value. In particular,
null and undefined remain different kinds. Foreign wrapper identity is not
promised across callbacks: compare the native value with JavaScript semantics
when identity matters. No global cache of primitive throws or instance registry
is introduced. Retaining the exception can retain the original native graph.

Foreign message/stack diagnostics read only own string-valued data descriptors.
They do not invoke arbitrary accessors or stringify foreign objects. Proxies may
intercept descriptor inspection; inaccessible descriptors are ignored. A stack
implemented by an accessor can therefore produce a null `nativeStack()` even
when direct JavaScript property access would return text. The original value
remains available. This bounded projection is explicit; it is not a promise of
native Error stack equivalence or source-map reconstruction.

The [Java consumer](../../tests/conformance/src/main/java/jx/conformance/cases/ClassErrorLifecycle.java)
uses actual nested React class boundaries. It checks descendant render,
derived-props and commit failures, committed fallback DOM, original Java failure
identity, foreign Error/object/null/undefined identity and kind, and nonempty
React component-stack information. Foreign failures originate in a real bound
JavaScript React child, so this evidence does not qualify throwing arbitrary
JavaScript primitives through a compiled Java exception frame. A Java-only check
compares the foreign stack projection with the actual native data descriptor,
including an object with a real captured stack string; no synthetic React wrapper
API is claimed by the reference fixture.

Reset preserves the boundary's local state while recreating the failed child;
key remount resets the boundary too. Real DOM handles establish those differences
and preserve an unaffected sibling. A boundary's own render failure reaches its
parent, and event-handler errors remain uncaught by the boundaries. Recovery
uses a separate failed flag, because null is itself a valid native thrown value.

The JVM contracts validate both orders of descriptor composition, schema and
nullable-patch rules, original Throwable/reporting identity, callback failure
propagation and verbatim/empty component-stack strings. Integration onto PR #55's
tested tree passes 47 core, 48 compiler and 15 Maven-plugin tests. The complete
bench passes **887 checks, 169 skipped, across 132 cases**: client 130 React / 129
JX, hydrate 99, static 105 and lazy 110 per side. All 48 class cells pass. A
subsequent focused run passes all eight projects with tightened assertions:
exactly eight expected caught-error console reports in order, one uncaught event
error, and no unrelated reports, including React's native console format for
null and undefined throws.

Client, hydration and lazy execute the interactions; static compares initial HTML
only. Recorded case evidence adds only `class-error-lifecycle`, with all 54 pair
witnesses unchanged among 6,328 active pairs. This is Chrome-only evidence.
Compiler error lifecycle declarations, server-render error handling, production
diagnostics and additional foreign kinds remain unqualified. The scoped
`class-error-report-dropped` campaign passed its unmutated baseline, then killed
one mutation, with zero survived/unusable. Only `class-error-lifecycle` fails, in
client, hydration and lazy, because the committed catch report is absent; static
correctly still passes. The campaign used commit `c27f881`; scratch evidence is
`jx-mutation-1028723938717671193`.

The final integration onto Forms merge `cc87f95` preserves the qualified class
runtime, JVM tests, Java/reference consumers and strengthened shared spec byte
for byte. Both recorded result tables are unchanged. The final push gate passed
all 15 steps in 342 seconds, including the 887-check bench. PR
[#57](https://github.com/lmrrcc/jx/pull/57) merged as `6ebfc76` after both
[push Verify](https://github.com/lmrrcc/jx/actions/runs/45635601496) and
[PR Verify](https://github.com/lmrrcc/jx/actions/runs/45635742220) passed on
`baa2da5`. The catalog at that merge includes 127 mutants, including the
two merged Forms mutants; the historical full campaign remains 100.

## R-28-03d direct lifecycle declarations — 25 November 2026

Native classes authored in `.jx` may declare their accepted lifecycle APIs with
ordinary Java members. The compiler connects those declarations to the existing
typed native descriptor; React still owns invocation and scheduling.

```java
@Component
public final class Panel extends ClassComponent<Panel.Props, Panel.Model, Void> {
    public record Props(int revision) {}
    public record Model(int count, Throwable error) {}
    public static final Context<String> contextType = new Context<>("day");
    private final Supplier<String> theme;

    public Panel(Props props, Supplier<String> theme) {
        super(props, new Model(props.revision(), null));
        this.theme = theme;
    }

    public static StatePatch<Model> getDerivedStateFromProps(Props next, Model pending) {
        return next.revision() == pending.count() ? null
                : statePatch().withCount(next.revision()).build();
    }

    public static StatePatch<Model> getDerivedStateFromError(Throwable error) {
        return statePatch().withError(error).build();
    }

    @Override protected void componentDidCatch(Throwable error, ComponentErrorInfo info) {
        report(error, info.componentStack());
    }

    @Override public View render() {
        return <p>{theme.get()}: {state().count()}</p>;
    }
}
```

The excerpt omits the application's `report` implementation and imports from
`jx` and `java.util.function.Supplier`. A runnable composed consumer is
[AuthoredLifecycle.jx](../../tests/conformance/src/main/jx/jx/conformance/cases/AuthoredLifecycle.jx).

`contextType` must be one initialized `public static final Context<T>` field,
with a concrete value type, paired with a public `(Props, Supplier<T>)`
constructor. Without it, the original `(Props)` constructor remains the
contract. The reader has the ownership, null and current-value semantics of
`normalWithContext` / `pureWithContext`; no hidden instance field or subscription
is introduced. Nested generic context values are Java types, independent of the
bounded props/state field schema.

The derivation methods are public, static, non-generic methods with the exact
declared props/state types and a `StatePatch<State>` result. Error derivation
takes `Throwable`; `componentDidCatch` is a public/protected void override taking
`Throwable, ComponentErrorInfo`. Omitted fields, explicit null fields and null
derivation results retain the existing runtime meaning. The generated descriptor
chains both immutable modifiers when both methods are present. Its initialization
follows authored static declarations, with schemas in the existing lazy holder.
Checked constructor/callback exceptions remain ordinary javac errors.

The parser reports malformed declarations at the authored member, respecting
explicit imports and local shadow types. Java body attribution and editor
projection remain the same compiler path; generated descriptor text is not an
editable source region. This does not qualify native IDE rename, inspections or
completion in running IDE distributions: that remains R-28-02b.

The next-context argument of `shouldComponentUpdate` is a separate R-28-03e
slice. Its proposed additive typed descriptor policy receives the Java peer,
next props, next state and the value of the descriptor's matching `Context<T>`.
It must preserve the existing two-argument override and specify precedence when
both are configured. Compiler overload support follows runtime evidence. This
slice does not change class generic parameters, null initial state, direct null
updates or class hot checkpoints.

Integrated package verification passes 47 core, 52 compiler, 15 Maven-plugin and 10 LSP
tests. Real-core javac consumers cover normal and Pure factories, both chained
derivations, typed context readers, import shadowing, fully qualified types and
checked constructor/callback exceptions. Both generated code and editor
projections type-check; invalid patch arguments map back to the authored method
body, and the LSP retains class tag/ref metadata and editable Java mappings.

The new browser consumer passes all eight focused projects with exactly one
expected caught-error report and no unrelated console/page errors. The complete
integrated bench then passes **895 checks, 169 skipped, across 133 cases**:
client 131 React / 130 JX, hydrate 100, static 106 and lazy 111 per side. All seven
class cases pass eight projects each. Context changes bypass Pure comparison without resetting local state;
derived props update only their named fields; error derivation and committed
reporting preserve the original Java failure. The initial test incorrectly
expected the boundary's original host section to survive a descendant error.
Both actual React and JX recreated that rendered subtree. The final test instead
checks pre-error DOM stability, old-subtree detachment, independently edited local
state and the actual class-ref identity retained across recovery, and a fresh
child instance. Production runtime code was unchanged.

Static mode compares initial HTML only. This is Chrome evidence on the integrated
class-error branch. Recorded case evidence adds only `class-lifecycle-authoring`;
all 54 pair witnesses remain green among 6,328 active candidates, with no new pair
credit. After the source-preserving rebase onto PR #57's Forms-integrated head,
the catalog has 128 mutants; the historical full campaign remains 100.
The compiler mutant `class-authoring-error-derive-lost` was killed by
`class-lifecycle-authoring` after the unmutated baseline passed. It ran in the
five-mutant campaign on combined candidate `e63ea76`: five killed, zero survived
or unusable, with compiler, LSP, authored consumers and shared spec byte-identical
to this slice. The other four mutations qualify their own preload/sink/act work;
they add no class evidence. Scratch evidence is
`jx-mutation-7730846870036546774`. Repeat only this mutation in an exclusive
build/browser window with
`java tools/MutateBench.java --only class-authoring-error-derive-lost --keep`.
The final push gate and Verify remain pending.

## R-28-03e typed next-context update policy — 25 November 2026

`withContextShouldUpdate(context, (peer, nextProps, nextState, nextContext) -> ...)`
adds React's third should-update argument without changing the generic parameters
of `ClassComponent`. The context must be the descriptor's declared context by
identity; a new descriptor is returned, preserving its factory, schemas,
derivations and lifecycle settings. Applications declare the resulting descriptor
once. The modifier enables native should-update dispatch automatically and takes
precedence over a registered two-argument peer override. Reapplying it replaces
the policy only on the new descriptor.

The adapter passes the callback's actual pending native context through checked
descriptor access. It does not replace the instance's current context or add a
subscription, global registry or separate peer owner. Props and state are checked
against the descriptor schemas before dispatch. Pending context may be null; it
is a value for that invocation, not the constructor's live reader. Java exceptions
from the reader or policy propagate unchanged through the Java dispatcher.

[React's public contract](https://react.dev/reference/react/Component#shouldcomponentupdate)
separates current instance values from the pending arguments and bypasses the
policy for initial rendering and forceUpdate. Inspection of the installed React
DOM 19.3.1 implementation (`checkShouldComponentUpdate` and the class update path)
also shows a changed context dependency can force a render after a false result.
The fixture compares this behavior with an actual native three-argument React
class; no manual update scheduling is substituted.

After the explicit descriptor consumer passed all eight projects, compiler support
was added for an ordinary public/protected Java overload:

```java
protected boolean shouldComponentUpdate(Props next, Model pending, Theme nextContext) {
    return next.revision() != props().revision()
        || pending.count() != state().count()
        || nextContext != theme.get();
}
```

This is an overload, without `@Override`: the Java base retains its existing
two-argument method. Its third parameter must match the declared `contextType`
value type. When both forms are present, the generated descriptor selects the
three-argument overload; ordinary Java calls to either method keep normal Java
resolution. The compiler generates the same typed policy modifier exercised by
the runtime consumer. Authored method bodies remain mapped Java in editor
projections, with no hidden context field.

Runtime package and eight-project evidence preceded compiler implementation. The
final package passes 47 core, 53 compiler, 15 Maven-plugin and 10 LSP tests. All
64 focused class projects pass, including the combined authored/runtime consumer
and seven existing class cases. Both peers observe current versus pending
props/state/context and original context identity. The fixture also covers
default/provider/null constructor values and provider changes to null.
Queued changes do not eagerly mutate current values; false policies skip local
updates while native provider changes still commit. Initial render and forceUpdate
bypass the policy. Actual DOM handles remain stable, and the unused legacy
override is never invoked. Real-core javac consumers prove either overload form
and their coexistence, source-mapped editing and invalid signature diagnostics.

Integration on responsive-preload merge `a805810`, over merged lifecycle
authoring `338872c`, passes 51 core, 2 React-option, 53 compiler, 15 Maven-plugin
and 10 LSP tests. The complete bench passes
**911 checks, 169 skipped, across 135 cases**: client 133 React / 132 JX,
hydrate 102, static 108 and lazy 113 per side. All 64 class cells pass; the
recorded table adds `class-next-context` and pair evidence remains 54/6328.
Runtime/compiler consumers and the shared spec are unchanged from the focused
proof. `CheckBaseline` and `git diff --check` pass. The wrong-context and
omitted-authored-policy mutants were both killed after an independent unmutated
baseline passed: **2 killed, 0 survived, 0 unusable**. They ran within the seven
mutants on combined candidate `f49b48f` (source `193181f`), whose complete bench
passed 931 checks/181 skipped/139 cases. Both failures include
`class-next-context`; the later Pure-policy consumer also detects them. The
mutated runtime/compiler files and this slice's consumer/spec files are
byte-identical on the candidate and the rebased publication branch. The other
five mutations belong to their own slices; the historical complete campaign
remains 100. Scratch evidence is `jx-mutation-2296619748002841958`.
Repeat just this slice exclusively with
`java tools/MutateBench.java --only class-next-context-current,class-authored-context-policy-lost --keep`.
Static projects compare initial HTML only. PR
[#60](https://github.com/lmrrcc/jx/pull/60) merged as `3815001` after the final
15-step gate passed in 373 seconds and both
[push Verify](https://github.com/lmrrcc/jx/actions/runs/45665909511) and
[PR Verify](https://github.com/lmrrcc/jx/actions/runs/45666011061) passed on `251aaa7`.
Native IDE qualification remains pending.

Remaining options are explicit, not accepted scope reductions: custom policies
on PureComponent are currently rejected, although React can call such a method
with a development warning; non-null initial props/state and direct patches,
bounded class record authoring, legacy/UNSAFE lifecycle declarations, native IDE
qualification and class hot checkpoints remain open under R-28-03f/02b/04.
The option audit must distinguish React's deprecated lifecycle behavior from
APIs removed by the pinned React version. Its inventory includes
`componentWillMount`, `componentWillReceiveProps`, `componentWillUpdate`, their
`UNSAFE_` forms, legacy context members and class `defaultProps`; none gains
support merely because the current parser reports an explicit diagnostic.

## R-28-03f nullable direct state patches — 25 November 2026

`setState(StatePatch<S>, Runnable)` accepts a null patch as a queued no-op. React
still owns its callback and runs it at commit, including when no render is needed.
The overload without a callback has the same nullable patch contract. The
functional overload still requires a non-null updater, whose result may be null.
An empty patch remains a native object merge; a nullable field explicitly set to
null remains a field update. Neither is interchangeable with a null patch.

Java cannot resolve a bare `setState(null)` between the existing patch and
functional overloads. Use a typed `StatePatch<Model>` variable or
`setState((StatePatch<Model>) null, callback)`. This additive behavior keeps both
overloads and does not weaken schema checks or permit null initial props/state.
The ordinary authored [NullPatchCounter.jx](../../tests/conformance/src/main/jx/jx/conformance/cases/NullPatchCounter.jx)
exercises both spellings, functional null results, empty merges, mixed queues and
explicit nullable field updates.

Before the runtime change, actual React passed client, hydration and lazy tests,
while JX failed all three with a Java exception and no committed callback. Both
static initial HTML checks passed. After removing only the direct-patch guard,
all eight focused checks pass. Assertions cover unchanged state and real DOM
identity, no render/should-update invocation for isolated null patches, callbacks
after handlers, and the ordered callbacks of a mixed null/increment queue.

The package passes 50 core, 53 compiler, 15 Maven-plugin and 10 LSP tests, including
three new JVM contracts for deferred evaluation, callback ownership, unchanged
failure identity and preserved construction/null-updater guards. All nine class
cases then pass 72/72 focused projects, including the eight pre-existing class
regressions. Integration over the final authoring head `0011c1d` then passes
54 core, 53 compiler, 15 Maven-plugin and 10 LSP tests. The complete bench passes
**911 checks, 169 skipped, across 135 cases**: client 133 React / 132 JX,
hydrate 102, static 108 and lazy 113 per side. All 72 class cells pass; the case
table includes `class-null-patch` and pair evidence remains 54/6328. The
callback-dropping mutant was killed by `class-null-patch` after an independent
unmutated baseline passed: **1 killed, 0 survived, 0 unusable**. This is one of
the seven scoped mutations on candidate `f49b48f` (source `193181f`), not a new
full-catalog run. The runtime and nullable-patch fixture/spec files are
byte-identical to this publication branch. Scratch evidence is
`jx-mutation-2296619748002841958`. Run this slice exclusively with
`java tools/MutateBench.java --only class-null-patch-callback-lost --keep`.
The final publication results follow below. The historical full mutation
campaign remains 100; this slice's original catalog had 131 entries.

The later source-preserving rebase onto next-context candidate `cc2776e`, which
includes responsive preload, passes 54 core, 2 React-option, 53 compiler,
15 Maven-plugin and 10 LSP tests. A focused comparison of `class-null-patch` and
`image-preload` passes all 16 projects. It does not rewrite complete evidence or
claim a new full-run count by itself. The runtime and nullable-patch fixtures remain identical
to the seven-mutant candidate whose full bench passed 931 checks/181 skipped.

Final integration onto next-context merge `3815001` passes the complete 15-step
gate in 348 seconds, including **919 checks, 169 skipped, across 136 cases**:
client 134 React / 133 JX, hydrate 103, static 109 and lazy 114 per side. All 72
class cells pass; pair evidence remains 54/6328. PR
[#61](https://github.com/lmrrcc/jx/pull/61) merged as `a93e325` after both
[push Verify](https://github.com/lmrrcc/jx/actions/runs/45670379139) and
[PR Verify](https://github.com/lmrrcc/jx/actions/runs/45670577701) passed on `1c23a22`.

### Remaining option inventory

| Option | Status and next evidence |
| --- | --- |
| Null initial props/state | Open: design explicit nullable schema decoding and merge semantics before removing guards |
| PureComponent with a custom should-update policy | R-28-03g below: locally implemented and focused-tested; integrated qualification pending |
| `componentWillMount`, `componentWillReceiveProps`, `componentWillUpdate`, and their `UNSAFE_` forms | Open: differential proof must cover scheduling and suppression by modern derivation/snapshot APIs |
| Class `defaultProps` | Open: define absent versus explicit null props before compiler/runtime support |
| Generic/indirect class authoring and wider field types | Open: preserve checked schemas and original-source diagnostics |
| Legacy `contextTypes`, `childContextTypes`, `getChildContext` | Removed by React 19; do not introduce obsolete behavior into the pinned React contract |
| Native editor behavior and committed hot checkpoints | Separate R-28-02b/R-28-04 qualification |

The [current Component reference](https://react.dev/reference/react/Component)
still lists deprecated lifecycle methods and class defaults. The
[React 19 upgrade guide](https://react.dev/blog/2024/04/25/react-19-upgrade-guide#removed-legacy-context-using-contexttypes-and-getchildcontext)
records removal of the old context API. Deprecated supported behavior remains
work to implement; removed behavior is not a new compatibility target.

## R-28-03g custom PureComponent policies — 25 November 2026

React invokes a class's callable `shouldComponentUpdate` before considering Pure's
default shallow comparison. It warns in development when a Pure class supplies
such a policy. JX now preserves that supported behavior: descriptor factories and
the compiler accept the ordinary two-argument override and the existing typed
next-context policy. The native constructor still extends `React.PureComponent`;
the bridge and warning are unchanged. Use `ClassComponent` for new custom-policy
code, as React recommends, but do not reject compatible existing code.

Both newly added JVM contracts failed with the old descriptor rejection. They
now pass and verify dispatch, immutable policy replacement, precedence and the
preserved Pure marker. Existing schema/context checks remain. The compiler's
real-core javac and editor-projection tests cover both signatures and coexisting
two/three-argument declarations; malformed return types are still rejected.
The package passes 52 core, 54 compiler, 15 Maven-plugin and 10 LSP tests.

The [ClassPurePolicy.jx consumer](../../tests/conformance/src/main/jx/jx/conformance/cases/ClassPurePolicy.jx)
passes eight focused projects against actual React. A true policy renders even
for equal props; a false one retains the previous DOM while current state/props
advance and queued callbacks commit. Force updates and context changes render
the latest state despite false policies. The three-argument method takes
precedence over its two-argument overload. Real DOM identity persists and only
React's expected Pure-policy warning is tolerated; unrelated errors fail.

All ten class cases also pass 80/80 focused projects after the shared runtime and
compiler change, including ordinary Pure shallow bailouts, class context,
derivation/error lifecycles, authored snapshots and typed null patches.

An initial fixture bundle was attempted before the Maven browser output existed;
that incomplete-asset run is not qualification. After Maven completed, all bundles
were rebuilt and all eight projects passed. Static projects compare initial HTML
only. Integration over final authoring head `0011c1d` then passes 56 core,
54 compiler, 15 Maven-plugin and 10 LSP tests. The complete bench passes
**919 checks, 169 skipped, across 136 cases**: client 134 React / 133 JX,
hydrate 103, static 109 and lazy 114 per side. All 80 class cells pass; the
recorded case table includes `class-pure-policy` and pair evidence stays 54/6328.
The policy-omission mutant was killed by `class-pure-policy` after an independent
unmutated baseline passed: **1 killed, 0 survived, 0 unusable**. It is one of
the seven scoped mutations on `f49b48f` (source `193181f`), whose complete bench
passed 931 checks/181 skipped. The class runtime/compiler and Pure-policy
fixture/spec files are byte-identical to this publication branch. Scratch
evidence is `jx-mutation-2296619748002841958`; reproduce this slice exclusively
with `java tools/MutateBench.java --only class-pure-policy-suppressed --keep`.
Final push gate, Verify and native IDE qualification remain pending. This slice's
original catalog had 132 entries, without a new full-catalog run beyond the
historical 100.

## R-28-03h1 nullable state and explicit field presence — 25 November 2026

The manual runtime descriptor now offers `withNullableState()`, independent of
the non-null props contract. Null state can reach constructors, render, queued
updaters, static derivation, current instance reads and previous-state lifecycle
arguments. The Java peer constructor no longer rejects null state immediately:
validation occurs when its descriptor constructs the peer, before mounting.
Existing descriptors still reject it unless explicitly configured. The null-patch
regression retains that guard at the descriptor boundary; a queued null patch
does not grant nullable initialization.

Sparse objects cannot be represented by silently defaulting required record
fields. `Presence<V>` therefore distinguishes Absent, own Undefined and Present
(including null when the value type allows it). `ClassSchema.optionalField` and
`Reader.find` expose that choice; existing required fields and `Reader.get` remain
strict. Initial encoding omits absent fields and preserves own undefined. Native
React still owns shallow merging, insertion order, Pure comparison and scheduling;
there is no parallel Java state store.

All declared source fields are validated once before calling the record factory;
Reader reads a schema-known immutable snapshot. The factory cannot conceal a
missing or invalid required source value by ignoring Reader and creating defaults.
This intentional tightening also applies to the existing defined-value read API.

`StatePatch.undefined(field)` explicitly assigns undefined to an optional field;
omission preserves prior state and never deletes a key. A null direct/functional
patch is a queued no-change operation. An empty patch produces an object merge,
including from null. Derived null means no change. Required fields must still be
defined after an object merge, even on a nullable-state descriptor.

The new `Entry.undefined` record component expands its record shape, while the
old two-argument constructor/accessors keep their defined-value meaning. This is
not a serialization compatibility promise. Modifier composition preserves the
nullable opt-in with context policies and both derived-state callbacks.

See [the consumer and qualification record](../evidence/class-nullable-state.md).
Native `.jx` sparse-record authoring, nullable initial props, general dynamic
object state, supported legacy lifecycle options and presence-aware hot codecs
remain open. In particular, this slice does not resolve R-28-04's hidden/SCU-false
commit-proof requirement or permit opaque values to cross TeaVM generations.
