package jx.react;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import jx.ClassComponent;
import jx.ClassComponentType;
import jx.ClassProps;
import jx.ClassRef;
import jx.ClassSchema;
import jx.Context;
import jx.ComponentErrorInfo;
import jx.Presence;
import jx.StatePatch;
import jx.View;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Concrete-schema bridge to native React class queues, refs and lifecycle callbacks. */
final class ReactClasses {
    private static final JSObject TYPES = weakMap();
    private static final JSObject REFS = weakMap();
    private ReactClasses() {}
    @JSFunctor interface Factory extends JSObject { JSObject create(JSObject instance, JSObject props); }
    @JSFunctor interface Render extends JSObject { JSObject render(); }
    @JSFunctor interface Done extends JSObject { void run(); }
    @JSFunctor interface Update extends JSObject { JSObject update(JSObject state, JSObject props); }
    @JSFunctor interface DerivedState extends JSObject { JSObject derive(JSObject props, JSObject state); }
    @JSFunctor interface DerivedError extends JSObject { JSObject derive(JSObject error); }
    @JSFunctor interface Caught extends JSObject { void caught(JSObject error, String componentStack); }
    @JSFunctor interface Updated extends JSObject { void update(JSObject props, JSObject state); }
    @JSFunctor interface Snapshot extends JSObject { Done capture(JSObject props, JSObject state); }
    @JSFunctor interface ShouldUpdate extends JSObject { boolean update(JSObject props, JSObject state, Object context); }
    @JSFunctor interface Attach extends JSObject { void attach(JSObject instance); }

    /** One current decoded value per native instance and schema, never a history of snapshots. */
    private static final class Current<T> {
        private final ClassSchema<T> schema;
        private final boolean nullable;
        private JSObject source;
        private T value;
        Current(ClassSchema<T> schema, boolean nullable, JSObject source, T value) {
            this.schema = schema; this.nullable = nullable; this.source = source; this.value = value;
        }
        T read(JSObject next) {
            if (source != next) observe(next, decode(schema, next, nullable));
            return value;
        }
        T pending(JSObject next) { return source == next ? value : decode(schema, next, nullable); }
        void observe(JSObject next, T decoded) { if (source != next) { source = next; value = decoded; } }
        void clear() { source = null; value = null; }
    }

    /** The same owner backs current props/state and the optional typed context reader. No
     * independent context subscription, instance cache or lifecycle/disposal flag is added. */
    private static final class InstanceHost<P,S> implements ClassComponent.Host<P,S>, ClassComponentType.ContextAccess {
        private final JSObject instance;
        private final ClassSchema<S> stateSchema;
        private final boolean nullableState;
        private final Context<?> context;
        private final Current<P> propsValue;
        private Current<S> stateValue;
        private ClassCheckpoints<S> checkpoint;

        InstanceHost(JSObject instance, ClassComponentType<P,S,?> type, JSObject initialProps, P initial) {
            this.instance = instance; this.stateSchema = type.stateSchema(); this.context = type.contextType();
            this.nullableState = type.nullableState();
            this.propsValue = new Current<>(type.propsSchema(), false, initialProps, initial);
        }
        void initializeState(JSObject initialState, S value) { stateValue = new Current<>(stateSchema, nullableState, initialState, value); }
        @Override public P props() { return propsValue.read(propsOf(instance)); }
        @Override public S state() { return stateValue.read(stateOf(instance)); }
        @Override public void update(BiFunction<S,P,StatePatch<S>> updater, Runnable committed) {
            if (checkpoint != null) checkpoint.pending();
            updateNative(instance, (pendingState, props) -> patch(stateSchema,
                    updater.apply(stateValue.pending(pendingState), propsValue.pending(props))),
                    completion(committed));
        }
        private Done completion(Runnable committed) {
            if (checkpoint == null) return committed == null ? null : committed::run;
            return () -> {
                checkpoint.completed(); checkpoint.commit();
                if (committed != null) committed.run();
            };
        }
        @Override public void force(Runnable committed) {
            if (checkpoint != null) checkpoint.pending();
            forceNative(instance, completion(committed));
        }
        @Override public <T> Supplier<T> current(Context<T> requested) {
            if (requested == null || requested != context) throw new IllegalArgumentException("Class context does not belong to this descriptor");
            return () -> Interop.value(contextOf(instance));
        }
    }

