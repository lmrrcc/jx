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

    @Override public <T> State<T> state(Supplier<T> initial) {
        JSObject pair = stateNative(initial::get);
        JSObject dispatch = setter(pair);
        State.Setter<T> setter = memo(() -> update -> updateNative(dispatch, old -> {
            T previous = Interop.value(old);
            T next = update.apply(previous);
            return Interop.same(previous, next) ? previous : next;
        }), Dependencies.none());
        return new State<>(Interop.value(value(pair)), setter);
    }
    @Override public <S, A> ReducerState<S, A> reducer(BiFunction<S, A, S> reducer, S initial) {
        JSObject pair = reducerNative((old, action) -> {
            S next = reducer.apply(Interop.value(old), Interop.value(action));
            return Interop.same(old, next) ? old : next;
        }, initial);
        JSObject setter = setter(pair);
        Consumer<A> dispatch = memo(() -> action -> dispatchNative(setter, action), Dependencies.none());
        return new ReducerState<>(Interop.value(value(pair)), dispatch);
    }
    @Override public void effect(Effect effect, Dependencies dependencies, boolean layout) {
        Setup setup = () -> {
            Runnable cleanup = effect.setup();
            return cleanup == null ? null : cleanup::run;
        };
        effectNative(setup, Interop.dependencies(dependencies), layout);
    }
    @Override public <T> Ref<T> ref(T initial) { return Interop.value(refNative(new Ref<>(initial))); }
    @Override public <T> T memo(Supplier<T> calculation, Dependencies dependencies) {
        return Interop.value(memoNative(calculation::get, Interop.dependencies(dependencies)));
    }
    @Override public <T> T context(Context<T> context) { return Interop.value(contextNative(React.context(context))); }
    @Override public <T> T externalStore(ExternalStore<T> store) {
        StoreBinding<T> binding = memo(() -> new StoreBinding<>(store), Dependencies.of(store));
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
    @Override public String id() { return idNative(); }
    @JSBody(script = "return React.useId();", imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native String idNative();
    @JSBody(params = "initial", script = "return React.useState(initial);", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject stateNative(Factory initial);
    @JSBody(params = {"reducer", "initial"}, script = "return React.useReducer(reducer, initial);", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject reducerNative(Reduce reducer, Object initial);
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
