package jx.react;

import jx.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Consumer;
import org.teavm.jso.*;

/** React DOM renderer for browser-compiled Java. All methods are browser-main-thread only.
 * React owns invocation, hooks, scheduling, keys, and reconciliation; this class converts descriptions. */
public final class React {
    private static final JSObject COMPONENTS = weakMap();
    private static final JSObject CONTEXTS = weakMap();
    private static final JSObject ERROR_BOUNDARY = boundaryType();
    private React() {}
    @JSFunctor interface Render extends JSObject { JSObject render(JSObject props); }
    @JSFunctor interface Handler extends JSObject { void handle(JSObject event); }
    @JSFunctor interface FailureView extends JSObject { JSObject render(JSObject error); }
    private record NativeView(JSObject element) implements View.Extension {}
    /** Error boundary for descendant rendering/lifecycle errors, including Java exceptions.
     * Like React itself, this does not catch event callbacks or asynchronous work. */
    public static View boundary(View child, Function<Throwable, View> fallback) {
        Objects.requireNonNull(fallback);
        return new NativeView(boundary(ERROR_BOUNDARY, element(child), error -> {
            Throwable javaError = JSExceptions.getJavaException(error);
            return element(fallback.apply(javaError == null ? new IllegalStateException(errorMessage(error)) : javaError));
        }));
    }
    /** Explicit event failure routing, because React error boundaries do not catch event handlers. */
    public static EventHandler guarded(EventHandler handler, Consumer<RuntimeException> failure) {
        Objects.requireNonNull(handler); Objects.requireNonNull(failure);
        return event -> { try { handler.handle(event); } catch (RuntimeException error) { failure.accept(error); } };
    }
    /** Typed external React binding. The binding owns deliberate conversion of its known props schema. */
    public static <P> Component<P> binding(String name, JSObject nativeComponent, Function<P, JSObject> props) {
        Objects.requireNonNull(nativeComponent); Objects.requireNonNull(props);
        return new Component<>(name, value -> new NativeView(external(nativeComponent, Objects.requireNonNull(props.apply(value)))));
    }
    /** Export once for use in an ordinary React tree. The decoder validates the foreign props schema. */
    public static <P> JSObject exportComponent(Component<P> component, Function<JSObject, P> decode) {
        Objects.requireNonNull(component); Objects.requireNonNull(decode);
        Hooks.installRuntime(ReactHooks.INSTANCE);
        Render render = props -> element(component.render(decode.apply(props)));
        return nativeFunction(render);
    }
    public static <P> Root mount(String elementId, Component<P> component, P props) {
        return mount(elementId, component, props, false);
    }
    /** Enables React development checks, including repeated effect setup/cleanup. */
    public static <P> Root mountStrict(String elementId, Component<P> component, P props) {
        return mount(elementId, component, props, true);
    }
    private static <P> Root mount(String elementId, Component<P> component, P props, boolean strict) {
        Hooks.installRuntime(ReactHooks.INSTANCE);
        JSObject root = createRoot(Objects.requireNonNull(elementId));
        JSObject child = component(new View.ComponentView<>(component, props, null), HotReload.rootPath(elementId, component.name()), true);
        Root owner = new Root(root, elementId);
        HotReload.root(elementId, owner);
        renderRoot(root, strict ? strict(child) : child);
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
        return switch (view) {
            case View.Element element -> nativeElement(element.tag(), attributes(element.attributes()), children(element.children(), position + "/element"));
            case View.Text text -> nativeText(text.value());
            case View.Fragment fragment -> fragment(children(fragment.children(), position + "/fragment"));
            case View.ComponentView<?> component -> component(component, HotReload.childPath(component.component().name(), component.key(), position, container), false);
            case View.Provider<?> provider -> provider(provider, position);
            case View.Extension extension -> {
                if (extension instanceof NativeView nativeView) yield nativeView.element();
                throw new IllegalArgumentException("Unsupported React view extension: " + extension.getClass().getName());
            }
        };
    }
    private static Object[] children(List<View> views, String container) {
        Object[] children = new Object[views.size()];
        for (int i = 0; i < children.length; i++) children[i] = element(views.get(i), container + "/" + i, container);
        return children;
    }
    private static JSObject attributes(Attributes attributes) {
        JSObject result = object();
        for (Attributes.Entry entry : attributes.entries()) {
            if (entry.value() instanceof EventHandler event) setHandler(result, entry.name(), js -> event.handle(new BrowserEvent(js)));
            else if (entry.value() instanceof DomRef ref) setHandler(result, "ref", js -> ref.attach(js == null ? null : new BrowserElement(js)));
            else if (entry.value() instanceof Style style) {
                JSObject values = object();
                style.properties().forEach((name, value) -> set(values, name, Interop.scalar(value)));
                setNative(result, entry.name(), values);
            } else set(result, entry.name(), Interop.scalar(entry.value()));
        }
        return result;
    }
    private static <P> JSObject component(View.ComponentView<P> view, String path, boolean root) {
        Component<P> descriptor = view.component();
        Render render = props -> {
            JSObject frame = HotReload.begin(readPath(props), descriptor.stateSignature(), readRoot(props));
            try {
                JSObject result = element(descriptor.render(Interop.value(readProps(props))));
                HotReload.commit(frame);
                return result;
            } finally { HotReload.end(frame); }
        };
        return nativeComponent(function(COMPONENTS, descriptor, render, descriptor.name()), view.props(), view.key(), path, root);
    }
    private static <T> JSObject provider(View.Provider<T> provider, String path) { return provider(context(provider.context()), provider.value(), Interop.scalar(provider.value()), children(provider.children(), path + "/provider")); }
    static JSObject context(Context<?> context) { return cachedContext(CONTEXTS, context, context.defaultValue()); }