    /** Callback-local pending context; never replaces the current native instance value. */
    private record PendingContext(Context<?> descriptor, Object value) implements ClassComponentType.ContextAccess {
        @Override public <T> Supplier<T> current(Context<T> requested) {
            if (requested == null || requested != descriptor) throw new IllegalArgumentException("Pending class context does not belong to this descriptor");
            return () -> Interop.value(value);
        }
    }


    static <P,S,C extends ClassComponent<P,S,?>> JSObject element(View.ClassComponentView<P,S,C> view, String path, String key) {
        return element(view.component(), encode(view.component().propsSchema(), view.props()), view.ref(), path, key, null);
    }

    static <P,S,C extends ClassComponent<P,S,?>> JSObject element(View.ClassPropsView<P,S,C> view, String path, String key) {
        return element(view.component(), encode(view.input()), view.ref(), path, key,
                view.overrides() == null ? null : encode(view.overrides()));
    }

    private static <P,S,C extends ClassComponent<P,S,?>> JSObject element(ClassComponentType<P,S,C> type,
            JSObject encodedProps, ClassRef<C> javaRef, String path, String key, JSObject overrides) {
        boolean checkpoint = HotReload.enabled() && type.checkpoint() != null;
        Factory create = (instance, props) -> create(type, instance, props);
        DerivedState derive = !type.hasDerivedStateFromProps() ? null : (props, state) -> patch(type.stateSchema(),
                type.deriveStateFromProps(decode(type.propsSchema(), props), decode(type.stateSchema(), state, type.nullableState())));
        DerivedError deriveError = !type.hasDerivedStateFromError() ? null : error ->
                patch(type.stateSchema(), type.deriveStateFromError(ReactError.fromNative(error)));
        JSObject constructor = constructor(TYPES, type, create, type.name(), type.pure(),
                type.contextType() == null ? null : React.context(type.contextType()),
                type.defaultProps() == null ? null : encode(type.defaultProps()),
                derive, deriveError, type.lifecycle(ClassComponentType.Lifecycle.CATCH),
                checkpoint || type.lifecycle(ClassComponentType.Lifecycle.MOUNT), checkpoint || type.lifecycle(ClassComponentType.Lifecycle.UPDATE),
                type.lifecycle(ClassComponentType.Lifecycle.SNAPSHOT),
                type.lifecycle(ClassComponentType.Lifecycle.SHOULD_UPDATE));
        JSObject ref = javaRef == null ? null : ref(REFS, javaRef, instance ->
                javaRef.attach(instance == null ? null : peer(instance)));
        return checkpoint ? ClassCheckpoints.element(constructor, encodedProps, key, path, ref, overrides)
                : element(constructor, encodedProps, key, path, ref, overrides);
    }

