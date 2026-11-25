package jx.react;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import jx.Async;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Typed functions backed by React's actual request-scoped server cache.
 *
 * <p>Create cached functions once, outside rendering. Each factory call creates
 * a wrapper; React owns request isolation and expiry. In the pinned runtime,
 * wrappers of the same Java function instance share that source function's cache;
 * different source functions do not. Outside
 * a server render, the function still runs but React does not cache its result.
 * Returned Java values (including null and promises) retain identity. A thrown
 * failure is cached and rethrown, not converted into a successful value.
 *
 * <p>Keys use the bridge's native scalar mapping: strings/characters, booleans
 * and boxed primitive numeric types become JavaScript primitives. Other Java
 * values, including BigInteger/BigDecimal and custom Number types, compare by
 * object identity, never {@code equals}. Long keys outside JavaScript's exact integer
 * range are rejected. React's native cache determines primitive-key equality;
 * in the pinned runtime NaN keys match and positive/negative zero share an entry.
 * These key rules differ from hook dependency comparison.
 *
 * <p>Calls belong to one JavaScript event loop. A function must return without
 * suspending that call; return a {@code Promise}, optionally from {@code Async.run},
 * for asynchronous work. Cache lifetime does not itself cancel that work: observe
 * {@link #cacheSignal()} and propagate its native abort signal or subscribe explicitly.
 */
public final class ReactCache {
    static { Async.installRuntime(AsyncBridge.INSTANCE); }
    private static final JSObject SUPPLIERS = sources();
    private static final JSObject FUNCTIONS = sources();
    private static final JSObject BI_FUNCTIONS = sources();
    private ReactCache() {}

    /** A zero-argument cached function. The function is not invoked by this factory. */
    public static <R> Supplier<R> cache(Supplier<R> function) {
        Objects.requireNonNull(function, "function");
        Invocation<R> invocation = new Invocation<>(SUPPLIERS, function);
        return () -> invocation.run(function, null, null, 0);
    }

    /** A one-argument cached function. Null arguments and null results are allowed. */
    public static <A, R> Function<A, R> cache(Function<A, R> function) {
        Objects.requireNonNull(function, "function");
        Invocation<R> invocation = new Invocation<>(FUNCTIONS, function);
        return first -> invocation.run(() -> function.apply(first), Interop.scalar(first), null, 1);
    }

    /** A two-argument cached function. Both arguments participate separately in the key.
     * For more arguments, compose a deliberate identity-bearing key; record equality
     * does not turn a newly allocated record into an equal native cache key.
     */
    public static <A, B, R> BiFunction<A, B, R> cache(BiFunction<A, B, R> function) {
        Objects.requireNonNull(function, "function");
        Invocation<R> invocation = new Invocation<>(BI_FUNCTIONS, function);
        return (first, second) -> invocation.run(() -> function.apply(first, second),
                Interop.scalar(first), Interop.scalar(second), 2);
    }

    /** React's current cache lifetime, or null outside a server render. Wrappers
     * returned by separate calls need not be the same Java object; React owns the
     * underlying native signal. Successful completion, failure or abort can end it.
     */
    public static CacheSignal cacheSignal() {
        JSObject signal = currentSignal();
        return signal == null ? null : new CacheSignal(signal);
    }

    @JSFunctor interface Query extends JSObject { Object get(); }
    /** Java arguments stay in the typed callback during the synchronous lookup.
     * React only receives scalar/identity keys. Native weak maps preserve the
     * source-function identity across wrapper factories without retaining requests.
     */
    private static final class Invocation<R> {
        private final JSObject source;
        private final JSObject cached;
        Invocation(JSObject sources, Object function) {
            source = source(sources, function);
            cached = create(source);
        }
        R run(Supplier<R> function, JSObject first, JSObject second, int arity) {
            return Interop.value(call(cached, source, function::get, first, second, arity));
        }
    }

    @JSBody(script = "return new WeakMap();") private static native JSObject sources();
    @JSBody(params = {"sources", "key"}, script = """
            var source = sources.get(key);
            if (!source) {
                source = {active: null, query: function() {
                    if (!source.active) throw new Error('Cache computation escaped its invocation');
                    return source.active();
                }};
                sources.set(key, source);
            }
            return source;
            """) private static native JSObject source(JSObject sources, Object function);
    @JSBody(params = "source", script = "return React.cache(source.query);", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject create(JSObject source);
    @JSBody(params = {"cached", "source", "query", "first", "second", "arity"}, script = """
            var previous = source.active;
            source.active = query;
            try {
                if (arity === 0) return cached();
                if (arity === 1) return cached(first);
                if (arity === 2) return cached(first, second);
                throw new Error('Unsupported cache arity');
            } finally { source.active = previous; }
            """)
    private static native Object call(JSObject cached, JSObject source, Query query, JSObject first, JSObject second, int arity);
    @JSBody(script = "return React.cacheSignal();", imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject currentSignal();
}
