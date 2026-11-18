# Browser runtime contracts

JX applications execute as Java compiled by javac and TeaVM 0.16.0 into JavaScript,
using React and React DOM 19.3.1. Java 25 is the source baseline. `jx-core` contains
Java view descriptions and contracts; `jx-react` converts those descriptions to
actual React elements. React invokes components, owns hook slots, reconciles keys,
commits DOM updates, and schedules effects. There is no alternate renderer.

## Components and ownership

Declare a `Component<Props>` once, normally as a static final descriptor. Compose
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
raw HTML, reserved prototype assignment, and executable `javascript:` URLs. `Style`
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

Portals, transitions, deferred values, Suspense, lazy-module loading, SSR,
hydration, component ref forwarding, and `.jx` debugger stepping are not advertised
as implemented by this runtime. Source maps are generated for the TeaVM output;
compiler/editor source mapping is a separate contract. No payload-size or speed
claim is made from the development fixture's unminified bundle.