    private static <P,S,C extends ClassComponent<P,S,?>> JSObject create(ClassComponentType<P,S,C> type,
            JSObject instance, JSObject initialProps) {
        P initial = decode(type.propsSchema(), initialProps);
        InstanceHost<P,S> host = new InstanceHost<>(instance, type, initialProps, initial);
        C peer = type.create(initial, host);
        S state = peer.initialState();
        boolean checkpointEnabled = HotReload.enabled() && type.checkpoint() != null;
        if (checkpointEnabled) state = ClassCheckpoints.restore(initialProps, type, state);
        JSObject initialState = state == null && type.nullableState() ? null : encode(type.stateSchema(), state);
        host.initializeState(initialState, state);
        Current<P> propsValue = host.propsValue;
        Current<S> stateValue = host.stateValue;
        peer.bind(host);
        ClassCheckpoints<S> checkpoint = checkpointEnabled
                ? new ClassCheckpoints<>(instance, type.checkpoint(),
                        snapshot -> decode(type.stateSchema(), snapshot, type.nullableState())) : null;
        host.checkpoint = checkpoint;
        if (!checkpointEnabled) HotReload.classStateReset(pathOf(initialProps));
        Render render = () -> {
            if (checkpoint != null) checkpoint.candidate(propsOf(instance), stateOf(instance));
            // Keep only the current graph even when the Java render does not read one of
            // its accessors. Pure skips have identical field values; normal SCU skips are
            // advanced in the explicit shouldUpdate callback below.
            propsValue.read(propsOf(instance));
            stateValue.read(stateOf(instance));
            JSObject frame = HotReload.begin(pathOf(propsOf(instance)), "", false);
            try { return React.element(peer.render()); }
            finally { HotReload.end(frame); }
        };
        Done mounted = () -> {
            // Activity/StrictMode may reconnect this lifecycle without a new class render.
            if (checkpoint != null) checkpoint.mounted();
            if (type.lifecycle(ClassComponentType.Lifecycle.MOUNT)) peer.mounted();
        };
        Updated updated = (props, previousState) -> {
            if (checkpoint != null) checkpoint.commit();
            if (type.lifecycle(ClassComponentType.Lifecycle.UPDATE))
                peer.updated(decode(type.propsSchema(), props), decode(type.stateSchema(), previousState, type.nullableState()));
        };
        Snapshot snapshot = (props, previousState) -> {
            Runnable committed = peer.snapshot(decode(type.propsSchema(), props), decode(type.stateSchema(), previousState, type.nullableState()));
            return () -> { if (checkpoint != null) checkpoint.commit(); committed.run(); };
        };
        ShouldUpdate should = (props, nextState, nextContext) -> {
            if (checkpoint != null) checkpoint.candidate(props, nextState);
            P nextProps = propsValue.pending(props);
            S next = stateValue.pending(nextState);
            boolean renderNext = type.shouldUpdate(peer, nextProps, next,
                    type.hasContextShouldUpdate() ? new PendingContext(type.contextType(), nextContext) : null);
            if (!renderNext) {
                if (checkpoint != null) checkpoint.skipped(props, nextState);
                // React advances instance props/state even when it skips render. Release the
                // preceding graph rather than retaining it until an imperative accessor runs.
                propsValue.observe(props, nextProps);
                stateValue.observe(nextState, next);
            }
            return renderNext;
        };
        Done unmount = () -> {
            if (checkpoint != null) checkpoint.disconnected();
            try { if (type.lifecycle(ClassComponentType.Lifecycle.UNMOUNT)) peer.unmounting(); }
            finally { propsValue.clear(); stateValue.clear(); }
        };
        Caught caught = (error, stack) -> peer.caught(ReactError.fromNative(error), new ComponentErrorInfo(stack));
        return callbacks(peer, initialState, render, mounted,
                updated, unmount, snapshot, should, caught);
    }

