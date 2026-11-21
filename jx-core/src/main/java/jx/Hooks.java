package jx;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import jx.spi.HookRuntime;

/** Hooks must run unconditionally in the same order, during React component rendering or custom hooks.
 * This facade has no JVM fallback: using hooks outside an installed browser renderer is an error. */
public final class Hooks {
    private static HookRuntime runtime;
    private Hooks() {}
    public static <T> State<T> useState(T initial) { return useStateLazy(() -> initial); }
    public static <T> State<T> useState(T initial, StateCodec<T> codec) { return useStateLazy(() -> initial, codec); }
    public static <T> State<T> useStateLazy(Supplier<T> initial) { return runtime().state(Objects.requireNonNull(initial), null); }
    public static <T> State<T> useStateLazy(Supplier<T> initial, StateCodec<T> codec) { return runtime().state(Objects.requireNonNull(initial), Objects.requireNonNull(codec)); }
    public static <S, A> ReducerState<S, A> useReducer(BiFunction<S, A, S> reducer, S initial) { return runtime().reducer(Objects.requireNonNull(reducer), initial, null); }
    public static <S, A> ReducerState<S, A> useReducer(BiFunction<S, A, S> reducer, S initial, StateCodec<S> codec) { return runtime().reducer(Objects.requireNonNull(reducer), initial, Objects.requireNonNull(codec)); }
    /** A reducer whose first state is computed once, on mount, like React's third {@code useReducer} argument. */
    public static <S, A> ReducerState<S, A> useReducerLazy(BiFunction<S, A, S> reducer, Supplier<S> initial) { return runtime().reducerLazy(Objects.requireNonNull(reducer), Objects.requireNonNull(initial), null); }
    public static <S, A> ReducerState<S, A> useReducerLazy(BiFunction<S, A, S> reducer, Supplier<S> initial, StateCodec<S> codec) { return runtime().reducerLazy(Objects.requireNonNull(reducer), Objects.requireNonNull(initial), Objects.requireNonNull(codec)); }
    /** Runs before layout effects and before React changes the DOM; meant for inserting styles.
     * Refs are not attached yet and state must not be updated here. */
    public static void useInsertionEffect(Effect effect, Dependencies dependencies) { runtime().insertionEffect(Objects.requireNonNull(effect), Objects.requireNonNull(dependencies)); }
    /** An effect event: the returned action always runs the callback of the latest render, so an
     * effect can call it without listing what the callback reads as dependencies. Call it only
     * from effects, never during rendering, and do not pass it to other components. */
    public static Runnable useEffectEvent(Runnable callback) {
        Objects.requireNonNull(callback);
        Consumer<Object> event = runtime().effectEvent(ignored -> callback.run());
        return () -> event.accept(null);
    }
    /** An effect event that takes one argument; see {@link #useEffectEvent(Runnable)}. */
    public static <A> Consumer<A> useEffectEvent(Consumer<A> callback) { return runtime().effectEvent(Objects.requireNonNull(callback)); }
    /** Labels the calling custom hook in React DevTools. It has no effect on rendering. */
    public static void useDebugValue(Object value) { runtime().debugValue(value, null); }
    /** As {@link #useDebugValue(Object)}; the formatter runs only when DevTools inspects the hook. */
    public static <T> void useDebugValue(T value, Function<T, String> format) { runtime().debugValue(value, Objects.requireNonNull(format)); }
    /** React's {@code useTransition}. */
    public static TransitionState useTransition() { return runtime().transition(); }
    /** React's {@code useDeferredValue}: returns the previous value while a render with the new
     * one is prepared in the background. Values compare like props of a memoized component:
     * strings, characters, booleans and boxed numbers by value, other objects by identity. */
    public static <T> T useDeferredValue(T value) { return runtime().deferredValue(value, null, false); }
    /** As {@link #useDeferredValue(Object)}, with the value to show during the first render. */
    public static <T> T useDeferredValue(T value, T initial) { return runtime().deferredValue(value, initial, true); }
    public static void useEffect(Effect effect, Dependencies dependencies) { runtime().effect(Objects.requireNonNull(effect), Objects.requireNonNull(dependencies), false); }
    public static void useLayoutEffect(Effect effect, Dependencies dependencies) { runtime().effect(Objects.requireNonNull(effect), Objects.requireNonNull(dependencies), true); }
    public static <T> Ref<T> useRef(T initial) { return runtime().ref(initial); }
    /** Publishes a handle through a ref that the parent passed as a prop, in place of a DOM node.
     * The handle is created after commit and again when a dependency changes; the ref holds null
     * after unmount. A null ref is ignored. */
    public static <H> void useImperativeHandle(Ref<H> ref, Supplier<H> create, Dependencies dependencies) { runtime().imperativeHandle(ref, Objects.requireNonNull(create), Objects.requireNonNull(dependencies)); }
    public static DomRef useDomRef() { return useMemo(DomRef::new, Dependencies.none()); }
    public static <T> T useMemo(Supplier<T> calculation, Dependencies dependencies) { return runtime().memo(Objects.requireNonNull(calculation), Objects.requireNonNull(dependencies)); }
    public static <T> T useCallback(T callback, Dependencies dependencies) { return useMemo(() -> callback, dependencies); }
    public static <T> Context<T> createContext(T defaultValue) { return new Context<>(defaultValue); }
    public static <T> T useContext(Context<T> context) { return runtime().context(Objects.requireNonNull(context)); }
    public static <T> T useSyncExternalStore(ExternalStore<T> store) { return runtime().externalStore(Objects.requireNonNull(store)); }
    public static String useId() { return runtime().id(); }
    /** Renderer bootstrap only. Replacing an active runtime would break mounted hook ownership. */
    public static void installRuntime(HookRuntime candidate) {
        Objects.requireNonNull(candidate);
        if (runtime != null && runtime != candidate) throw new IllegalStateException("A hook runtime is already installed");
        runtime = candidate;
    }
    private static HookRuntime runtime() {
        if (runtime == null) throw new IllegalStateException("Hooks require a mounted browser React component");
        return runtime;
    }
}
