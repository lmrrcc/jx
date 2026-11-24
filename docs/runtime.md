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
