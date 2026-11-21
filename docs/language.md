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
It declares exactly one `public static View render()` method, or a
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
React's `Object.is` for Java values: strings, characters, booleans and boxed numbers
compare by value, every other object by identity. A new list, record or lambda on
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
signatures. Attributes can appear in any order. Boolean shorthand means `true`;
there are no implicit property defaults or spread attributes. Nested record fields
can use ordinary Java generics; generic component classes themselves are currently
rejected. Props records themselves must also be concrete, without type parameters;
raw generic Props must not erase property type checking.

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
attribute API. Children need finite, bounded collections; streaming an unbounded
Iterable is not a rendering contract.

HTML attributes otherwise use React DOM spelling. `class` aliases `className`
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
- `useImperativeHandle` is described under Refs.

`React.profiler(id, onRender, children...)` and `React.strictMode(children...)` in
`jx.react` are React's `Profiler` and nested `StrictMode`. They are view factories
called in an interpolation, for example `{React.strictMode(<Panel />)}`. As in
React, a nested `StrictMode` renders its children twice in development but repeats
their effects only when it mounts after its parent.

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