    private static JSObject encode(ClassProps<?> props) {
        JSObject object = dictionary();
        for (ClassProps.Entry<?,?> entry : props.entries()) {
            if (entry.undefined()) setUndefined(object, entry.field().name());
            else set(object, entry.field().name(), nativeValue(entry.field().type(), entry.value()));
        }
        return object;
    }
    private static <T> JSObject encode(ClassSchema<T> schema, T value) {
        schema.validate(value);
        JSObject object = object();
        for (ClassSchema.Field<T,?> field : schema.fields()) {
            switch (field.presence(value)) {
                case Presence.Absent<?> ignored -> { }
                case Presence.Undefined<?> ignored -> setUndefined(object, field.name());
                case Presence.Present<?> present -> set(object, field.name(), nativeValue(field.type(), present.value()));
            }
        }
        return object;
    }
    private static <S> JSObject patch(ClassSchema<S> schema, StatePatch<S> patch) {
        if (patch == null) return null;
        if (patch.schema() != schema) throw new IllegalArgumentException("State patch belongs to another class schema");
        JSObject object = object();
        for (StatePatch.Entry<S,?> entry : patch.entries()) {
            if (entry.undefined()) setUndefined(object, entry.field().name());
            else set(object, entry.field().name(), nativeValue(entry.field().type(), entry.value()));
        }
        return object;
    }
    private static JSObject nativeValue(ClassSchema.ValueType<?> type, Object value) {
        return type.kind() == ClassSchema.Kind.REFERENCE ? Interop.opaque(value) : Interop.scalar(value);
    }
    private static <T> T decode(ClassSchema<T> schema, JSObject object) {
        return decode(schema, object, false);
    }
    private static <T> T decode(ClassSchema<T> schema, JSObject object, boolean nullable) {
        if (isNullValue(object) && nullable) return null;
        requireObject(object);
        return schema.readPresence(field -> switch (presence(object, field.name())) {
            case 0 -> Presence.absent();
            case 1 -> Presence.undefined();
            default -> Presence.present(read(object, field.name(), field.type().kind()));
        });
    }
    private static Object read(JSObject object, String field, ClassSchema.Kind kind) {
        if (isNull(object, field)) return null;
        return switch (kind) {
            case INTEGER -> integer(object, field);
            case LONG -> (long) exactLong(object, field);
            case DOUBLE -> number(object, field);
            case FLOAT -> (float) number(object, field);
            case BOOLEAN -> bool(object, field);
            case STRING -> text(object, field);
            case REFERENCE -> reference(object, field);
        };
    }

