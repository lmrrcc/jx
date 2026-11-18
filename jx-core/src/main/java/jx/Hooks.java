package jx;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import jx.spi.HookRuntime;

/** Hooks must run unconditionally in the same order, during React component rendering or custom hooks.
 * This facade has no JVM fallback: using hooks outside an installed browser renderer is an error. */
public final class Hooks {
    private static HookRuntime runtime;
    private Hooks() {}
    public static <T> State<T> useState(T initial) { return useStateLazy(() -> initial); }
    public static <T> State<T> useStateLazy(Supplier<T> initial) { return runtime().state(Objects.requireNonNull(initial)); }
    public static <S, A> ReducerState<S, A> useReducer(BiFunction<S, A, S> reducer, S initial) { return runtime().reducer(Objects.requireNonNull(reducer), initial); }
    public static void useEffect(Effect effect, Dependencies dependencies) { runtime().effect(Objects.requireNonNull(effect), Objects.requireNonNull(dependencies), false); }
    public static void useLayoutEffect(Effect effect, Dependencies dependencies) { runtime().effect(Objects.requireNonNull(effect), Objects.requireNonNull(dependencies), true); }
    public static <T> Ref<T> useRef(T initial) { return runtime().ref(initial); }
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