    private static final class BrowserElement implements DomElement {
        final JSObject element;
        BrowserElement(JSObject element) { this.element = element; }
        @Override public void focus() { focus(element); }
        @Override public String value() { return value(element); }
        @JSBody(params = "element", script = "element.focus();") private static native void focus(JSObject element);
        @JSBody(params = "element", script = "return element.value == null ? '' : String(element.value);") private static native String value(JSObject element);
    }

    private static final class BrowserEvent implements Event {
        private final JSObject event;
        BrowserEvent(JSObject event) { this.event = event; }
        @Override public String targetValue() { return targetValue(event); }
        @Override public boolean targetChecked() { return targetChecked(event); }
        @Override public String key() { return key(event); }
        @Override public void preventDefault() { preventDefault(event); }
        @Override public void stopPropagation() { stopPropagation(event); }
        @JSBody(params = "event", script = "return event.target && event.target.value != null ? String(event.target.value) : '';") private static native String targetValue(JSObject event);
        @JSBody(params = "event", script = "return !!(event.target && event.target.checked);") private static native boolean targetChecked(JSObject event);
        @JSBody(params = "event", script = "return event.key || '';") private static native String key(JSObject event);
        @JSBody(params = "event", script = "event.preventDefault();") private static native void preventDefault(JSObject event);
        @JSBody(params = "event", script = "event.stopPropagation();") private static native void stopPropagation(JSObject event);
    }
    @JSBody(script = "return new WeakMap();") private static native JSObject weakMap();
    @JSBody(params = "render", script = "return render;") private static native JSObject nativeFunction(Render render);
    @JSBody(script = "function Boundary(props) { React.Component.call(this, props); this.state = {error: null}; } Boundary.prototype = Object.create(React.Component.prototype); Boundary.prototype.constructor = Boundary; Boundary.getDerivedStateFromError = function(error) { return {error: error}; }; Boundary.prototype.render = function() { return this.state.error === null ? this.props.children : this.props.fallback(this.state.error); }; return Boundary;", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject boundaryType();
    @JSBody(params = {"type", "child", "fallback"}, script = "return React.createElement(type, {fallback: fallback}, child);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject boundary(JSObject type, JSObject child, FailureView fallback);
    @JSBody(params = "error", script = "return String(error && error.message || error);") private static native String errorMessage(JSObject error);
    @JSBody(params = {"type", "props"}, script = "return React.createElement(type, props);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject external(JSObject type, JSObject props);
    @JSBody(script = "return {};") private static native JSObject object();
    @JSBody(params = {"object", "name", "value"}, script = "object[name] = value;") private static native void set(JSObject object, String name, Object value);
    @JSBody(params = {"object", "name", "value"}, script = "object[name] = value;") private static native void setNative(JSObject object, String name, JSObject value);
    @JSBody(params = {"object", "name", "handler"}, script = "object[name] = handler;") private static native void setHandler(JSObject object, String name, Handler handler);
    @JSBody(params = {"cache", "descriptor", "render", "name"}, script = "var fn = cache.get(descriptor); if (!fn) { fn = render; fn.displayName = name; cache.set(descriptor, fn); } return fn;") private static native JSObject function(JSObject cache, Object descriptor, Render render, String name);
    @JSBody(params = {"cache", "descriptor", "value"}, script = "var context = cache.get(descriptor); if (!context) { context = React.createContext(value); context.jxProvider = function(props) { var stable = React.useMemo(function() { return props.value; }, [props.identity]); return React.createElement(context.Provider, {value: stable}, props.children); }; cache.set(descriptor, context); } return context;", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject cachedContext(JSObject cache, Object descriptor, Object value);
    @JSBody(params = {"component", "props", "key", "path", "root"}, script = "var values = {value: props, jxPath: path, jxRoot: root}; if (key !== null) values.key = key; return React.createElement(component, values);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject nativeComponent(JSObject component, Object props, String key, String path, boolean root);
    @JSBody(params = "props", script = "return props.value;") private static native Object readProps(JSObject props);
    @JSBody(params = "props", script = "return props.jxPath;") private static native String readPath(JSObject props);
    @JSBody(params = "props", script = "return !!props.jxRoot;") private static native boolean readRoot(JSObject props);
    @JSBody(params = {"tag", "props", "children"}, script = "return React.createElement.apply(null, [tag, props].concat(children));", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject nativeElement(String tag, JSObject props, Object[] children);
    @JSBody(params = "text", script = "return React.createElement(React.Fragment, null, text);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject nativeText(String text);
    @JSBody(params = "children", script = "return React.createElement.apply(null, [React.Fragment, null].concat(children));", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject fragment(Object[] children);
    @JSBody(params = {"context", "value", "identity", "children"}, script = "return React.createElement(context.jxProvider, {value: value, identity: identity, children: children});", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject provider(JSObject context, Object value, Object identity, Object[] children);
    @JSBody(params = "elementId", script = "var element = document.getElementById(elementId); if (!element) throw new Error('Missing React root: ' + elementId); var hot = globalThis.__JX_HOT__; var options = hot && hot.enabled ? {onUncaughtError: function(error) { if (typeof hot.fail === 'function') hot.fail(error); if (typeof reportError === 'function') reportError(error); else console.error(error); }} : undefined; return ReactDOM.createRoot(element, options);", imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom/client")) private static native JSObject createRoot(String elementId);
    @JSBody(params = "child", script = "return React.createElement(React.StrictMode, null, child);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject strict(JSObject child);
    @JSBody(params = {"root", "child"}, script = "root.render(child);") private static native void renderRoot(JSObject root, JSObject child);
    @JSBody(params = "root", script = "root.unmount();") private static native void unmount(JSObject root);
}
