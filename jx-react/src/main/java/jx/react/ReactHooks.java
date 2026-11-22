package jx.react;

import jx.*;
import jx.spi.HookRuntime;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.teavm.jso.*;

final class ReactHooks implements HookRuntime {
    static final ReactHooks INSTANCE = new ReactHooks();
    @JSFunctor interface Factory extends JSObject { Object get(); }
    @JSFunctor interface Update extends JSObject { Object update(Object old); }
    @JSFunctor interface Reduce extends JSObject { Object reduce(Object old, Object action); }
    @JSFunctor interface Cleanup extends JSObject { void run(); }
    @JSFunctor interface Setup extends JSObject { Cleanup run(); }
    @JSFunctor interface Subscribe extends JSObject { Cleanup subscribe(Cleanup listener); }

    @Override public <T> State<T> state(Supplier<T> initial, StateCodec<T> codec) {
        JSObject slot = HotReload.slot("state");
        Ref<StateCodec<T>> selected = HotReload.enabled() ? Interop.value(refNative(new Ref<>(codec))) : null;
        JSObject pair = stateNative(() -> {
            T value = initial.get();
            if (selected == null) return value;
            if (selected.current() == null) selected.set(StateCodecs.forInitialValue(value));
            return HotReload.restore(slot, selected.current(), value);
        });
        T snapshot = Interop.value(value(pair));
        HotReload.record(slot, selected == null ? null : selected.current(), () -> snapshot);
        JSObject dispatch = setter(pair);
        State.Setter<T> setter = memoRaw(() -> update -> updateNative(dispatch, old -> {
            T previous = Interop.value(old);
            T next = update.apply(previous);
            return Interop.same(previous, next) ? previous : next;
        }), Dependencies.none());
        return new State<>(snapshot, setter);
    }
    @Override public <S, A> ReducerState<S, A> reducer(BiFunction<S, A, S> reducer, S initial, StateCodec<S> codec) {
        JSObject slot = HotReload.slot("reducer");
        StateCodec<S> selected = codec == null ? StateCodecs.forInitialValue(initial) : codec;
        JSObject pair = reducerNative(reduce(reducer), () -> HotReload.restore(slot, selected, initial));
        S snapshot = Interop.value(value(pair));
        HotReload.record(slot, selected, () -> snapshot);
        JSObject setter = setter(pair);
        Consumer<A> dispatch = memoRaw(() -> action -> dispatchNative(setter, action), Dependencies.none());
        return new ReducerState<>(snapshot, dispatch);
    }
    /** Keeps the previous state object when the reducer returns an equal value, so React bails out. */
    private static <S, A> Reduce reduce(BiFunction<S, A, S> reducer) {
        return (old, action) -> {
            S next = reducer.apply(Interop.value(old), Interop.value(action));
            return Interop.same(old, next) ? old : next;
        };
    }
    @Override public <S, A> ReducerState<S, A> reducerLazy(BiFunction<S, A, S> reducer, Supplier<S> initial, StateCodec<S> codec) {
        JSObject slot = HotReload.slot("reducer");
        Ref<StateCodec<S>> selected = HotReload.enabled() ? Interop.value(refNative(new Ref<>(codec))) : null;
        JSObject pair = reducerNative(reduce(reducer), () -> {
            S value = initial.get();
            if (selected == null) return value;
            if (selected.current() == null) selected.set(StateCodecs.forInitialValue(value));
            return HotReload.restore(slot, selected.current(), value);
        });
        S snapshot = Interop.value(value(pair));
        HotReload.record(slot, selected == null ? null : selected.current(), () -> snapshot);
        JSObject setter = setter(pair);
        Consumer<A> dispatch = memoRaw(() -> action -> dispatchNative(setter, action), Dependencies.none());
        return new ReducerState<>(snapshot, dispatch);
    }
    @Override public void insertionEffect(Effect effect, Dependencies dependencies) {
        HotReload.slot("insertion-effect");
        Setup setup = () -> {
            Runnable cleanup = effect.setup();
            return cleanup == null ? null : cleanup::run;
        };
        insertionNative(setup, Interop.dependencies(dependencies));
    }
    @JSFunctor interface Call extends JSObject { void call(Object argument); }
    @JSFunctor interface Format extends JSObject { String format(Object value); }
    @Override public <A> Consumer<A> effectEvent(Consumer<A> callback) {
        HotReload.slot("effect-event");
        JSObject event = effectEventNative(argument -> callback.accept(Interop.value(argument)));
        return argument -> callEvent(event, argument);
    }
    @Override public <T> void debugValue(T value, Function<T, String> format) {
        HotReload.slot("debug-value");
        debugNative(Interop.scalar(value), format == null ? null : shown -> format.apply(Interop.value(shown)));
    }
    @JSBody(params = {"setup", "deps"}, script = "React.useInsertionEffect(function() { var cleanup = setup(); return cleanup === null ? undefined : cleanup; }, deps === null ? undefined : deps);", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native void insertionNative(Setup setup, Object[] deps);
    @JSBody(params = "callback", script = "return React.useEffectEvent(function(argument) { callback(argument); });", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject effectEventNative(Call callback);
    @JSBody(params = {"event", "argument"}, script = "event(argument);") private static native void callEvent(JSObject event, Object argument);
    @JSBody(params = {"value", "format"}, script = "if (format) React.useDebugValue(value, function(shown) { return format(shown); }); else React.useDebugValue(value);", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native void debugNative(Object value, Format format);
    @JSFunctor interface AsyncAction extends JSObject { void run(JSObject resolve); }
    @Override public TransitionState transition() {
        HotReload.slot("transition");
        JSObject pair = transitionNative();
        JSObject start = setter(pair);
        TransitionState.Starter starter = memoRaw(() -> new TransitionState.Starter() {
            @Override public void start(Runnable action) { startNative(start, action::run); }
            @Override public void startAsync(Consumer<Runnable> action) { startAsyncNative(start, resolve -> action.accept(() -> call(resolve))); }
            @Override public void startPromise(Supplier<? extends Promise<?>> action) {
                startPromiseNative(start, () -> AsyncBridge.thenable(java.util.Objects.requireNonNull(action.get(), "The transition action returned null")));
            }
        }, Dependencies.none());
        return new TransitionState(pending(pair), starter);
    }
    @Override public <T> T deferredValue(T value, T initial, boolean hasInitial) {
        HotReload.slot("deferred-value");
        // React compares by identity. Equal scalars are kept as one Java object across renders,
        // otherwise a recomputed but equal string would be deferred again on every render.
        Ref<Object[]> last = Interop.value(refNative(new Ref<>(null)));
        Object[] previous = last.current();
        T stable = previous != null && Interop.same(previous[0], value) ? Interop.value(previous[0]) : value;
        last.set(new Object[] {stable});
        return Interop.value(deferredNative(stable, initial, hasInitial));
    }
    @JSBody(script = "return React.useTransition();", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject transitionNative();
    @JSBody(params = "pair", script = "return !!pair[0];") private static native boolean pending(JSObject pair);
    @JSBody(params = {"start", "action"}, script = "start(function() { action(); });") private static native void startNative(JSObject start, Cleanup action);
    @JSBody(params = {"start", "action"}, script = "start(function() { return new Promise(function(resolve) { action(resolve); }); });") private static native void startAsyncNative(JSObject start, AsyncAction action);
    @JSFunctor interface StateAction extends JSObject { Object run(Object previous, JSObject data); }
    @Override public <S> ActionState<S> actionState(BiFunction<S, FormData, ?> action, S initial, boolean async) {
        HotReload.slot("action-state");
        JSObject triple = actionStateNative((previous, data) -> {
            Object next = action.apply(Interop.value(previous), new Forms.BrowserFormData(data));
            return async ? AsyncBridge.thenable((Promise<?>) java.util.Objects.requireNonNull(next, "The action returned null")) : next;
        }, initial);
        JSObject dispatch = setter(triple);
        FormAction formAction = memoRaw(() -> new Forms.NativeAction(dispatch), Dependencies.none());
        return new ActionState<>(Interop.value(value(triple)), formAction, third(triple));
    }
    @Override public <T> State<T> optimistic(T value) {
        HotReload.slot("optimistic");
        JSObject pair = optimisticNative(value);
        JSObject set = setter(pair);
        State.Setter<T> setter = memoRaw(() -> update -> updateNative(set, old -> update.apply(Interop.value(old))), Dependencies.none());
        return new State<>(Interop.value(value(pair)), setter);
    }
    @Override public <S, A> ReducerState<S, A> optimistic(S value, BiFunction<S, A, S> update) {
        HotReload.slot("optimistic");
        JSObject pair = optimisticReducerNative(value, (state, action) -> update.apply(Interop.value(state), Interop.value(action)));
        JSObject add = setter(pair);
        Consumer<A> dispatch = memoRaw(() -> action -> dispatchNative(add, action), Dependencies.none());
        return new ReducerState<>(Interop.value(value(pair)), dispatch);
    }
    @Override public FormStatus formStatus() {
        HotReload.slot("form-status");
        JSObject status = formStatusNative();
        JSObject data = statusData(status);
        return new FormStatus(statusPending(status), data == null ? null : new Forms.BrowserFormData(data), statusMethod(status));
    }
    @JSBody(params = {"action", "initial"}, script = "return React.useActionState(function(previous, data) { return action(previous, data); }, initial);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject actionStateNative(StateAction action, Object initial);
    @JSBody(params = "triple", script = "return !!triple[2];") private static native boolean third(JSObject triple);
    @JSBody(params = "value", script = "return React.useOptimistic(value);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject optimisticNative(Object value);
    @JSBody(params = {"value", "update"}, script = "return React.useOptimistic(value, function(state, action) { return update(state, action); });", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native JSObject optimisticReducerNative(Object value, Reduce update);
    @JSBody(script = "return ReactDOM.useFormStatus();", imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom")) private static native JSObject formStatusNative();
    @JSBody(params = "status", script = "return !!status.pending;") private static native boolean statusPending(JSObject status);
    @JSBody(params = "status", script = "return status.data ? status.data : null;") private static native JSObject statusData(JSObject status);
    @JSBody(params = "status", script = "return status.pending && status.method ? String(status.method) : null;") private static native String statusMethod(JSObject status);
    @JSFunctor interface PromiseAction extends JSObject { JSObject run(); }
    @JSBody(params = {"start", "action"}, script = "start(function() { return action(); });") private static native void startPromiseNative(JSObject start, PromiseAction action);
    @Override public <T> T use(Promise<T> promise) { return Interop.value(useNative(AsyncBridge.thenable(promise))); }
    @Override public <T> T use(Context<T> context) { return Interop.value(useNative(React.context(context))); }
    @JSBody(params = "usable", script = "return React.use(usable);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native Object useNative(JSObject usable);
    @JSBody(params = "resolve", script = "resolve();") private static native void call(JSObject resolve);
    @JSBody(params = {"value", "initial", "hasInitial"}, script = "return hasInitial ? React.useDeferredValue(value, initial) : React.useDeferredValue(value);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native Object deferredNative(Object value, Object initial, boolean hasInitial);
    @Override public void effect(Effect effect, Dependencies dependencies, boolean layout) {
        HotReload.slot(layout ? "layout-effect" : "effect");
        Setup setup = () -> {
            Runnable cleanup = effect.setup();
            return cleanup == null ? null : cleanup::run;
        };
        effectNative(setup, Interop.dependencies(dependencies), layout);
    }
    @Override public <T> Ref<T> ref(T initial) { HotReload.slot("ref"); return Interop.value(refNative(new Ref<>(initial))); }
    @JSFunctor interface Assign extends JSObject { void set(Object value); }
    private static final JSObject HANDLES = weakMap();
    @Override public <H> void imperativeHandle(Ref<H> ref, Supplier<H> create, Dependencies dependencies) {
        HotReload.slot("imperative-handle");
        // React compares the ref by identity, so each Ref keeps one target function.
        JSObject target = ref == null ? null : handleTarget(HANDLES, ref, handle -> ref.set(Interop.value(handle)));
        imperativeNative(target, create::get, Interop.dependencies(dependencies));
    }
    @JSBody(script = "return new WeakMap();") private static native JSObject weakMap();
    @JSBody(params = {"cache", "ref", "assign"}, script = "var target = cache.get(ref); if (!target) { target = function(handle) { assign(handle); }; cache.set(ref, target); } return target;") private static native JSObject handleTarget(JSObject cache, Object ref, Assign assign);
    @JSBody(params = {"target", "factory", "deps"}, script = "React.useImperativeHandle(target, factory, deps === null ? undefined : deps);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native void imperativeNative(JSObject target, Factory factory, Object[] deps);
    @Override public <T> T memo(Supplier<T> calculation, Dependencies dependencies) {
        HotReload.slot("memo");
        return memoRaw(calculation, dependencies);
    }
    private <T> T memoRaw(Supplier<T> calculation, Dependencies dependencies) {
        return Interop.value(memoNative(calculation::get, Interop.dependencies(dependencies)));
    }
    @Override public <T> T context(Context<T> context) { HotReload.slot("context"); return Interop.value(contextNative(React.context(context))); }
    @Override public <T> T externalStore(ExternalStore<T> store) {
        HotReload.slot("external-store");
        StoreBinding<T> binding = memoRaw(() -> new StoreBinding<>(store), Dependencies.of(store));
        return Interop.value(storeNative(binding.subscribe, binding.snapshot));
    }
    private static final class StoreBinding<T> {
        final Subscribe subscribe;
        final Factory snapshot;
        StoreBinding(ExternalStore<T> store) {
            subscribe = listener -> {
                Runnable unsubscribe = store.subscribe(listener::run);
                return unsubscribe::run;
            };
            snapshot = store::snapshot;
        }
    }
    @Override public String id() { HotReload.slot("id"); return idNative(); }
    @JSBody(script = "return React.useId();", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native String idNative();
    @JSBody(params = "initial", script = "return React.useState(initial);", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject stateNative(Factory initial);
    @JSBody(params = {"reducer", "initial"}, script = "return React.useReducer(reducer, null, initial);", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject reducerNative(Reduce reducer, Factory initial);
    @JSBody(params = "pair", script = "return pair[0];") private static native Object value(JSObject pair);
    @JSBody(params = "pair", script = "return pair[1];") private static native JSObject setter(JSObject pair);
    @JSBody(params = {"setter", "update"}, script = "setter(update);") private static native void updateNative(JSObject setter, Update update);
    @JSBody(params = {"setter", "action"}, script = "setter(action);") private static native void dispatchNative(JSObject setter, Object action);
    @JSBody(params = {"setup", "deps", "layout"}, script = "var effect = function() { var cleanup = setup(); return cleanup === null ? undefined : cleanup; }; if (layout) React.useLayoutEffect(effect, deps === null ? undefined : deps); else React.useEffect(effect, deps === null ? undefined : deps);", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native void effectNative(Setup setup, Object[] deps, boolean layout);
    @JSBody(params = "initial", script = "return React.useRef(initial).current;", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native Object refNative(Object initial);
    @JSBody(params = {"factory", "deps"}, script = "return React.useMemo(factory, deps === null ? undefined : deps);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native Object memoNative(Factory factory, Object[] deps);
    @JSBody(params = "context", script = "return React.useContext(context);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native Object contextNative(JSObject context);
    @JSBody(params = {"subscribe", "snapshot"}, script = "return React.useSyncExternalStore(subscribe, snapshot);", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native Object storeNative(Subscribe subscribe, Factory snapshot);
}