    @JSBody(script = "return new WeakMap();") private static native JSObject weakMap();
    @JSBody(script = "return {};") private static native JSObject object();
    @JSBody(script = "return Object.create(null);") private static native JSObject dictionary();
    @JSBody(params = {"object", "field", "value"}, script = "object[field] = value;") private static native void set(JSObject object, String field, JSObject value);
    @JSBody(params = {"object", "field"}, script = "object[field] = undefined;") private static native void setUndefined(JSObject object, String field);
    @JSBody(params = "value", script = "return value === null;") private static native boolean isNullValue(JSObject value);
    @JSBody(params = "value", script = "if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new Error('Class props/state must be an object');") private static native void requireObject(JSObject value);
    @JSBody(params = {"object", "field"}, script = "return !Object.prototype.hasOwnProperty.call(object, field) ? 0 : object[field] === undefined ? 1 : 2;") private static native int presence(JSObject object, String field);
    @JSBody(params = {"object", "field"}, script = "if (!Object.prototype.hasOwnProperty.call(object, field) || object[field] === undefined) throw new Error('Missing class field: ' + field); return object[field] === null;") private static native boolean isNull(JSObject object, String field);
    @JSBody(params = {"object", "field"}, script = "var n = object[field]; if (!Number.isInteger(n) || n < -2147483648 || n > 2147483647) throw new Error('Invalid integer class field: ' + field); return n;") private static native int integer(JSObject object, String field);
    @JSBody(params = {"object", "field"}, script = "var n = object[field]; if (!Number.isSafeInteger(n)) throw new Error('Invalid exact long class field: ' + field); return n;") private static native double exactLong(JSObject object, String field);
    @JSBody(params = {"object", "field"}, script = "var n = object[field]; if (typeof n !== 'number') throw new Error('Invalid numeric class field: ' + field); return n;") private static native double number(JSObject object, String field);
    @JSBody(params = {"object", "field"}, script = "var b = object[field]; if (typeof b !== 'boolean') throw new Error('Invalid boolean class field: ' + field); return b;") private static native boolean bool(JSObject object, String field);
    @JSBody(params = {"object", "field"}, script = "var s = object[field]; if (typeof s !== 'string') throw new Error('Invalid string class field: ' + field); return s;") private static native String text(JSObject object, String field);
    @JSBody(params = {"object", "field"}, script = "return object[field];") private static native Object reference(JSObject object, String field);
    @JSBody(params = "instance", script = "return instance.props;") private static native JSObject propsOf(JSObject instance);
    @JSBody(params = "instance", script = "return instance.state;") private static native JSObject stateOf(JSObject instance);
    @JSBody(params = "instance", script = "return instance.context;") private static native Object contextOf(JSObject instance);
    @JSBody(params = "instance", script = "return instance.__jx.peer;") private static native Object peer(JSObject instance);
    @JSBody(params = "props", script = "return props.__jxPath;") private static native String pathOf(JSObject props);
    @JSBody(params = {"instance", "updater", "committed"}, script = "instance.setState(function(state, props) { return updater(state, props); }, committed === null ? undefined : function() { committed(); });") private static native void updateNative(JSObject instance, Update updater, Done committed);
    @JSBody(params = {"instance", "committed"}, script = "instance.forceUpdate(committed === null ? undefined : function() { committed(); });") private static native void forceNative(JSObject instance, Done committed);
    @JSBody(params = {"peer", "initial", "render", "mount", "update", "unmount", "snapshot", "should", "caught"}, script = "return {peer: peer, initial: initial, render: render, mount: mount, update: update, unmount: unmount, snapshot: snapshot, should: should, caught: caught};")
    private static native JSObject callbacks(Object peer, JSObject initial, Render render, Done mount, Updated update, Done unmount, Snapshot snapshot, ShouldUpdate should, Caught caught);
    @JSBody(params = {"cache", "ref", "attach"}, script = "var fn = cache.get(ref); if (!fn) { fn = function(instance) { attach(instance); }; cache.set(ref, fn); } return fn;")
    private static native JSObject ref(JSObject cache, Object ref, Attach attach);
    @JSBody(params = {"constructor", "props", "key", "path", "ref", "overrides"}, script = "props.__jxPath = path; if (key !== null) props.key = key; if (ref !== null) props.ref = ref; var element = React.createElement(constructor, props); return overrides === null ? element : React.cloneElement(element, overrides);", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject element(JSObject constructor, JSObject props, String key, String path, JSObject ref, JSObject overrides);
    @JSBody(params = {"cache", "descriptor", "create", "name", "pure", "context", "defaults", "derive", "deriveError", "caught", "mount", "update", "snapshot", "should"}, script = """
        var Type = cache.get(descriptor);
        if (Type) return Type;
        var Base = pure ? React.PureComponent : React.Component;
        Type = function(props, contextValue) {
            Base.call(this, props, contextValue);
            this.__jx = create(this, props);
            this.state = this.__jx.initial;
            this.__jx.initial = null;
        };
        Type.prototype = Object.create(Base.prototype);
        Type.prototype.constructor = Type;
        Type.displayName = name;
        if (defaults !== null) Type.defaultProps = Object.freeze(defaults);
        if (context !== null) Type.contextType = context;
        if (derive !== null) Type.getDerivedStateFromProps = function(props, state) { return derive(props, state); };
        if (deriveError !== null) Type.getDerivedStateFromError = function(error) { return deriveError(error); };
        if (caught) Type.prototype.componentDidCatch = function(error, info) { this.__jx.caught(error, info.componentStack); };
        Type.prototype.render = function() { return this.__jx.render(); };
        if (mount) Type.prototype.componentDidMount = function() { this.__jx.mount(); };
        Type.prototype.componentWillUnmount = function() { this.__jx.unmount(); };
        if (update) Type.prototype.componentDidUpdate = function(props, state, snapshotValue) {
            if (snapshotValue) snapshotValue(); else this.__jx.update(props, state);
        };
        if (snapshot) Type.prototype.getSnapshotBeforeUpdate = function(props, state) { return this.__jx.snapshot(props, state); };
        if (should) Type.prototype.shouldComponentUpdate = function(props, state, context) { return this.__jx.should(props, state, context); };
        cache.set(descriptor, Type);
        return Type;
        """, imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject constructor(JSObject cache, Object descriptor, Factory create, String name, boolean pure,
            JSObject context, JSObject defaults, DerivedState derive, DerivedError deriveError, boolean caught,
            boolean mount, boolean update, boolean snapshot, boolean should);
}
