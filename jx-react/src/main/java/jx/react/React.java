package jx.react;

import jx.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Consumer;
import org.teavm.jso.*;

/** React adapter for TeaVM-compiled Java. Client operations belong to the browser main thread;
 * server hosts enter through {@link ReactServerComponents} or {@link ReactServer}.
 * React owns invocation, hooks, scheduling, keys, and reconciliation; this class converts descriptions. */
public final class React {
    private static final JSObject COMPONENTS = weakMap();
    private static final JSObject CONTEXTS = weakMap();
    private static final JSObject REFS = weakMap();
    // The react-server condition omits client class components. Initialize this
    // adapter only when a client boundary is actually composed, not for every view.
    private static final class ErrorBoundaryType {
        private static final JSObject TYPE = boundaryType();
    }
    private React() {}
    /** The version exported by the loaded {@code react} runtime, not a JX build constant. */
    @JSBody(script = "return React.version;", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    public static native String version();
    /** The version exported by the loaded {@code react-dom} runtime. */
    @JSBody(script = "return ReactDOM.version;", imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom"))
    public static native String domVersion();
    /** The version exported by the loaded {@code react-dom/client} runtime. */
    @JSBody(script = "return ReactDOMClient.version;", imports = @JSBodyImport(alias = "ReactDOMClient", fromModule = "react-dom/client"))
    public static native String clientVersion();

    /** Invokes React DOM's unstable legacy batching API and returns the callback's Java value,
     * including null, without changing its identity. Callback failures propagate to the caller.
     * Modern roots already batch automatically; this does not force a synchronous DOM commit.
     * Like the native export, this API may change with a future React version. */
    public static <T> T unstableBatchedUpdates(Supplier<T> action) {
        Objects.requireNonNull(action);
        return Interop.value(batchedNative(action::get));
    }
    @JSFunctor interface Batch extends JSObject { Object run(); }
    @JSBody(params = "action", script = "return ReactDOM.unstable_batchedUpdates(action);",
            imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom"))
    private static native Object batchedNative(Batch action);
    @JSFunctor interface Render extends JSObject { JSObject render(JSObject props); }
    @JSFunctor interface Handler extends JSObject { void handle(JSObject event); }
    @JSFunctor interface FailureView extends JSObject { JSObject render(JSObject error); }
    @JSFunctor interface Detach extends JSObject { void run(); }
    @JSFunctor interface Attach extends JSObject { Detach attach(JSObject node); }
    @JSFunctor interface Compare extends JSObject { boolean same(Object previous, Object next); }
    static { Async.installRuntime(AsyncBridge.INSTANCE); }
    /** Framework wrappers keep Java children until their final reconciliation key is known. */
    private record NativeDescription(Function<String, JSObject> build) implements View.ElementExtension {}
    private record NativeElement(JSObject element) implements View.ElementExtension {
        @Override public String key() { return nativeKey(element); }
    }
    private record NativePortal(JSObject element) implements View.Extension {
        @Override public String key() { return nativeKey(element); }
    }
    /** Error boundary for descendant rendering/lifecycle errors, including Java exceptions.
     * Like React itself, this does not catch event callbacks or asynchronous work. */
    public static View boundary(View child, Function<Throwable, View> fallback) {
        Objects.requireNonNull(fallback);
        return new NativeDescription(path -> boundary(ErrorBoundaryType.TYPE,
                element(child, path + "/content", path + "/content"), error -> {
            Throwable javaError = JSExceptions.getJavaException(error);
            return element(fallback.apply(javaError == null ? new IllegalStateException(errorMessage(error)) : javaError),
                    path + "/fallback", path + "/fallback");
        }));
    }
    /** One commit measured by {@link #profiler}: React's {@code onRender} arguments. {@code phase}
     * is {@code mount}, {@code update} or {@code nested-update}; times are milliseconds. */
    public record Profile(String id, String phase, double actualDuration, double baseDuration, double startTime, double commitTime) {}
    @JSFunctor interface OnRender extends JSObject { void render(String id, String phase, double actual, double base, double start, double commit); }
    /** React's {@code Profiler}: reports each commit of the children. The callback runs after
     * commit, outside rendering. Timings are zero in a production React build. */
    public static View profiler(String id, Consumer<Profile> onRender, Object... children) {
        Objects.requireNonNull(id); Objects.requireNonNull(onRender);
        View child = Views.fragment(children);
        return new NativeDescription(path -> profiler(id, (name, phase, actual, base, start, commit) ->
                onRender.accept(new Profile(name, phase, actual, base, start, commit)),
                element(child, path + "/profiler", path + "/profiler")));
    }
    /** React's {@code StrictMode} around part of a tree: development checks, including repeated
     * effect setup and cleanup, apply to the children only. */
    public static View strictMode(Object... children) {
        View child = Views.fragment(children);
        return new NativeDescription(path -> strict(element(child, path + "/strict", path + "/strict")));
    }
    /** React's {@code Suspense}: shows {@code fallback} while a descendant is suspended, for
     * example in {@link Hooks#use(Promise)}, and the children once they can render. {@code fallback}
     * may be null to show nothing. */
    public static View suspense(View fallback, Object... children) {
        View child = Views.fragment(children);
        return new NativeDescription(path -> suspenseNative(
                element(fallback, path + "/fallback", path + "/fallback"),
                element(child, path + "/content", path + "/content")));
    }
    /** The JX promise that settles like a JavaScript promise or thenable, for browser APIs such as
     * {@code fetch}. A rejection that is not a Java exception becomes an {@link IllegalStateException}
     * with the JavaScript message. */
    public static Promise<JSObject> promise(JSObject thenable) { return AsyncBridge.promise(Objects.requireNonNull(thenable)); }
    /** The JavaScript promise that settles like {@code promise}, for explicit interop. The same
     * object is returned for the same promise. */
    public static JSObject thenable(Promise<?> promise) { return AsyncBridge.thenable(Objects.requireNonNull(promise)); }
    /** React's {@code Activity}: while {@code visible} is false the children are hidden in the DOM
     * and their effects are cleaned up, but their state and DOM are kept; showing them again runs
     * the effects again. */
    public static View activity(boolean visible, Object... children) {
        View child = Views.fragment(children);
        return new NativeDescription(path -> activityNative(visible ? "visible" : "hidden",
                element(child, path + "/activity", path + "/activity")));
    }
    /** Names and CSS classes of a {@link #viewTransition(ViewTransitionOptions, Object...)}; React's
     * {@code name}, {@code default}, {@code enter}, {@code exit}, {@code update} and {@code share}
     * props. A null component keeps React's default. */
    public record ViewTransitionOptions(String name, String defaultClass, String enter, String exit, String update, String share) {
        /** Only a name, to pair this boundary with another one in a shared element transition. */
        public static ViewTransitionOptions named(String name) { return new ViewTransitionOptions(Objects.requireNonNull(name), null, null, null, null, null); }
    }
    /** React's {@code ViewTransition}: when the children change inside a transition, the browser
     * animates between the old and the new picture. Browsers without view transitions apply the
     * update without animation. */
    public static View viewTransition(Object... children) { return viewTransition(new ViewTransitionOptions(null, null, null, null, null, null), children); }
    /** As {@link #viewTransition(Object...)}, with a name and animation classes. The event
     * callbacks of React's component are not offered. */
    public static View viewTransition(ViewTransitionOptions options, Object... children) {
        Objects.requireNonNull(options);
        View child = Views.fragment(children);
        return new NativeDescription(path -> {
            JSObject props = object();
            if (options.name() != null) set(props, "name", Interop.text(options.name()));
            if (options.defaultClass() != null) set(props, "default", Interop.text(options.defaultClass()));
            if (options.enter() != null) set(props, "enter", Interop.text(options.enter()));
            if (options.exit() != null) set(props, "exit", Interop.text(options.exit()));
            if (options.update() != null) set(props, "update", Interop.text(options.update()));
            if (options.share() != null) set(props, "share", Interop.text(options.share()));
            return viewTransitionNative(props, element(child, path + "/transition", path + "/transition"));
        });
    }
    /** React's {@code addTransitionType}: labels the running transition, so that CSS and view
     * transition classes can tell for example forward from backward navigation. Call it inside a
     * transition action. */
    public static void addTransitionType(String type) { addTransitionTypeNative(Objects.requireNonNull(type)); }
    /** React DOM's {@code requestFormReset}: resets the uncontrolled fields of a form to their
     * defaults when the running transition ends. Call it inside a transition, with the form taken
     * from an event, for example {@code event.currentTarget()} of a submit event. */
    public static void requestFormReset(EventTarget form) {
        if (!(form instanceof BrowserTarget target)) throw new IllegalArgumentException("requestFormReset needs a form element from a browser event");
        requestFormResetNative(target.element);
    }
    /** As {@link #requestFormReset(EventTarget)}, for a form held by a {@link DomRef}. */
    public static void requestFormReset(DomElement form) {
        if (!(form instanceof BrowserElement element)) throw new IllegalArgumentException("requestFormReset needs a form element from a DomRef");
        requestFormResetNative(element.element);
    }
    /** React's {@code createPortal}: renders the children into another DOM element while they stay
     * part of this React tree, so context and event bubbling follow the React parent. The target
     * comes from a {@link DomRef} or from {@link #documentBody()} and must stay in the document. */
    public static View portal(DomElement target, Object... children) {
        if (!(Objects.requireNonNull(target) instanceof BrowserElement browser))
            throw new IllegalArgumentException("A portal target must be a DOM element of this renderer");
        return new NativePortal(portal(element(Views.fragment(children)), browser.element));
    }
    /** The document's body element, the usual target of a portal. */
    public static DomElement documentBody() { return new BrowserElement(body()); }
    /** React's {@code flushSync}: runs the updates in {@code action} and commits them to the DOM
     * before returning. Use it rarely; it forces a synchronous render and must not be called
     * during rendering or from an effect. */
    public static void flushSync(Runnable action) {
        Objects.requireNonNull(action);
        flushSyncNative(action::run);
    }
    /** React's standalone {@code startTransition}: runs {@code action} now and marks the state
     * updates it makes as a transition. It reports no pending state; use
     * {@link Hooks#useTransition()} for that. */
    public static void startTransition(Runnable action) {
        Objects.requireNonNull(action);
        startTransitionNative(action::run);
    }
    /** React's standalone {@code startTransition} with an asynchronous action, usually
     * {@code () -> Async.run(() -> ...)}: {@code action} runs now and the transition lasts until
     * the promise it returns is settled. Updates made after an {@code await} belong to a
     * transition only when they are wrapped in one again. */
    public static void startTransitionAsync(Supplier<? extends Promise<?>> action) {
        Objects.requireNonNull(action);
        startTransitionPromiseNative(() -> AsyncBridge.thenable(Objects.requireNonNull(action.get(), "The transition action returned null")));
    }
    /** Explicit event failure routing, because React error boundaries do not catch event handlers. */
    public static EventHandler guarded(EventHandler handler, Consumer<RuntimeException> failure) {
        Objects.requireNonNull(handler); Objects.requireNonNull(failure);
        return event -> { try { handler.handle(event); } catch (RuntimeException error) { failure.accept(error); } };
    }
    /** Typed external React binding. The binding owns deliberate conversion of its known props schema. */
    public static <P> ComponentType<P> binding(String name, JSObject nativeComponent, Function<P, JSObject> props) {
        Objects.requireNonNull(nativeComponent); Objects.requireNonNull(props);
        return new ComponentType<>(name, value -> new NativeElement(external(nativeComponent, Objects.requireNonNull(props.apply(value)))));
    }
    /** Export once for use in an ordinary React tree. The decoder validates the foreign props schema. */
    public static <P> JSObject exportComponent(ComponentType<P> component, Function<JSObject, P> decode) {
        Objects.requireNonNull(component); Objects.requireNonNull(decode);
        Hooks.installRuntime(ReactHooks.INSTANCE);
        Render render = props -> element(component.render(decode.apply(props)));
        return nativeFunction(render);
    }
    public static <P> Root mount(String elementId, ComponentType<P> component, P props) {
        return mount(elementId, component, props, false);
    }
    /** Enables React development checks, including repeated effect setup/cleanup. */
    public static <P> Root mountStrict(String elementId, ComponentType<P> component, P props) {
        return mount(elementId, component, props, true);
    }
    /** An error reported to a root callback. {@code error} is the Java exception that was thrown,
     * or an {@link IllegalStateException} with the JavaScript message; for a recoverable error its
     * {@linkplain Throwable#getCause() cause} is the error React recovered from, when known.
     * {@code componentStack} names the components around the failure and may be empty. */
    public record RootError(Throwable error, String componentStack) {}
    /** React's {@code createRoot} options. A null component keeps React's default.
     * {@code onCaughtError} reports errors an error boundary caught, {@code onUncaughtError}
     * errors no boundary caught (React then removes the tree; supplying this replaces React's
     * default report to the browser), {@code onRecoverableError} errors React recovered from by
     * rendering again. The callbacks run outside rendering. {@code identifierPrefix} is put in
     * front of every {@link Hooks#useId()} of the root, to keep several roots on one page apart. */
    public record RootOptions(String identifierPrefix, Consumer<RootError> onCaughtError, Consumer<RootError> onUncaughtError, Consumer<RootError> onRecoverableError) {
        public static RootOptions defaults() { return new RootOptions(null, null, null, null); }
        public RootOptions withIdentifierPrefix(String prefix) { return new RootOptions(prefix, onCaughtError, onUncaughtError, onRecoverableError); }
        public RootOptions withCaughtError(Consumer<RootError> callback) { return new RootOptions(identifierPrefix, callback, onUncaughtError, onRecoverableError); }
        public RootOptions withUncaughtError(Consumer<RootError> callback) { return new RootOptions(identifierPrefix, onCaughtError, callback, onRecoverableError); }
        public RootOptions withRecoverableError(Consumer<RootError> callback) { return new RootOptions(identifierPrefix, onCaughtError, onUncaughtError, callback); }
    }
    /** As {@link #mount(String, ComponentType, Object)}, with root options. */
    public static <P> Root mount(String elementId, ComponentType<P> component, P props, RootOptions options) {
        return mount(elementId, component, props, false, Objects.requireNonNull(options), false);
    }
    /** As {@link #mountStrict(String, ComponentType, Object)}, with root options. */
    public static <P> Root mountStrict(String elementId, ComponentType<P> component, P props, RootOptions options) {
        return mount(elementId, component, props, true, Objects.requireNonNull(options), false);
    }
    private static <P> Root mount(String elementId, ComponentType<P> component, P props, boolean strict) {
        return mount(elementId, component, props, strict, RootOptions.defaults(), false);
    }
    /** React's {@code hydrateRoot}: attaches the component to HTML that
     * {@link ReactServer#renderToString(View)} produced for the same component and props, keeping
     * the existing DOM nodes. The first render must produce the same output as the server did;
     * React reports a difference as a recoverable error and renders that part again. */
    public static <P> Root hydrate(String elementId, ComponentType<P> component, P props) {
        return mount(elementId, component, props, false, RootOptions.defaults(), true);
    }
    /** As {@link #hydrate(String, ComponentType, Object)}, with React's development checks. The
     * server must then have rendered inside {@link #strictMode(Object...)} as well. */
    public static <P> Root hydrateStrict(String elementId, ComponentType<P> component, P props) {
        return mount(elementId, component, props, true, RootOptions.defaults(), true);
    }
    /** As {@link #hydrate(String, ComponentType, Object)}, with root options. The identifier
     * prefix must equal the one given to the server renderer. */
    public static <P> Root hydrate(String elementId, ComponentType<P> component, P props, RootOptions options) {
        return mount(elementId, component, props, false, Objects.requireNonNull(options), true);
    }
    /** As {@link #hydrateStrict(String, ComponentType, Object)}, with root options. */
    public static <P> Root hydrateStrict(String elementId, ComponentType<P> component, P props, RootOptions options) {
        return mount(elementId, component, props, true, Objects.requireNonNull(options), true);
    }
    @JSFunctor interface ErrorReport extends JSObject { void report(JSObject error, String componentStack); }
    private static ErrorReport report(Consumer<RootError> callback) {
        return callback == null ? null : (error, stack) -> callback.accept(new RootError(throwable(error), stack));
    }
    /** The Java exception behind a JavaScript error, or a stand-in that keeps message and cause. */
    static Throwable throwable(JSObject error) {
        Throwable java = JSExceptions.getJavaException(error);
        if (java != null) return java;
        JSObject cause = errorCause(error);
        return new IllegalStateException(errorMessage(error), cause == null ? null : throwable(cause));
    }
    private static <P> Root mount(String elementId, ComponentType<P> component, P props, boolean strict, RootOptions options, boolean hydrate) {
        Hooks.installRuntime(ReactHooks.INSTANCE);
        JSObject child = component(new View.ComponentView<>(component, props, null), HotReload.rootPath(Objects.requireNonNull(elementId), component.name()), true);
        JSObject tree = strict ? strict(child) : child;
        // hydrateRoot takes the tree at creation; createRoot renders it afterwards.
        JSObject root = createRoot(elementId, hydrate ? tree : null, options.identifierPrefix(),
                report(options.onCaughtError()), report(options.onUncaughtError()), report(options.onRecoverableError()));
        Root owner = new Root(root, elementId);
        HotReload.root(elementId, owner);
        if (!hydrate) renderRoot(root, tree);
        return owner;
    }
    /** Owned React root. Closing is idempotent and disposes effects and subscriptions. */
    public static final class Root implements AutoCloseable {
        private JSObject root;
        private final String id;
        private Root(JSObject root, String id) { this.root = root; this.id = id; }
        @Override public void close() { if (root != null) { unmount(root); root = null; HotReload.forgetRoot(id); } }
    }
    /** Convert a view to an opaque native React element for framework-maintained interop bindings. */
    public static JSObject element(View view) {
        String path = HotReload.viewPath();
        return element(view, path, path);
    }
    private static JSObject element(View view, String position, String container) {
        return element(view, position, container, null);
    }
    private static JSObject element(View view, String position, String container, String replacementKey) {
        if (view == null) return null;
        String key = replacementKey == null ? Views.key(view) : replacementKey;
        // A keyed wrapper owns the same descendant checkpoint paths after reordering.
        // This is metadata only: never introduce an extra native Fragment boundary.
        String keyedPosition = key == null ? position : HotReload.keyedPosition(container, key);
        return switch (view) {
            case View.Keyed keyed -> element(keyed.element(), position, container, replacementKey == null ? keyed.key() : replacementKey);
            case View.Element element -> withKey(nativeElement(element.tag(), attributes(element.attributes()), children(element.children(), keyedPosition + "/element")), replacementKey);
            case View.Text text -> nativeText(text.value());
            case View.Fragment fragment -> withKey(fragment(children(fragment.children(), keyedPosition + "/fragment")), replacementKey);
            case View.ComponentView<?> component -> component(component, HotReload.childPath(component.component().name(), key, position, container), false, key);
            case View.ClassComponentView<?,?,?> component -> ReactClasses.element(component,
                    HotReload.childPath(component.component().name(), key, position, container), key);
            case View.ClassPropsView<?,?,?> component -> ReactClasses.element(component,
                    HotReload.childPath(component.component().name(), key, position, container), key);
            case View.Provider<?> provider -> withKey(provider(provider, keyedPosition), replacementKey);
            case View.Empty empty -> null;
            // A real array, so React gives the collection one position and matches its items by key.
            case View.Items items -> array(children(items.children(), position + "/items"), items.staticChildren());
            case View.Extension extension -> {
                if (extension instanceof NativeDescription description) yield withKey(description.build().apply(keyedPosition), replacementKey);
                if (extension instanceof NativeElement nativeElement) yield withKey(nativeElement.element(), replacementKey);
                if (extension instanceof NativePortal nativePortal) yield nativePortal.element();
                throw new IllegalArgumentException("Unsupported React view extension: " + extension.getClass().getName());
            }
        };
    }
    private static Object[] children(List<View> views, String container) {
        Object[] children = new Object[views.size()];
        for (int i = 0; i < children.length; i++) {
            // Text stays a plain string child: title, style, option and textarea accept nothing else.
            children[i] = views.get(i) instanceof View.Empty ? null : views.get(i) instanceof View.Text text
                    ? org.teavm.jso.core.JSString.valueOf(text.value())
                    : element(views.get(i), container + "/" + i, container);
        }
        return children;
    }
    private static JSObject attributes(Attributes attributes) {
        JSObject result = object();
        for (Attributes.Entry entry : attributes.entries()) {
            if (entry.value() instanceof EventHandler event) setHandler(result, entry.name(), js -> event.handle(new BrowserEvent(js)));
            else if (entry.value() instanceof DomRef ref) setNative(result, "ref", refFunction(REFS, ref, js -> {
                ref.attach(js == null ? null : new BrowserElement(js));
                return null;
            }));
            else if (entry.value() instanceof RefCallback callback) setNative(result, "ref", refFunction(REFS, callback, js -> {
                Runnable cleanup = callback.attach(js == null ? null : new BrowserElement(js));
                return cleanup == null ? null : cleanup::run;
            }));
            else if (entry.value() instanceof FormAction action) setNative(result, entry.name(), Forms.function(action));
            else if (entry.value() instanceof RawHtml raw) setNative(result, entry.name(), rawHtml(raw.html()));
            else if (entry.value() instanceof Style style) {
                JSObject values = object();
                style.properties().forEach((name, value) -> set(values, name, Interop.scalar(value)));
                setNative(result, entry.name(), values);
            } else set(result, entry.name(), Interop.scalar(entry.value()));
        }
        return result;
    }
    private static <P> JSObject component(View.ComponentView<P> view, String path, boolean root) {
        return component(view, path, root, view.key());
    }
    private static <P> JSObject component(View.ComponentView<P> view, String path, boolean root, String key) {
        ComponentType<P> descriptor = view.component();
        Render render = props -> {
            JSObject frame = HotReload.begin(readPath(props), descriptor.stateSignature(), readRoot(props));
            try {
                JSObject result = element(descriptor.render(Interop.value(readProps(props))));
                HotReload.commit(frame);
                return result;
            } finally { HotReload.end(frame); }
        };
        var propsEqual = descriptor.propsEqual();
        Compare compare = propsEqual == null ? null : (previous, next) -> propsEqual.test(Interop.value(previous), Interop.value(next));
        return nativeComponent(function(COMPONENTS, descriptor, render, compare, descriptor.name()), view.props(), key, path, root);
    }
    private static <T> JSObject provider(View.Provider<T> provider, String path) { return provider(context(provider.context()), provider.value(), Interop.scalar(provider.value()), children(provider.children(), path + "/provider")); }
    static JSObject context(Context<?> context) { return cachedContext(CONTEXTS, context, context.defaultValue()); }

    private static final class BrowserElement implements DomElement {
        final JSObject element;
        BrowserElement(JSObject element) { this.element = element; }
        @Override public void focus() { focus(element); }
        @Override public String value() { return value(element); }
        @Override public void setValue(String value) { setValue(element, Objects.requireNonNull(value)); }
        @Override public String tagName() { return text(element, "tagName"); }
        @Override public String textContent() { return text(element, "textContent"); }
        @JSBody(params = {"element", "value"}, script = "element.value = value;") private static native void setValue(JSObject element, String value);
        @JSBody(params = "element", script = "element.focus();") private static native void focus(JSObject element);
        @JSBody(params = "element", script = "return element.value == null ? '' : String(element.value);") private static native String value(JSObject element);
    }

    private static final class BrowserTarget implements EventTarget {
        private final JSObject element;
        BrowserTarget(JSObject element) { this.element = element; }
        @Override public String tagName() { return text(element, "tagName"); }
        @Override public String id() { return text(element, "id"); }
        @Override public String value() { return text(element, "value"); }
        @Override public boolean checked() { return flag(element, "checked"); }
        @Override public String attribute(String name) { return attribute(element, name); }
        @Override public double currentTime() { return number(element, "currentTime"); }
        @Override public boolean paused() { return flag(element, "paused"); }
        @JSBody(params = {"element", "name"}, script = "var value = element.getAttribute ? element.getAttribute(name) : null; return value == null ? '' : String(value);") private static native String attribute(JSObject element, String name);
    }

    private static final class BrowserEvent implements Event {
        private final JSObject event;
        BrowserEvent(JSObject event) { this.event = event; }
        @Override public String type() { return text(event, "type"); }
        @Override public EventTarget target() { return target("target"); }
        @Override public EventTarget currentTarget() { return target("currentTarget"); }
        @Override public EventTarget relatedTarget() { return target("relatedTarget"); }
        @Override public double timeStamp() { return number(event, "timeStamp"); }
        @Override public String targetValue() { return targetValue(event); }
        @Override public boolean targetChecked() { return targetChecked(event); }
        @Override public boolean shiftKey() { return flag(event, "shiftKey"); }
        @Override public boolean altKey() { return flag(event, "altKey"); }
        @Override public boolean ctrlKey() { return flag(event, "ctrlKey"); }
        @Override public boolean metaKey() { return flag(event, "metaKey"); }
        @Override public String key() { return text(event, "key"); }
        @Override public String code() { return text(event, "code"); }
        @Override public boolean repeat() { return flag(event, "repeat"); }
        @Override public int button() { return (int) number(event, "button"); }
        @Override public int buttons() { return (int) number(event, "buttons"); }
        @Override public double clientX() { return number(event, "clientX"); }
        @Override public double clientY() { return number(event, "clientY"); }
        @Override public double pageX() { return number(event, "pageX"); }
        @Override public double pageY() { return number(event, "pageY"); }
        @Override public int detail() { return (int) number(event, "detail"); }
        @Override public int pointerId() { return (int) number(event, "pointerId"); }
        @Override public String pointerType() { return text(event, "pointerType"); }
        @Override public boolean isPrimary() { return flag(event, "isPrimary"); }
        @Override public double pressure() { return number(event, "pressure"); }
        @Override public double deltaX() { return number(event, "deltaX"); }
        @Override public double deltaY() { return number(event, "deltaY"); }
        @Override public int deltaMode() { return (int) number(event, "deltaMode"); }
        @Override public java.util.List<Touch> touches() { return touches("touches"); }
        @Override public java.util.List<Touch> changedTouches() { return touches("changedTouches"); }
        @Override public String data() { return text(event, "data"); }
        @Override public String transferData(String format) { return transferData(event, java.util.Objects.requireNonNull(format)); }
        @Override public void setTransferData(String format, String value) { setTransferData(event, java.util.Objects.requireNonNull(format), java.util.Objects.requireNonNull(value)); }
        @Override public String animationName() { return text(event, "animationName"); }
        @Override public String propertyName() { return text(event, "propertyName"); }
        @Override public double elapsedTime() { return number(event, "elapsedTime"); }
        @Override public void preventDefault() { preventDefault(event); }
        @Override public void stopPropagation() { stopPropagation(event); }
        @Override public boolean defaultPrevented() { return flag(event, "defaultPrevented"); }
        private EventTarget target(String name) {
            JSObject element = member(event, name);
            return element == null ? null : new BrowserTarget(element);
        }
        private java.util.List<Touch> touches(String name) {
            int count = touchCount(event, name);
            java.util.List<Touch> result = new java.util.ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                result.add(new Touch((int) touchNumber(event, name, index, "identifier"),
                        touchNumber(event, name, index, "clientX"), touchNumber(event, name, index, "clientY")));
            }
            return java.util.List.copyOf(result);
        }
        @JSBody(params = "event", script = "return event.target && event.target.value != null ? String(event.target.value) : '';") private static native String targetValue(JSObject event);
        @JSBody(params = "event", script = "return !!(event.target && event.target.checked);") private static native boolean targetChecked(JSObject event);
        @JSBody(params = {"event", "name"}, script = "var value = event[name]; return value && typeof value === 'object' ? value : null;") private static native JSObject member(JSObject event, String name);
        @JSBody(params = {"event", "name"}, script = "var list = event[name]; return list && typeof list.length === 'number' ? list.length : 0;") private static native int touchCount(JSObject event, String name);
        @JSBody(params = {"event", "name", "index", "field"}, script = "var value = event[name][index][field]; return typeof value === 'number' ? value : 0;") private static native double touchNumber(JSObject event, String name, int index, String field);
        @JSBody(params = {"event", "format"}, script = "var transfer = event.clipboardData || event.dataTransfer; return transfer ? String(transfer.getData(format) || '') : '';") private static native String transferData(JSObject event, String format);
        @JSBody(params = {"event", "format", "value"}, script = "var transfer = event.clipboardData || event.dataTransfer; if (transfer) transfer.setData(format, value);") private static native void setTransferData(JSObject event, String format, String value);
        @JSBody(params = "event", script = "event.preventDefault();") private static native void preventDefault(JSObject event);
        @JSBody(params = "event", script = "event.stopPropagation();") private static native void stopPropagation(JSObject event);
    }
    @JSBody(params = {"object", "name"}, script = "var value = object[name]; return value == null ? '' : String(value);") private static native String text(JSObject object, String name);
    @JSBody(params = {"object", "name"}, script = "var value = object[name]; return typeof value === 'number' && isFinite(value) ? value : 0;") private static native double number(JSObject object, String name);
    @JSBody(params = {"object", "name"}, script = "return !!object[name];") private static native boolean flag(JSObject object, String name);
    @JSBody(script = "return new WeakMap();") private static native JSObject weakMap();
    // One function per ref object or callback, so React re-attaches only when the ref itself changes.
    @JSBody(params = {"cache", "ref", "attach"}, script = "var fn = cache.get(ref); if (!fn) { fn = function(node) { var cleanup = attach(node); return cleanup === null ? undefined : cleanup; }; cache.set(ref, fn); } return fn;") private static native JSObject refFunction(JSObject cache, Object ref, Attach attach);
    @JSBody(params = "render", script = "return render;") private static native JSObject nativeFunction(Render render);
    @JSBody(script = "function Boundary(props) { React.Component.call(this, props); this.state = {error: null}; } Boundary.prototype = Object.create(React.Component.prototype); Boundary.prototype.constructor = Boundary; Boundary.getDerivedStateFromError = function(error) { return {error: error}; }; Boundary.prototype.render = function() { return this.state.error === null ? this.props.children : this.props.fallback(this.state.error); }; return Boundary;", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject boundaryType();
    @JSBody(params = {"type", "child", "fallback"}, script = "return React.createElement(type, {fallback: fallback}, child);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject boundary(JSObject type, JSObject child, FailureView fallback);
    @JSBody(params = {"id", "onRender", "child"}, script = "return React.createElement(React.Profiler, {id: id, onRender: function(id, phase, actual, base, start, commit) { onRender(id, phase, actual, base, start, commit); }}, child);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject profiler(String id, OnRender onRender, JSObject child);
    @JSBody(params = {"child", "target"}, script = "return ReactDOM.createPortal(child, target);", imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom")) private static native JSObject portal(JSObject child, JSObject target);
    @JSBody(params = "action", script = "React.startTransition(function() { action(); });", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native void startTransitionNative(Detach action);
    @JSBody(script = "return document.body;") private static native JSObject body();
    @JSBody(params = "action", script = "ReactDOM.flushSync(function() { action(); });", imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom")) private static native void flushSyncNative(Detach action);
    @JSBody(params = "error", script = "return String(error && error.message || error);") private static native String errorMessage(JSObject error);
    @JSBody(params = {"type", "props"}, script = "return React.createElement(type, props);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject external(JSObject type, JSObject props);
    @JSBody(script = "return {};") private static native JSObject object();
    // Authored fixed siblings use React's ordinary variadic element API for key validation.
    // Only its children array is returned; the temporary description is never rendered.
    // A dynamic collection does not take this path and retains React's missing-key checks.
    @JSBody(params = {"items", "fixed"}, script = "var values = Array.prototype.slice.call(items); return fixed && values.length > 1 ? React.createElement.apply(null, [React.Fragment, null].concat(values)).props.children : values;", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject array(Object[] items, boolean fixed);
    @JSBody(params = {"object", "name", "value"}, script = "object[name] = value;") private static native void set(JSObject object, String name, JSObject value);
    @JSBody(params = {"object", "name", "value"}, script = "object[name] = value;") private static native void setNative(JSObject object, String name, JSObject value);
    @JSBody(params = {"object", "name", "handler"}, script = "object[name] = handler;") private static native void setHandler(JSObject object, String name, Handler handler);
    // A memoized descriptor becomes React.memo. The comparison also requires the same hot-reload
    // position, which is part of the native props but not of the component's own props.
    @JSBody(params = {"cache", "descriptor", "render", "compare", "name"}, script = "var fn = cache.get(descriptor); if (!fn) { fn = render; fn.displayName = name; if (compare) fn = React.memo(fn, function(previous, next) { return previous.jxPath === next.jxPath && previous.jxRoot === next.jxRoot && compare(previous.value, next.value); }); cache.set(descriptor, fn); } return fn;", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject function(JSObject cache, Object descriptor, Render render, Compare compare, String name);
    @JSBody(params = {"cache", "descriptor", "value"}, script = "var context = cache.get(descriptor); if (!context) { context = React.createContext(value); context.jxProvider = function(props) { var stable = React.useMemo(function() { return props.value; }, [props.identity]); return React.createElement(context.Provider, {value: stable}, props.children); }; cache.set(descriptor, context); } return context;", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject cachedContext(JSObject cache, Object descriptor, Object value);
    @JSBody(params = {"component", "props", "key", "path", "root"}, script = "var values = {value: props, jxPath: path, jxRoot: root}; if (key !== null) values.key = key; return React.createElement(component, values);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject nativeComponent(JSObject component, Object props, String key, String path, boolean root);
    @JSBody(params = "props", script = "return props.value;") private static native Object readProps(JSObject props);
    @JSBody(params = "props", script = "return props.jxPath;") private static native String readPath(JSObject props);
    @JSBody(params = "props", script = "return !!props.jxRoot;") private static native boolean readRoot(JSObject props);
    @JSBody(params = {"tag", "props", "children"}, script = "return React.createElement.apply(null, [tag, props].concat(children));", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject nativeElement(String tag, JSObject props, Object[] children);
    @JSBody(params = "text", script = "return React.createElement(React.Fragment, null, text);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject nativeText(String text);
    @JSBody(params = "children", script = "return React.createElement.apply(null, [React.Fragment, null].concat(children));", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject fragment(Object[] children);
    @JSBody(params = {"element", "key"}, script = "return key === null ? element : React.cloneElement(element, {key: key});", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject withKey(JSObject element, String key);
    @JSBody(params = "element", script = "return element.key == null ? null : String(element.key);") private static native String nativeKey(JSObject element);
    @JSBody(params = {"context", "value", "identity", "children"}, script = "return React.createElement.apply(null, [context.jxProvider, {value: value, identity: identity}].concat(children));", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject provider(JSObject context, Object value, JSObject identity, Object[] children);
    @JSBody(params = "error", script = "var cause = error && error.cause; return cause && typeof cause === 'object' ? cause : null;") private static native JSObject errorCause(JSObject error);
    @JSBody(params = {"elementId", "tree", "prefix", "caught", "uncaught", "recoverable"}, script = "var element = document.getElementById(elementId); if (!element) throw new Error('Missing React root: ' + elementId);"
            + " var stack = function(info) { return info && info.componentStack ? String(info.componentStack) : ''; };"
            + " var options = {}; if (prefix !== null) options.identifierPrefix = prefix;"
            + " if (caught) options.onCaughtError = function(error, info) { caught(error, stack(info)); };"
            + " if (recoverable) options.onRecoverableError = function(error, info) { recoverable(error, stack(info)); };"
            + " var hot = globalThis.__JX_HOT__; var hotFail = hot && hot.enabled && typeof hot.fail === 'function' ? hot.fail : null;"
            // The hot-reload overlay hears of uncaught errors first; without an application callback the error is still reported to the browser.
            + " if (uncaught || hotFail || (hot && hot.enabled)) options.onUncaughtError = function(error, info) { if (hotFail) hotFail(error); if (uncaught) uncaught(error, stack(info)); else if (typeof reportError === 'function') reportError(error); else console.error(error); };"
            + " return tree === null ? ReactDOM.createRoot(element, options) : ReactDOM.hydrateRoot(element, tree, options);", imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom/client"))
    private static native JSObject createRoot(String elementId, JSObject tree, String prefix, ErrorReport caught, ErrorReport uncaught, ErrorReport recoverable);
    @JSBody(params = {"fallback", "child"}, script = "return React.createElement(React.Suspense, {fallback: fallback}, child);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject suspenseNative(JSObject fallback, JSObject child);
    @JSBody(params = {"mode", "child"}, script = "return React.createElement(React.Activity, {mode: mode}, child);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject activityNative(String mode, JSObject child);
    @JSBody(params = {"props", "child"}, script = "return React.createElement(React.ViewTransition, props, child);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject viewTransitionNative(JSObject props, JSObject child);
    @JSBody(params = "type", script = "React.addTransitionType(type);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native void addTransitionTypeNative(String type);
    @JSBody(params = "form", script = "ReactDOM.requestFormReset(form);", imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom")) private static native void requestFormResetNative(JSObject form);
    @JSFunctor interface PromiseAction extends JSObject { JSObject run(); }
    @JSBody(params = "action", script = "React.startTransition(function() { return action(); });", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native void startTransitionPromiseNative(PromiseAction action);
    @JSBody(params = "html", script = "return {__html: html};") private static native JSObject rawHtml(String html);
    @JSBody(params = "child", script = "return React.createElement(React.StrictMode, null, child);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject strict(JSObject child);
    @JSBody(params = {"root", "child"}, script = "root.render(child);") private static native void renderRoot(JSObject root, JSObject child);
    @JSBody(params = "root", script = "root.unmount();") private static native void unmount(JSObject root);
}
