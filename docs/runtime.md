# Browser runtime contracts

JX applications execute as Java compiled by javac and TeaVM 0.16.0 into JavaScript,
using React and React DOM 19.3.1. Java 25 is the source baseline. `jx-core` contains
Java view descriptions and contracts; `jx-react` converts those descriptions to
actual React elements. React invokes components, owns hook slots, reconciles keys,
commits DOM updates, and schedules effects. There is no alternate renderer.

## Components and ownership

Declare a `ComponentType<Props>` once, normally as a static final descriptor. Compose
children through `Views.component`, rather than calling a child's `render` method.
The compiler generates descriptors for `.jx` components. A descriptor maps to a
stable native React function through a WeakMap. A mounted instance owns its state;
the descriptor does not hold application state. Native Java props remain opaque,
typed Java objects rather than being serialized to JSON.

`React.mount("root", APP, props)` returns an owned, idempotently closable root.
`mountStrict` enables React development checks. All runtime calls run on the
browser main thread. `Hooks` installs one renderer per compiled Java runtime and
has no JVM hook emulation. Hook calls must keep the same unconditional order in
components and custom hooks; source diagnostics are practical checks, not a proof
of arbitrary Java control flow.

## Native Java class components

The explicit class runtime creates actual native `React.Component` or
`React.PureComponent` subclasses. Each mounted native instance owns one Java peer;
React owns reconciliation, state queues, shallow merging, commit callbacks and
lifecycle ordering. This is an additional runtime API; functional components and
hooks remain the default authoring model, and `jx.Component` remains the annotation.

