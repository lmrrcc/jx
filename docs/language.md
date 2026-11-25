# JX language and compiler contract

JX adds markup expressions to Java 25 compilation units. The compiler scans Java
lexical contexts, parses markup recursively, uses javac's syntax trees for Java
declarations, and generates readable Java. It does not evaluate application code.
The [counter application](../examples/counter-app/src/main/jx/jx/example/counter/)
is a compiling consumer of this contract.

Inside element or fragment children, `<!-- ... -->` is a markup comment: its
contents are neither rendered nor interpreted as Java. Comments do not change
the text on either side. Unterminated comments report `JX015` at the opening
delimiter. Java comments and comment-like strings inside Java or quoted attribute
values keep their ordinary meaning; markup comments are not attribute syntax.

## Components and properties

A component is a non-generic top-level class annotated with `@jx.Component`
(normally imported as `@Component`). This annotation is independent of Spring's
`org.springframework.stereotype.Component` and does not enable dependency injection.
The functional form declares exactly one `public static View render()` method, or a
`public static View render(Props props)` method with a public nested record:

```java
import jx.Component;
import jx.Hooks;
import jx.State;
import jx.View;

@Component
public final class Counter {
    public record Props(int initial, String label) {}

    public static View render(Props props) {
        State<Integer> count = Hooks.useState(props.initial());
        return <button onClick={event -> count.update(previous -> previous + 1)}>
            {props.label()}: {count.value()}
        </button>;
    }
}
```

`<Counter initial={0} label="Count" />` constructs the typed record in its
declared field order and references the generated `Counter.JX` descriptor.
React invokes the render function. A parent does not call a child's render method
during lowering. `JX` is a reserved static field name.

A functional component may return `null` to produce no DOM output. It remains
mounted: its hook state and effects survive until its parent removes the component
or changes its identity. Returning null does not suppress thrown render errors.

### Native class components

A public, concrete `@Component` class may directly extend
`ClassComponent<Props, Model, Snapshot>` or `PureComponent<Props, Model, Snapshot>`.
It declares public, non-generic nested `Props` and state records, a public constructor
taking Props, and a public **instance** `render()` method:

```java
@Component
public final class Counter extends ClassComponent<Counter.Props, Counter.Model, Void> {
    public record Props(int initial) {}
    public record Model(int count, String note) {}

    public Counter(Props props) { super(props, new Model(props.initial(), "ready")); }

    private void increment() {
        setState((pending, nextProps) -> statePatch().withCount(pending.count() + 1).build());
    }

    @Override public View render() {
        return <button onClick={event -> increment()}>{state().count()}</button>;
    }
}
```

The generated `Counter.JX` is a native `ClassComponentType`; React constructs the
class instance and owns its state queue. The compiler also generates checked field
schemas and an immutable `StateUpdate` builder. `statePatch().withNote(null).build()`
sets just `note` to null, while an omitted field retains its value. Setting a field
twice in one builder fails explicitly. A builder setter is `with` plus the field
name with its first character uppercased; colliding names such as `count`/`Count`
are rejected. Private lazy metadata permits the builder in an authored static
initializer, without relying on the position of the generated `JX` field.

`<Counter initial={2} key="counter" ref={counterRef} />` accepts a checked
`ClassRef<Counter>` separately from Props. A full Props spread may accompany both
`key` and this class ref. Functional component refs remain ordinary Props fields.
Keys retain their existing nullable `String` contract. Class refs expose the Java
peer after commit and clear on unmount; use a checked decoder when constructing one.

The generated schema supports `int`, `long`, `float`, `double`, `boolean`, their
boxed forms, `String`, and reifiable named/array reference types. References are
nullable and preserve identity. Primitive fields reject null; the runtime's exact
JavaScript-number policy still applies to `long`. Scalar `byte`/`short`/`char`,
parameterized fields, generic classes/records and indirect component inheritance
are outside this slice. Primitive arrays remain reference values.

Declared mount, update, unmount, snapshot and should-update overrides enable those
existing runtime callbacks. `PureComponent` supplies shallow comparison and cannot
declare should-update. Unsupported lifecycle declarations fail with `JX111`; schema
and authoring constraints use `JX110`; hook-looking calls in native classes use
`JX201`. Hooks belong to function components. Reserved native field names and
generated members (`JX`, `StateUpdate`, `statePatch`, `__JxMetadata`) are rejected.

