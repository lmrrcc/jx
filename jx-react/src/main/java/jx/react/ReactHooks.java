package jx.react;

import jx.*;
import jx.spi.HookRuntime;
import java.util.function.BiFunction;
import java.util.function.Consumer;
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
        JSObject pair = reducerNative((old, action) -> {
            S next = reducer.apply(Interop.value(old), Interop.value(action));
            return Interop.same(old, next) ? old : next;
        }, () -> HotReload.restore(slot, selected, initial));
        S snapshot = Interop.value(value(pair));
        HotReload.record(slot, selected, () -> snapshot);
        JSObject setter = setter(pair);
        Consumer<A> dispatch = memoRaw(() -> action -> dispatchNative(setter, action), Dependencies.none());
        return new ReducerState<>(snapshot, dispatch);
    }
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