Direct native classes can now be authored in `.jx` with `@Component`, public
Props/state records, an ordinary Props constructor and an instance `render()`.
The compiler generates the stable native descriptor, checked schemas and an
immutable `statePatch().withCount(value).build()` API. See the
[class authoring contract](language.md#native-class-components) and
[AuthoredCounter.jx](../tests/conformance/src/main/jx/jx/conformance/cases/AuthoredCounter.jx).
The lower-level alternative is to declare a stable `ClassComponentType<P,S,C>` once and compose it through
`Views.component(type, props, key, ref)`. Extend `ClassComponent<P,S,X>` for ordinary
classes or `PureComponent<P,S,X>` for native shallow comparison. `P` and `S` are
immutable values; `X` is the typed snapshot passed from `getSnapshotBeforeUpdate`
to `componentDidUpdate`. Descriptors explicitly select lifecycle support.
Constructors, render methods and state updaters must be pure and cannot call
hooks. Rendering may return null while retaining the class instance. Initial
props and state must be non-null.

`ClassSchema<T>` declares each field's name, accessor, value type and a checked
constructor. Props and state become native objects with one property per declared
field, so `PureComponent` uses React's comparison of those fields rather than an
opaque aggregate record. Scalars follow the browser representation rules below;
nested Java objects retain identity, even when their record values compare equal.
Schema validation rejects duplicate fields, wrong schema ownership and invalid
values. The current conservative reserved-name rule applies to both props and
state, including props-only names such as `key` and `ref`.

`StatePatch<S>` is immutable and belongs to one state schema. An omitted field is
unchanged; a present nullable field can be set to null. An empty patch is an empty
object update, while a functional updater returning null requests no update.
The direct patch overload also accepts a typed null patch and preserves pending
state. Its optional callback still runs after commit; other queued updates,
props, context or derived state may still cause a render. A null updater function
is rejected. Because Java has both patch and updater overloads, use a typed
`StatePatch<Model>` null value rather than an ambiguous `setState(null)` call.
This does not allow null initial props or state. The compiling
[NullPatchCounter.jx](../tests/conformance/src/main/jx/jx/conformance/cases/NullPatchCounter.jx)
consumer exercises queue order, callback timing and snapshot identity.
`setState` queues a patch or a pure function of pending state and next props.
Optional callbacks run after commit. `forceUpdate` uses React's native operation
and bypasses `shouldComponentUpdate`. The peer's `props()` and `state()` read the
current native values even when rendering was skipped, and repeated reads of the
same current snapshot retain decoded value identity. Historical aggregate identity
across discarded work or simulated unmounts is not qualified.

`ClassRef<C>` identifies the concrete Java peer through a checked decoder. Keep
the ref stable; `current()` is null before attachment and after detachment. The
class peer and its resources belong to that mounted instance. All calls run on
the browser main thread. The initial hot-reload policy resets class state; opaque
peers never cross TeaVM generations. Class checkpoints remain future work.

Compiling consumers are
[`ClassRuntime`](../tests/conformance/src/main/java/jx/conformance/cases/ClassRuntime.java)
and [`ClassKeyed`](../tests/conformance/src/main/java/jx/conformance/cases/ClassKeyed.java).
The first covers queued updates, omitted/null fields, commit snapshots and
callbacks, skipped rendering, forceUpdate, null output, instance refs and native
Pure comparisons. The second exercises keyed reordering of two Pure peers and
checks DOM, class-ref and state retention without additional renders.
See [the class-component decision](decisions/0006-java-class-components.md) for
the API boundary and remaining R-28 slices.

Local evidence on 25 November: the package passes 34 core, 41 compiler and 15
Maven-plugin tests. The earlier `class-runtime` focused run passed all eight
React/JX projects; interactive assertions ran in client, hydrate and lazy modes,
while static mode compared initial HTML. The complete bench including
`ClassKeyed`, integrated over SSR merge `7759933`, passes 825 checks, with 151
skipped across 122 case IDs: client 120 reference / 119 JX, hydrate 92 per side,
static 98 per side and lazy 103 per
side. Both class cases pass all eight projects. Their two new declared pair
witnesses bring this branch's evidence to 54 / 5,460 active pairs; activating
`PureComponent` expands the candidate universe. This count follows this branch's
feature and case inventory. All three scoped mutants were killed after the
unmutated scratch baseline passed, with zero survivors or unusable mutants.
PR [#48](https://github.com/lmrrcc/jx/pull/48) merged as `ccfcb2d` after the local
14-step gate and both Verify checks passed. These Chrome checks do not close
R-28: native editor qualification, advanced
lifecycles, class context, the remaining option matrix and class checkpoints are
not yet qualified.

R-28-02a adds the `class-authoring` consumer through the generated syntax. Its
focused run passes all eight React/JX projects, including initial static HTML
equality; package checks pass 34 core, 48 compiler and 15 Maven-plugin tests, and
the language-server reactor passes 10 protocol tests. The compiler tests compile
the real core API with javac, exercise static patch initialization and verify
source-mapped type errors. LSP tests cover cross-file class/prop navigation and
typed class-ref completion without changing functional ref props. After rebase
onto PR #48's merge `ccfcb2d`, the complete integrated bench passes 833 checks,
with 151 skipped across 123 cases: client 121 reference / 120 JX, hydrate 93 per
side, static 99 per side and lazy 104 per side. Both recorded TSVs come from that
complete run. Pair evidence stays 54 / 5,460; this compiler consumer adds no pair
witnesses. The `class-authoring-snapshot-lost` mutant was killed by the authored
class case after the unmutated scratch baseline passed: 1 killed, 0 survived,
0 unusable. The three runtime mutants already passed in R-28-01. Authoring
[PR #50](https://github.com/lmrrcc/jx/pull/50) merged as `3e1db97` after the local
15-step gate and both Verify checks passed. No native IDE or human qualification
is implied.

Explicit class descriptors can select one native React context with
`ClassComponentType.normalWithContext` or `pureWithContext`. The factory receives
the initial props and a typed `Supplier<T>` reading the instance's current
context. It can read that value in the constructor, rendering, lifecycle callbacks
and event handlers. React's actual `contextType` owns updates, including changes
that bypass Pure or false should-update bailouts. Missing providers use the
context default; explicit null and Java reference identity are preserved.

The compiling [`ClassContextCase`](../tests/conformance/src/main/java/jx/conformance/cases/ClassContextCase.java)
checks those contracts, Activity hide/show with state updated while hidden, and
context during cleanup. Retaining a peer or its reader retains the native
instance and its last context after unmount; this API makes no disposal promise.
Package tests pass 37 core, 48 compiler and 15 Maven-plugin checks. The complete
integrated bench over utility merge `8fdbed0` passes 855 checks, with 169 skipped
across 128 cases: client 126 reference / 125 JX, hydrate 95, static 101 and lazy
106 per side. The context and three existing class cases pass all eight projects.
No new pair witness is claimed. The captured-context mutant was killed by
`class-context` in client, hydration and lazy modes after a passing scratch
baseline: 1 killed, 0 survived, 0 unusable. [PR #53](https://github.com/lmrrcc/jx/pull/53)
merged as `198c0b8` after the 15-step local gate and both Verify checks passed on
`30a8911`. Direct `.jx` context declarations are supported by the
[class authoring contract](language.md#native-class-components). Preserved class
hot state remains separate work.

### Next context in class update policies

`descriptor.withContextShouldUpdate(context, policy)` supplies React's pending
props, state and third `nextContext` argument to a typed callback. The callback
also receives the current Java peer, whose `props()`, `state()` and constructor
context reader still expose the current instance values during this decision.
Declare the returned immutable descriptor once to preserve component identity.
The context must be the same `Context<T>` instance declared by that descriptor;
the pending value may be null.

This policy takes precedence over the peer's two-argument
`shouldComponentUpdate` override. It is pure, may be repeated or discarded, and
must not schedule updates or perform effects. Initial mounting and `forceUpdate`
bypass the policy; native provider changes can still cause rendering after a
false result. Pure descriptors may also declare a custom two-argument override
or typed context policy. The native class still extends `React.PureComponent`,
but the custom decision takes precedence over its usual shallow comparison.
React's development warning for this combination remains visible. Without a
custom policy, native Pure comparison is unchanged. See the compiling
[ClassPurePolicy.jx](../tests/conformance/src/main/jx/jx/conformance/cases/ClassPurePolicy.jx)
consumer for both forms and their skip/force/context behavior.

The compiling
[`ClassNextContext`](../tests/conformance/src/main/java/jx/conformance/cases/ClassNextContext.java)
consumer exercises both descriptor and authored forms, current/pending value
separation, nullable context, legacy-override precedence and retained DOM identity.
Its focused evidence is recorded in the
[class decision](decisions/0006-java-class-components.md#r-28-03e-typed-next-context-update-policy--25-november-2026);
the integrated gate and native IDE qualification remain separate checks.

### State derived from class props

`descriptor.withDerivedStateFromProps((nextProps, pendingState) -> patch)` adds
React's native static `getDerivedStateFromProps`. It returns a new immutable
descriptor, so declare the result once to keep component identity stable.
Existing context, factory, Pure and lifecycle settings are preserved.

React evaluates the callback after construction and queued state updates, before
should-update or Pure comparison. It can repeat or discard the calculation.
The callback has no peer instance and must remain pure: no hooks, external effects
or scheduled updates. Return a patch belonging to the descriptor's state schema,
or null to leave pending state unchanged. Omitted fields survive; explicitly
nullable fields can become null. An empty patch remains an object merge.

The compiling [`ClassDerivedProps`](../tests/conformance/src/main/java/jx/conformance/cases/ClassDerivedProps.java)
consumer exercises initialization, local/prop-driven changes, skipped rendering,
force updates, Pure bailouts and previous-DOM snapshots. The
[class decision](decisions/0006-java-class-components.md#r-28-03b-derived-state-from-props--25-november-2026)
records evidence and remaining qualification. Explicit descriptors and authored
static lifecycle declarations share this runtime contract. Browser error-boundary
qualification is described below; committed class hot checkpoints remain open. Initial state still
must be non-null.

### Class error boundaries

`descriptor.withDerivedStateFromError(error -> patch)` installs React's actual
static error-state derivation. Like prop derivation, it creates a new descriptor
and performs pure render-phase work that React can repeat. Return a patch from
the descriptor's state schema, or null for no derived state change.

Enable `Lifecycle.CATCH` to receive
`componentDidCatch(Throwable, ComponentErrorInfo)` after React commits the
fallback. Reporting and logging belong there. The
[`ClassErrorLifecycle` consumer](../tests/conformance/src/main/java/jx/conformance/cases/ClassErrorLifecycle.java)
demonstrates descendant render, derived-props and commit failures, recovery,
keyed reset, and the boundary's own render/event exclusions.

Compiled Java failures retain their original `Throwable` identity. A foreign
JavaScript throw is exposed as `ReactError`, preserving its borrowed native value
and distinguishing null from undefined. The wrapper itself has no stable identity
promise. Its optional message/stack projection reads own string data properties;
it does not invoke accessors or reconstruct Java source traces. Retaining the
error can retain the foreign object graph. `ComponentErrorInfo.componentStack()`
contains React's original component-stack string, which may be empty.

The [class decision](decisions/0006-java-class-components.md#r-28-03c-native-error-lifecycle--25-november-2026)
records the tested cases and diagnostic limits. Direct `.jx` lifecycle declarations
use the same typed runtime, as demonstrated by
[AuthoredLifecycle.jx](../tests/conformance/src/main/jx/jx/conformance/cases/AuthoredLifecycle.jx).
Production diagnostics and server-render error recovery remain separate work.

The runtime error-lifecycle slice merged in
[PR #57](https://github.com/lmrrcc/jx/pull/57) as `6ebfc76`: the final integrated
bench passed 887 checks with 169 skipped, its reporting mutant was killed after
a passing baseline, and the 15-step local gate plus both Verify checks passed.
This qualifies the explicit runtime contract, not the remaining class options.

## Hook behavior

| API | Contract and current evidence |
| --- | --- |
| `useState` / `useStateLazy` | Snapshot values, pure queued functional updates, callable values stored without invocation; two instances, three queued increments, old handler snapshots, and remount reset are browser-tested. |
| `useReducer` | React's reducer scheduling with stable Java dispatch; batched actions are browser-tested. Reducers must be pure. |
| `useEffect` | After-commit setup, cleanup before changed-dependency setup and on unmount; development repeated setup/cleanup is browser-tested. |
| `useLayoutEffect` | React layout effect; DOM-ref focus on commit is browser-tested. |
| `useRef` / `useDomRef` | Stable mutable Java cell / nullable borrowed DOM handle; writes do not schedule a render. DOM attachment is cleared on unmount. |
| `createContext` / `useContext` | Scoped providers; native React provider adapters preserve scalar value comparisons and opaque object identity. Nested scope and unchanged out-of-cache integers are browser-tested. |
| `useMemo` / `useCallback` | React memo cache and dependency comparison. Callback identity across renders is browser-tested; calculations must remain pure and cannot rely on memoization for correctness. |
| `useSyncExternalStore` | React subscribes to an external owner; unchanged snapshots must retain identity. MVU and history stores are browser-tested consumers. |
| `useId` | Delegates to React; label/input linkage is browser-tested. SSR and hydration are not implemented. |
| Custom hooks | Ordinary unconditional Java methods composed from these hooks. |

`State.value()` is the value for that render. `set` schedules a replacement and
`update` queues a pure unary operator; neither mutates an already-captured snapshot.
The state setter and reducer dispatcher remain stable for a mounted hook slot.
React may evaluate initializers, updaters, reducers, and memo calculations again in
development. Effects and command interpreters own I/O.

Dependencies are explicit: `Dependencies.always()` omits the array,
`Dependencies.none()` supplies an empty array, and `Dependencies.of(...)` supplies
values. Use the same dependency-list length and order between renders.

## Promise observer isolation

`Promise.whenSettled` observers run asynchronously in registration order on the
browser thread. Each observer has its own microtask. If an observer throws, its
exception remains visible to the browser host; it neither changes the promise's
settled value nor prevents later observers from receiving settlement. Use `map`,
`flatMap` or `recover` when a callback's result or failure belongs to a derived
promise. Repeated settlement remains a no-op.

This matters when an application observer precedes the React bridge's subscriber.
The bridge caches one native thenable per Java `Promise`, so skipping its observer
can leave a `Hooks.use` reader permanently suspended even after the Java promise
has resolved. The compiling
[`PromiseObserverFixture`](../tests/runtime-browser/src/main/java/jx/fixture/PromiseObserverFixture.java)
owns the promise above Suspense and is mounted only at
`/?fixture=promise-observer`. Its first observer intentionally throws. The browser
test requires exactly one reported error, automatic removal of the fallback
before any parent update, and successful reuse of the same promise after rerender
and repeated settlement. It does not suppress browser or console errors.

R-27-03-PROMISE local evidence, 24 November 2026: before the repair, one of the
nine Promise JVM tests failed because the bridge observer remained pending; the
Chrome regression also failed because the reader remained on its fallback.
After the repair, all nine Promise tests pass. The runtime package passes 36 JVM
tests (29 core, including those nine, and seven Elm tests), the focused browser
regression passes, and the complete runtime suite passes 13 Chrome checks.
The task is in review: the full local push gate and CI Verify are pending.
These checks add no differential feature-pair credit and do not qualify all
asynchronous ownership, cancellation or cross-browser behavior.

Reproduce the focused checks after building the runtime as described below:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl jx-core -Dtest=PromiseTest test
npx playwright test -c tests/runtime-browser/playwright.config.js -g 'throwing Java Promise observer'
```

## Representation rules

| Java value/boundary | Representation |
| --- | --- |
| State, reducer values, props, store snapshots | Opaque Java values. The bridge recovers the same Java value through a scalar JSO accessor, not an `Object[]` return conversion. |
| String, Character, Boolean dependency or comparison value | Native JavaScript string/boolean, compared using `Object.is`. |
| Byte, Short, Integer, Float, Double dependency/comparison value | Native JavaScript Number. `NaN` and signed zero use `Object.is`; Java wrapper class distinctions are intentionally not retained in comparisons. |
| Long dependency/comparison value | Number only in the exact integer range ±9,007,199,254,740,991; outside that range fails explicitly. Use an explicit String binding for larger IDs. |
| Other Java objects, records, collections, callbacks | Opaque identity. `equals()` does not replace React identity. Records with equal fields are still different dependencies when newly allocated. |
| Context provider values | A React-owned adapter memoizes the original Java value using the normalized comparison value. Equal scalars retain their existing opaque value; ordinary objects compare by identity. |
| Foreign JavaScript props | An explicit typed binding encodes/decodes its known schema. There is no blanket reflection, bean conversion, or automatic JSON transport. |

Numeric normalization applies at state/reducer equality checks, context providers,
and dependencies. JSON codecs separately validate their wire schema. Long support
in Java computation does not imply exact native JavaScript Number interoperability.
Mutable values are not tracked: mutating a Java object in place does not create a
new snapshot or dependency identity.

### Signed-zero boundary regression

The native comparison path must retain JavaScript scalars all the way to React.
Returning a native number as Java `Object` passes it through TeaVM 0.16.0's
`JSWrapper` numeric cache. Its `Map` uses SameValueZero and can merge `+0` and
`-0` before React's `Object.is` comparison runs. A Java `JSObject[]` also boxes
elements when they enter Java array storage, so merely changing the element type
does not fix dependency comparisons.

The bridge now uses the public TeaVM JSO scalar types, `JSObject` receiver
signatures and a native JavaScript dependency array. Opaque Java values remain
opaque; the existing exact-range Long policy and numeric normalization do not
change. A deferred `useDebugValue` formatter captures its original Java argument,
so it does not reconstruct a typed value from the normalized native scalar.

The compiling [`ScalarBoundaryFixture`](../tests/runtime-browser/src/main/java/jx/fixture/ScalarBoundaryFixture.java)
is isolated at `/?fixture=scalar-boundary`, outside the default hot-reload tree.
Its three browser regressions independently check numeric state, dependency
consumers and context. Boolean updates force dependency/provider rendering, so
a broken numeric state bailout cannot hide those other defects. The tests cover
both signed-zero directions, unchanged-sign state bailout, stable dependencies
on an unrelated render, memo/callback, insertion/layout/passive effects,
imperative handles and a memoized context consumer. Unexpected console or page
errors fail the tests.

Local reproduction on 25 November 2026: the first version of all three tests
failed before the repair and passed after it in the implementation worktree.
The standalone prerequisite then passed its runtime package (29 core and seven
Elm JVM tests) and the complete 16-test Chrome runtime suite. That final run also
includes the strengthened checks for unchanged-sign commit bailout and passive
effects after an unrelated render, alongside Promise and hot-reload regressions.
Generated JavaScript was checked for direct numeric values, native array slots
and `Object.is` without the numeric wrapper round trip. The local push gate and
Verify remain pending. These checks add no React feature-pair credit.
Cross-browser behavior, changing numeric wrapper classes in a hook slot and
DevTools formatter invocation have no new qualification from this regression.

After building the runtime, the focused check is:

```sh
RUNTIME_PORT=4579 npx playwright test -c tests/runtime-browser/playwright.config.js tests/runtime-browser/tests/scalar-boundary.spec.js
```

`RUNTIME_PORT` defaults to 4373; it changes both the fixture server and browser
test URL for isolated verification.

## DOM and interop

`Views` supports native elements, fragments, components, keys, iterable children,
conditional children, scalar text, and context providers. Null and boolean children
are absent. Strings remain escaped text. `Attributes` separates event callbacks
from scalar props, normalizes `class`/`for`, and rejects string event handlers,
raw HTML that is not an explicit `RawHtml` value, reserved prototype assignment, and executable `javascript:` URLs. `Style`
holds explicit CSS property values. `DomRef` exposes focus and value without
leaking a server dependency into the core.

`React.boundary(child, fallback)` is an actual React class error boundary. The
bridge unwraps Java render exceptions through TeaVM's exception interoperability.
React error boundaries do not catch event callbacks or unrelated asynchronous
work. `React.guarded(handler, failure)` provides explicit event failure routing;
HTTP failures are typed results.

`React.binding(name, nativeComponent, propsEncoder)` returns a typed Java component
for a JavaScript React component. `React.exportComponent(component, propsDecoder)`
returns a native function to install once in an ordinary React application. The
runtime fixture exercises both directions with stateful components. This proves
the bridge shape, not compatibility with every third-party component library.
Bindings for refs, complex values, and individual library contracts still require
their own tests.

## Children traversal, composed keys and native nodes

R-27-02a, 24 November 2026, merged in [PR #39](https://github.com/lmrrcc/jx/pull/39). The contracts below pass JVM, full
browser-matrix and runtime hot-replacement checks; all three scoped mutants were
killed and Verify passed. See the [interaction evidence](ga-react-interactions.md#r-27-02a-children-contracts)
for the reproduction and current limits.

`Views.childrenValue` represents a component's implicit `children` prop. No
children or a single null produces null; one child stays that child; multiple
authored siblings become `View.Items`. A supplied array or iterable stays a
collection even if it contains one item. The compiler uses this contract in both
generated Java and editor projections. An authored `<>...</>` remains an explicit
`View.Fragment`: `Children` treats it as one opaque element, including an empty
fragment, and never traverses its contents.

`Children.count`, `forEach`, `map` and `toList` traverse nested collections.
Null/boolean positions inside a collection count as empty positions; flattened
output omits empty entries. `Children.only` accepts one valid element, returning
that element unchanged. A singleton collection, text, null or a portal is not an
element and is rejected. `View.ElementExtension` distinguishes native React
elements from opaque nodes: a `React.strictMode` element passes `isValidElement`
and `only`, while a `React.portal` node fails both element checks but counts as
one traversed child.

Mapping composes source keys, nesting paths and returned-element keys, preserving
local key namespaces when arrays are flattened. `View.Keyed` carries replacement
identity metadata without adding a React wrapper or fragment; the renderer must
keep the underlying type, props, refs and children. `Views.key` exposes that
effective key. Mapping does not mutate the source views. Fixed authored siblings
retain `View.Items.staticChildren` validation metadata, avoiding false missing-key
warnings for fixed positions. Dynamic collections and mapped output retain normal
React list-key validation; the flag does not change reconciliation identity.

Numeric keys use native JavaScript scalar spelling, not Java's default number
formatting. The JVM and TeaVM consumer vectors cover signed zero, the plain and
exponential notation boundaries, subnormal/minimum and maximum doubles, a widened
float, `NaN`, infinities and the largest exact JavaScript integer. Explicit strings
also exercise React's `:`/`=` key escaping. A long outside the exact integer range
is rejected; use a string for such an identifier. Keys remain metadata, never
component props or DOM attributes.

The compiling Java consumers are
[`CombinedChildrenContract.jx`](../tests/conformance/src/main/jx/jx/conformance/cases/CombinedChildrenContract.jx),
[`CombinedChildrenMap.jx`](../tests/conformance/src/main/jx/jx/conformance/cases/CombinedChildrenMap.jx)
and [`CombinedChildrenList.jx`](../tests/conformance/src/main/jx/jx/conformance/cases/CombinedChildrenList.jx).
The contract fixture creates its unmounted portal only after a button event,
keeping initial server rendering browser-independent. API Javadocs live beside
[`Children`](../jx-core/src/main/java/jx/Children.java),
[`Views`](../jx-core/src/main/java/jx/Views.java) and
[`View`](../jx-core/src/main/java/jx/View.java).

A separate regression exposed mapped native wrappers assigning hot-reload
checkpoint paths by position instead of their composed keys. The repair now uses
the composed identity in those paths. The runtime suite passes all 12 checks,
including hot replacement of mapped DOM and `StrictMode` wrappers; this is distinct
evidence from ordinary state retention during React reconciliation. It does not
qualify opaque foreign bindings or error-boundary fallback ownership: a fallback
callback runs outside the original Java render frame, whose owner is not restored
by this change. Those lifecycle combinations remain open in R-27.

## Elm Model–View–Update

`Program<Model, Message>` supplies pure `init`, `update`, `view`, and subscription
descriptions. `Transition` contains an immutable model and command descriptions.
`Store` serializes dispatch, commits the model, notifies observers, reconciles
subscriptions, and then starts commands. Reentrant command replies are queued;
commands never execute inside an update or React rendering.

Commands have keys and one accepted result. Replacement cancels the prior command
and invalidates late replies even when the transport ignores cancellation.
Subscriptions retain their resource while their key remains present; include
configuration in the key. Removing the key cancels the source. Cancellation must
be idempotent and nonthrowing. Operational failures should become typed messages;
unexpected interpreter/update failures propagate and stop the store's owned work.

`Programs.useProgram` constructs a store without starting effects, observes it
through `useSyncExternalStore`, and starts/stops it in a React effect. Stop preserves
the model; restart reinterprets initialization commands and current subscriptions.
Strict Mode may therefore start/cancel/start an operation. This is not exactly-once
I/O: server operations need their own idempotency strategy when required.

The default bound is 1,024 listeners, queued messages, active commands,
subscriptions, and synchronous messages per drain; constructors allow a smaller
or larger positive bound. A stopped store rejects direct dispatch; stale owned
callbacks are ignored. Store snapshots must be immutable and keep identity while
unchanged. Do not duplicate the same model into independent hook state.

## Evidence and limits

The compiling consumer is
[`RuntimeApplication.java`](../tests/runtime-browser/src/main/java/jx/fixture/RuntimeApplication.java).
Run:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl tests/runtime-browser -am package
npm run build:runtime
npm run test:runtime
```

The Playwright suite uses actual Chrome, React and TeaVM output, including disabled
networking after initial asset load. The MVU module also runs focused JVM tests for
commit ordering, reentrancy, stale replies, cancellation, restart, failure, and bounds.
These validate records, sealed message hierarchies, lambdas, collections, ordinary
Java exceptions, and the Java APIs reached by the fixture. They do not establish
support for the full Java 25 standard library. Server Spring, filesystem, JDBC,
blocking network calls, and thread-based application workflows are outside this
browser subset. TeaVM reachability diagnostics remain part of the build.

## State-preserving development replacement

The development loader keeps the running application available during a rebuild.
After loading a successful candidate, it captures the latest **committed** state,
disposes the previous React roots and owned work, and starts the new compiled Java
runtime with portable checkpoints. This replaces React roots without reloading
the document. It does not pass Java instances between independently compiled TeaVM
bundles: their class identities and representations can differ.

The compiler supplies `ComponentType(name, stateSignature, render)` metadata. Logical
component paths include their parent/container position and explicit list keys.
Unchanged component fingerprints preserve compatible state slots. A changed or
missing fingerprint resets that component's state. The two-argument descriptor
constructor remains valid but intentionally has no hot-restoration guarantee.
Compiler fingerprints are conservative for hook order, state types, and custom
hooks; unknown behavior must reset rather than guess.

Scalar `useState` and `useReducer` values infer codecs from their initial String,
Character, Boolean, or numeric wrapper type. These codecs retain exact Java values,
including Unicode strings, null after initialization, integer limits, signed zero,
and NaN. An initially null value has no inferable type: supply an explicit codec,
for example `Hooks.useState(null, StateCodecs.strings())`. Callable values, arbitrary
objects, resources, and identity-sensitive values are not automatically serialized.

Custom records/collections require a pure `StateCodec<T>` with a stable schema id,
string encoder, and validating decoder:

```java
State<Model> model = Hooks.useState(initialModel, MODEL_CODEC);
// MODEL_CODEC.decode reconstructs Model and its immutable collections in the new runtime.
```

Change the codec's schema when its representation becomes incompatible. A different
schema or rejected payload resets the corresponding slot. Encoding failure resets
the containing component; unsupported values reset with a development notice.
No checkpoint payload is logged. This mechanism does not preserve arbitrary object
identity or claim transparent migration of every Java class.

For a separately owned MVU store, register it before mounting:

```java
Store<Model, Message> store = new Store<>(program);
HotReload.trackStore("application-model", "workflow-v1", store, MODEL_CODEC);
React.mountStrict("root", APP, new App.Props(store));
```

`Store` implements `StateTransfer`: restoration is allowed only before its first
activation, suppresses initial commands, and starts current subscriptions normally.
Old commands/subscriptions are cancelled during disposal. Pending operations are
not checkpointed or replayed. The model codec must normalize transient loading or
saving states when their work cannot resume, and expose a deliberate recovery path.
Strict Mode restarts do not replay initialization commands in a restored store.

Effects, refs, memo caches, context instances, DOM handles, and foreign JavaScript
component state restart. The development client restores input focus/selection
after the new root commits; state ownership remains in hooks or the tracked store.
Moving/removing components, changing keys, or changing incompatible hook metadata
can reset state. This is a development migration profile, not React Fast Refresh's
arbitrary JavaScript object-identity model.

The runtime contract is enabled only when the loader sets
`globalThis.__JX_HOT__ = {enabled: true}` before bootstrap. It exposes `capture()`,
`dispose()`, `restore(checkpoint)`, and `ready()`. Checkpoints contain JSON data and
encoded strings only; `ready()` waits for root commit and two animation frames and
rejects on an uncaught root-render error. Handled error boundaries retain their
normal application behavior. Development bundles share one React/React DOM vendor
runtime across generations, avoiding repeated delegated DOM listener registration.
The loader must capture before disposal, retain the checkpoint if candidate startup
fails, and leave the old tree running on build/load failure. Runtime bounds cover
4,096 component instances, 256 hooks per component, 128 tracked stores, one million
UTF-16 code units per encoded state value, and eight MiB per whole checkpoint.

The browser suite's `HotApplication` fixture loads separate TeaVM module generations
and checks scalar/keyed state, a controlled String form, reducers, a record containing
an immutable list, and an Elm store. It verifies new-runtime class identity and
working post-replacement handlers, lifecycle cleanup, suppressed initialization,
and safe resets after fingerprint/schema changes. The dev workflow additionally
needs source-edit/build tests; a successful checkpoint transfer alone does not
prove watcher/editor integration.

As of 23 November 2026, portals, transitions/deferred values, Suspense, promise-based
lazy component factories and component refs are implemented with differential
browser cases in the [feature register](evidence/conformance.tsv). `ComponentType.lazy`
defers its factory; it does not yet split the application into separately fetched
TeaVM modules. Root options, JX SSR/static rendering, hydration, resume, the RSC
protocol and `.jx` debugger stepping remain planned. The register records narrower
API options as well; a passing client case does not qualify server or other-browser
behavior. See the [dated progress review](status.md#progress-review--23-november-2026).

Source maps are generated for the TeaVM output; compiler/editor source mapping is
a separate contract. No payload-size or speed claim is made from the development
fixture's unminified bundle.

## Runtime metadata and test scopes

The R-29 APIs below have compiling consumers and differential evidence:
14 focused checks pass, with 18 inapplicable cells, on 25 November 2026. The
integrated full bench passes 847 checks with 169 inapplicable cells across 127
cases, on merged class-authoring base `3e1db97` (code `52eb29e`). Client 125 reference /
124 JX, hydrate 94, static 100 and lazy 105 per side pass. All four scoped utility
mutants were killed after the unmutated baseline passed, with zero survivors or
unusable mutants. PR #52 merged as `8fdbed0` after the 15-step local gate and both
Verify checks passed on `f0b5a5b`;
these results do not qualify every diagnostic depth, synchronous test API or
browser engine.

`React.version()`, `React.domVersion()` and `React.clientVersion()` read the loaded
browser runtime exports. `ReactServer.version()` and `ReactStatic.version()` read
the corresponding server entry points. They do not duplicate pinned version text
in the Java framework. `ReactDiagnostics.captureOwnerStack()` returns React's
development owner stack, or null when it is unavailable, including production
builds. An empty string is a valid distinct result. React's bounded ten-frame
element creation stack can truncate deep TeaVM conversion chains, so an active
Java component may have no printable owner frames. The raw result is preserved;
full Java owner-chain diagnostics remains an R-29-04/R-31 gap. This diagnostic
text is not a stable application protocol or a Java stack.

`React.unstableBatchedUpdates(() -> result)` delegates to React DOM's legacy
export, retaining the callback's Java result and propagating failures. Modern
roots already batch automatically; this API does not promise immediate DOM reads.

`ReactTesting.act(() -> promise)` is an asynchronous test scope. Await its result
before checking rendered output or starting another scope. It resolves after
React flushes scheduled updates/effects and retains the Java callback result;
callback and flush failures reject it. Nested scopes must also be awaited. There
is no synchronous overload, cancellation or support for overlapping independent
scopes. Production React does not implement `act`, so the returned promise rejects.
The JavaScript test host owns `globalThis.IS_REACT_ACT_ENVIRONMENT = true` and its
restoration; the library never changes that global setting.

The compiling consumer in `tests/conformance/src/main/java/jx/conformance/UtilitiesExports.java`
mounts and disposes a root with `act`, releases an asynchronous gate, verifies an
effect that schedules another state change, and exercises rejected and nested
scopes. `ReactTesting` is intended for development tests, not application handlers.