[AuthoredCounter.jx](../tests/conformance/src/main/jx/jx/conformance/cases/AuthoredCounter.jx),
[AuthoredPure.jx](../tests/conformance/src/main/jx/jx/conformance/cases/AuthoredPure.jx)
and their [parent](../tests/conformance/src/main/jx/jx/conformance/cases/ClassAuthoring.jx)
are compiling consumers. Native editor qualification, advanced class lifecycles
and state-preserving class hot replacement remain separate roadmap work; hot
replacement currently resets class state. See the
[authoring decision](decisions/0006-java-class-components.md#r-28-02a-authoring-decision--25-november-2026).

### Memoized components

`@jx.Memo` on a component is React's `memo`: when the parent renders again and the
props are equal, React keeps the previous output and does not call `render`.

```java
@Component
@Memo
public final class Row {
    public record Props(String label, int tick) {}

    // Optional. Without it every Props field is compared.
    public static boolean arePropsEqual(Props previous, Props next) {
        return previous.label().equals(next.label());
    }

    public static View render(Props props) { return <p>{props.label()}</p>; }
}
```

The generated comparison tests each field with `ComponentType.sameValue`, which is
the bridge's native scalar comparison: strings/characters, booleans and boxed
primitive numbers compare by value across compatible boxed types. NaNs match;
positive and negative zero differ. Long values outside the native exact-integer
range retain exact Java Long equality; they are not thereby valid native number
bindings. Other objects, including arbitrary-precision numbers, compare by
identity. A new list, record or lambda on
each parent render therefore defeats the memo, as in React; keep such values stable
with `Hooks.useMemo` or `Hooks.useCallback`. A comparison must be pure. State,
context and hot updates still render a memoized component. A descriptor written by
hand is memoized with `ComponentType.memo(comparison)`.

### Pre-release component naming migration

Use `import jx.Component;` and `@Component` for new declarations. The original
`@JxComponent` spelling remains a deprecated source-compatible alias; both forms
generate the same component metadata. The original implementation prompt is a
historical specification and retains its original examples.

The runtime descriptor previously named `jx.Component<P>` is now
`jx.ComponentType<P>`, leaving `Component` available for the annotation. Handwritten
descriptor declarations and constructors must use `ComponentType`; rebuild generated
sources and dependent modules together. For example:

```java
public static final jx.ComponentType<Props> JX =
    new jx.ComponentType<>("example.Counter", Counter::render);
```

This descriptor rename is a pre-release source/binary API change. Only the old
annotation spelling retains compatibility. An explicit Spring or other `Component`
import is not interpreted as a JX annotation; use `@jx.Component` when qualification
is necessary.

Every record field is required. Unknown, missing, and duplicate properties are
compiler errors. javac checks property value types and functional interface
signatures. Attributes can appear in any order. Boolean shorthand means `true`.

### Optional props and defaults

A Props field marked `@jx.OptionalProp` may be omitted in markup. An omitted field
receives Java's default for its type (null, zero or false); the record's compact
constructor supplies any other default:

```java
public record Props(@OptionalProp String label, @OptionalProp String tone) {
    public Props { if (label == null) label = "default label"; }
}
```

`<Badge />` then renders with the default label. As with a default parameter in
JavaScript, the default also replaces an explicit null. A null attribute value,
such as `data-tone={props.tone()}` here, adds no attribute.

### Spread

`{...value}` between properties is the typed form of a JSX spread.

- On an element the value is an `Attributes`. Spreads and written attributes apply
  in source order and the later one wins: `<a title="x" {...shared}>` takes the
  title from `shared`, `<a {...shared} title="x">` keeps `x`.
- On a component the value is its whole Props record: `<Label {...props} />`. It
  must be the only property besides `key` (and `ref` on a native class), because a record cannot be merged field
  by field; build the value in Java instead.

### Generic components

A component can be generic in its Props. Declare the type parameters on the record
and on `render`; the component class itself stays non-generic:

```java
@Component
public final class TypedList {
    public record Props<T>(List<T> items, Function<T, String> label) {}

    public static <T> View render(Props<T> props) {
        return <ul>{props.items().stream().map(item -> <li>{props.label().apply(item)}</li>).toList()}</ul>;
    }
}
```

`<TypedList items={names} label={name -> name.toUpperCase()} />` infers `T` from
the property values, and javac checks the lambdas against it.

Component lookup supports the current package, explicit imports, wildcard imports,
and fully qualified names. All component declarations used in markup must be
included in the source batch. Importing component metadata from dependency JARs
is not implemented. Explicit Java React bindings can be passed to
`Views.component(...)` inside an interpolation.

A component can declare a property named `children` with type `View` (or another
type accepting a `View`). Nested markup is supplied as one fragment. Declaring
both a children attribute and nested content is an error. An empty nested body
does not supply a missing required children property; pass `children={Views.fragment()}`
explicitly.

`key` is reserved component identity metadata, typed as a nullable Java
`String`, and is not a Props field. Keep keys stable among siblings in a dynamic
list. React owns keyed reconciliation. Unkeyed collection entries and duplicate
keys follow React's warnings and behavior; the compiler does not prove uniqueness
of computed keys.

### Refs

`ref` on an element takes a `DomRef` or a `RefCallback`:

```java
DomRef input = Hooks.useDomRef();
RefCallback measure = Hooks.useCallback(node -> {
    observe(node);
    return () -> stopObserving(node);   // runs when the node is removed
}, Dependencies.none());
return <><input ref={input} /><p ref={measure}>text</p></>;
```

A callback that returns null is called again with a null element on removal, as
in React. React compares refs by identity: a lambda written inline is a new
callback on each render and is detached and attached again each time.
`DomElement` offers `focus`, `value`, `setValue`, `tagName` and `textContent`.

As in React 19, `ref` is an ordinary prop of a component. Declare it as a Props
field, typed `DomRef` to hand an inner element to the parent, or `Ref<Handle>`
to publish chosen operations with
`Hooks.useImperativeHandle(props.ref(), () -> handle, dependencies)`. There is no
separate `forwardRef`. A ref object may also be created outside a component with
`new DomRef()` or `new Ref<>(initial)`, which is React's `createRef`.

`jx.Children` is the typed counterpart of React's `Children` for a `View children`
prop: `count`, `toList`, `map`, `forEach` and `only`. `Views.cloneElement(element,
extra, children...)` copies a DOM element view with attributes merged over it and,
when given, new children; a component view is rebuilt from a new Props record
instead. `Views.isValidElement(value)` is React's `isValidElement`.

As in React, every child written in markup keeps one position among its siblings.
A null or boolean child renders nothing but holds its place (`View.Empty`), and a
collection child, such as the list from `stream().map(...).toList()`, is one
position whose items are matched by `key` (`View.Items`). A component written after
`{shown ? <Banner /> : null}` or after a list therefore keeps its state when the
condition flips or the list grows. Give the items of a collection a `key`; React
warns in development when they have none. `Children.count` counts empty positions
and looks through collections; `Children.toList` leaves empty positions out.

Records are shallowly immutable. Application authors must defensively copy mutable
collections and nested values when props ownership requires immutable snapshots.

## Markup expressions

Supported forms include:

```java
<main class="page"><h1>Hello</h1></main>
<input disabled />
<svg viewBox="0 0 20 20"><path strokeWidth={2} d="M0 0L20 20" /></svg>
<><span>First</span><span>Second</span></>
<Panel title="Content"><Counter initial={0} label="Nested" /></Panel>
condition ? <span>Ready</span> : null
items.stream().map(item -> <Counter key={item.id()} initial={0} label={item.label()} />).toList()
```

Markup may appear in Java expression positions, including returns, parentheses,
ternaries, lambda bodies, and expressions nested within markup braces. Normal Java
strings, character literals, text blocks, comments, comparisons, generic type
arguments, and method references are retained. Conditions use Java typing: a
boolean-and-View expression is invalid Java.

Lowercase names are intrinsic HTML/SVG tags; uppercase or dotted names are
components. All tags require an explicit matching close or `/>`, including HTML
void elements. Fragments use `<>...</>`. A Java interpolation uses balanced braces.
An empty interpolation is an error; comments alone do not constitute a Java value.

The compiler limits each source snapshot to 4 MiB of UTF-16 content and markup /
interpolation nesting to 128 levels. Incomplete documents yield an original-source
diagnostic. Editor services can retain metadata from other valid documents.

## Text, children, attributes, and escaping

A single-line text node retains its spaces. For multiline text, indentation is
removed from interior lines, trailing layout spaces are removed before newlines,
empty layout lines are discarded, and remaining lines are joined with one space.
Use a Java string interpolation for exact whitespace, such as `{" "}`.

Text and quoted attribute values decode `&amp;`, `&lt;`, `&gt;`, `&quot;`,
`&apos;`, `&nbsp;`, and valid decimal/hexadecimal numeric entities. Escape a
literal ampersand; unknown entities are errors. Literal markup delimiters can use
entities or Java string interpolations. Quoted markup values are strings; Java
backslash escapes belong inside a Java expression, not a markup quoted value.

`Views` normalizes children as follows:

| Java value | Rendering |
| --- | --- |
| null or boolean | No child |
| String, Character, Number | Text, escaped by React |
| View | Its declared view |
| Iterable or object array | Recursively flattened children |
| Other objects or primitive arrays | Explicit runtime error |

A Java string containing HTML remains text. Raw HTML is not accepted by the ordinary
attribute API. The one explicit way in is React's `dangerouslySetInnerHTML` with a
`RawHtml` value, on an element without children:

```java
<div dangerouslySetInnerHTML={RawHtml.unsanitized(markup)} />
```

Nothing is escaped or sanitized. Markup that contains text from a user or a request
lets that source run script in the page; pass only markup the application wrote or
a sanitizer cleaned. A plain string is a compile error there. Children need finite, bounded collections; streaming an unbounded
Iterable is not a rendering contract.

HTML attributes otherwise use React DOM spelling. Every attribute name and event handler name in React DOM's
own tables and every HTML and SVG element is exercised by a generated bench case and
compared with React. `class` aliases `className`
and `for` aliases `htmlFor`; specifying both aliases is a duplicate error.
Use names such as `tabIndex`, `readOnly`, `viewBox`, and `strokeWidth`.
`data-*` and `aria-*` names retain their spelling. Boolean attributes use Java
booleans. `style` accepts the typed `Style` API. `ref` on intrinsic elements
accepts `Hooks.useDomRef()`; it is React metadata, not a string DOM attribute.
DOM event names such as `onClick` target `EventHandler`, so lambda parameters
are typed. Event strings are rejected. A handler receives one `jx.Event`, whose
accessors cover every event family: type and targets, modifier keys, keyboard,
mouse and pointer, wheel, touch lists, composition text, clipboard and drag data,
animation and transition names. An accessor that does not apply to the event
returns an empty value and never throws; read values inside the handler. Capture
handlers use React's names, such as `onClickCapture`. Other attribute values must be scalar or
an explicitly supported typed value. These runtime checks do not constitute a
complete static HTML attribute schema.

## Hooks and source diagnostics

Hooks belong to mounted component instances. `State.value()` is the current
render's immutable snapshot; setters schedule future rendering. Functional
updaters queue in order. Effects and external operations belong outside render.

`Hooks` offers React's hooks under their own names. Beyond state, effects, refs,
memo, context, external stores and ids:

- `useReducerLazy(reducer, initial)` computes the first state once per mount, like
  the third argument of React's `useReducer`.
- `useInsertionEffect` runs before layout effects and before the DOM changes.
- `useEffectEvent` takes a `Runnable` or a `Consumer` and returns an action that
  always runs the latest render's callback, so an effect can call it without
  depending on what it reads.
- `useDebugValue(value)` and `useDebugValue(value, format)` label a custom hook in
  React DevTools.
- `useTransition()` returns a `TransitionState`: `pending()`, `start(action)` and
  `startAsync(...)`. An asynchronous action either returns a `Promise`, usually
  `startAsync(() -> Async.run(() -> { ... }))`, or takes a completion callback,
  `startAsync(done -> ...)`; the transition stays pending until the promise is
  settled or the callback is called.
  `React.startTransition(action)` in `jx.react` is the standalone form.
- `use(promise)` returns the value of a `Promise` or suspends the component, and
  `use(context)` reads a context. Unlike other hooks, `use` may be called in a
  condition or a loop. See Asynchronous functions below.
- `useDeferredValue(value)` and `useDeferredValue(value, initial)` return the
  previous value while a render with the new one is prepared.
- `useImperativeHandle` is described under Refs.

`React.portal(target, children...)` is React's `createPortal`: the children render
into another DOM element, taken from a `DomRef` or `React.documentBody()`, while
context and event bubbling still follow the React parent. `React.flushSync(action)`
commits the updates made in `action` to the DOM before it returns.

`React.activity(visible, children...)` is React's `Activity`: hidden children keep
their state and DOM while their effects are cleaned up. `React.viewTransition(children...)`
is `ViewTransition`; `React.viewTransition(options, children...)` adds a name and
animation classes, and `React.addTransitionType(type)` labels the running
transition. The event callbacks of `ViewTransition` are not offered.

`Resources` in `jx.react` offers React DOM's resource hints: `preconnect`,
`prefetchDNS`, `preload`, `preinit`, `preloadModule` and `preinitModule`. Each adds
its element to the document head once per URL; `Resources.Options` carries the
optional settings.

`React.mount` and `React.mountStrict` take an optional `React.RootOptions`:
`withIdentifierPrefix` for the ids of `useId`, and `withCaughtError`,
`withUncaughtError` and `withRecoverableError` for React's root error callbacks.
Each callback receives a `RootError` with the Java exception and the component
stack. Supplying `withUncaughtError` replaces React's default report to the browser.

`ReactServer.renderToString(view)` and `ReactServer.renderToStaticMarkup(view)` in
`jx.react` are React's synchronous server renderers. They need no document: the
compiled application is started in a Node host and calls them there. Effects do
not run and handlers are not attached. `React.hydrate` and `React.hydrateStrict`
attach the same component to that HTML in the browser and keep its DOM nodes; the
first render must produce the same output as the server did. `ExternalStore.serverSnapshot()`
is the value a store shows on the server and during hydration.

`ReactServer.renderToPipeableStream(view, options)` and
`ReactServer.renderToReadableStream(view, options)` stream the HTML: content under a
`React.suspense` boundary that is not ready is sent after its fallback. The first
returns a `PipeableStream` with `shellReady()` and `allReady()` promises and
`pipe(sink)`, to a Java `HtmlSink` or a Node `Writable`; the second returns the
promise of a `ReadableStream` that wraps the web stream. `StreamOptions` carries
the identifier prefix, the bootstrap scripts and modules, and an error callback.

`ReactStatic.prerender(view, options)` and `ReactStatic.prerenderToNodeStream(view, options)`
produce static HTML ahead of a request: they wait for every `React.suspense`
boundary, so the output has no fallbacks. Each returns a `Prerendering` with the
promise of the result (`prelude()`, `text()`) and `abort()`, which ends the wait
early and leaves a `postponed()` state to continue from.

That state is continued later, with the same view: `ReactServer.resume` and
`ReactServer.resumeToPipeableStream` stream the rest, and
`ReactStatic.resumeAndPrerender` and `resumeAndPrerenderToNodeStream` finish it as
static HTML. `Postponed.json()` and `Postponed.fromJson` store the state between
the two steps; only state that came from `json()` may be passed back.

`React.profiler(id, onRender, children...)` and `React.strictMode(children...)` in
`jx.react` are React's `Profiler` and nested `StrictMode`. They are view factories
called in an interpolation, for example `{React.strictMode(<Panel />)}`. As in
React, a nested `StrictMode` renders its children twice in development but repeats
their effects only when it mounts after its parent.

### Asynchronous functions, promises and Suspense

`jx.Promise<T>` is the counterpart of a JavaScript promise: it is settled once,
with a value or an error, and its callbacks (`whenSettled`, `map`, `flatMap`,
`recover`) run after the current browser job. `Promise.create(resolver -> ...)`
makes one; `React.promise(jsPromise)` and `React.thenable(promise)` in `jx.react`
convert from and to JavaScript promises for browser APIs.

`Async.run` is `async` and `Async.await` is `await`. The body is ordinary Java,
including loops and `try`/`catch`, and really suspends at each `await` while the
browser stays responsive:

```java
import static jx.Async.await;

Promise<String> title = Async.run(() -> {
    User user = await(loadUser(id));
    try {
        Team team = await(loadTeam(user.team()));
        return user.name() + " of " + team.name();
    } catch (IllegalStateException missing) {
        return user.name();
    }
});
```

- The body starts at once and runs to its first `await`, as in JavaScript. Started
  from inside another asynchronous function, it starts after the current browser job.
- A failed promise throws at `await`: unchecked errors as they are, checked
  exceptions wrapped in `AsyncException`. An exception that leaves the body rejects
  the promise that `Async.run` returned.
- `await` works only in Java code called from an `Async.run` body. It throws
  `IllegalStateException` in an event handler, an effect or rendering; start an
  asynchronous function there. It must not be called inside a callback that React
  or the browser runs synchronously, such as the action of `React.flushSync`; that
  misuse is not detected.
- Only methods that can reach an `await` are compiled as suspendable code. Calling
  `await` behind a widely used interface such as `Runnable` or `Supplier` makes
  every caller of that interface suspendable and the bundle larger; `Async.Body`
  and `Async.Action` exist to avoid that.

In a component, read a promise with `Hooks.use(promise)` under
`React.suspense(fallback, children...)`, React's `Suspense`. The promise must be
the same object on every render: create it outside rendering, or keep it in state
or a memo. A failed promise reaches the nearest `React.boundary`.

```java
{React.suspense(<p>Loading</p>, <Profile user={userPromise} />)}
```

### Form actions

A form's `action` and a submit button's `formAction` take a function in place of a
URL. It receives the submitted fields as `FormData` (`get`, `getAll`, `has`; file
fields are not offered), runs inside a transition, and React resets the form's
uncontrolled fields afterwards:

```java
<form action={data -> result.set("saved " + data.get("name"))}>
```

- `FormAction.async(data -> Async.run(() -> { ... }))` is an asynchronous action;
  the form is pending until its promise is settled.
- `Hooks.useFormStatus()` in a component inside the form returns a `FormStatus`
  with `pending()`, `data()`, `method()` and nullable `action()` / `actionUrl()` alternatives. The action is the
  actual submitted function, including a submit button's override and an
  action-state dispatcher. Java actions preserve identity when crossing the
  native React boundary. A URL reported by React during an `onSubmit` transition
  is available through `actionUrl()`; `action()` remains null for that alternative.
  Idle forms expose neither. Read this
  snapshot during rendering; invoking an action is an event/transition operation.
- `Hooks.useActionState(action, initial)` and `useActionStateAsync` return an
  `ActionState`: `state()`, the `action()` to give to the form, and `pending()`.
  The action receives the previous state and the form data and returns the next
  state, or its promise in the asynchronous form.
- `Hooks.useOptimistic(value)` returns a `State` that shows `value`, or what was
  set inside a running transition or action until it ends. With an update function
  it returns a `ReducerState`.
- `React.startTransitionAsync(() -> promise)` is the standalone asynchronous
  transition, and `React.requestFormReset(form)` resets a form by hand.

`FormStatus` retains its three-argument constructor for source/binary call-site
compatibility. Adding action components intentionally changes record
patterns, structural equality and `toString` in this pre-GA API. Consumers using
record patterns must include both action components or use accessor methods.

The pinned React runtime reports a URL value during a prevented URL submission
with an async transition. This differs from the current
[useFormStatus documentation](https://react.dev/reference/react-dom/hooks/useFormStatus),
which describes the action as a function or null. JX preserves the observed URL
through its separate typed accessor; the differential consumer records that
behavior rather than coercing the reference value to null.

`ComponentType.lazy(name, () -> promiseOfComponent)` is React's `lazy`: the loader
runs once, when the component first renders, and the nearest `React.suspense`
shows its fallback until the promise is settled. `LazyComponent.of(name, loader)` is
the same with `retry()`, which forgets a failed load so that the next render tries
again.

To keep code out of the first download, the loader calls `LazyModules.load(url)` for
a second, separately compiled JX module and wraps the component it exports with
`React.binding`. Java objects do not cross between the two modules: props are
encoded into a plain JavaScript object on one side and decoded on the other, a
context of the host is not seen, and an exception arrives with its message but not
its type. See the [decision record](decisions/0005-lazy-module-boundary.md).

The compiler checks conventional `useX` calls in render/custom-hook methods for
placement inside branches, loops, callbacks, try/switch constructs, and after a
possible early return. These are conservative syntax checks. They do not analyze
arbitrary helper call graphs, prove render purity, verify effect dependencies, or
replace React lifecycle testing. Helpers named `useX` are treated as hooks by this
naming convention.

`JxCompiler.parse`, `compile`, and `check` expose immutable source snapshots,
syntax nodes, component/property ranges, generated Java, and source maps.
Offsets and columns are UTF-16; diagnostic lines and columns are one-based.
Copied Java expressions map character-for-character. Generated scaffolding maps
to its owning tag, attribute, or declaration.

The Maven `check` / `check-test` goals run javac with annotation processors
disabled and report errors at the original `.jx` position, for example:

```text
Counter.jx:12:18: error [compiler.err.prob.found.req] incompatible types: java.lang.String cannot be converted to int
```

Normal Maven Java compilation still runs afterward. Registering generated source
roots alone does not remap errors. Projects relying on annotation-generated Java
must make those generated sources available before this separate check.

Diagnostic maps are implemented and tested. TeaVM/esbuild JavaScript maps are
generated separately. Mapping JavaScript stack traces or debugger breakpoints
through generated Java back to the original `.jx` markup is not yet implemented.
