package jx;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/** Stable component identity. Declare descriptors once, normally as a static final field.
 * The renderer invokes the render function; applications compose descriptors through Views. */
public final class ComponentType<P> {
    private final String name;
    private final String stateSignature;
    private final Function<P, View> render;
    private final BiPredicate<P, P> propsEqual;
    public ComponentType(String name, Function<P, View> render) {
        this(name, "", render);
    }
    /** Compiler-supplied hook/type fingerprint. Empty fingerprints safely disable hot-state restoration. */
    public ComponentType(String name, String stateSignature, Function<P, View> render) {
        this(name, stateSignature, render, null);
    }
    private ComponentType(String name, String stateSignature, Function<P, View> render, BiPredicate<P, P> propsEqual) {
        this.propsEqual = propsEqual;
        this.name = Objects.requireNonNull(name);
        this.stateSignature = Objects.requireNonNull(stateSignature);
        if (name.isBlank()) throw new IllegalArgumentException("Component name is empty");
        this.render = Objects.requireNonNull(render);
    }
    public String name() { return name; }
    public String stateSignature() { return stateSignature; }
    /** A memoized descriptor of the same component, like React's {@code memo}: when the parent
     * rerenders and {@code propsEqual.test(previous, next)} is true, the renderer keeps the previous
     * output. The result is a new identity; declare it once and use it in place of this descriptor.
     * The comparison runs during rendering and must be pure. */
    public ComponentType<P> memo(BiPredicate<P, P> propsEqual) {
        return new ComponentType<>(name, stateSignature, render, Objects.requireNonNull(propsEqual));
    }
    /** A component that is loaded on first use, like React's {@code lazy}. {@code load} runs once,
     * when the component first renders; until its promise is settled the nearest
     * {@code React.suspense} shows its fallback, and a failed load reaches the nearest error
     * boundary and is not retried; use {@link LazyComponent} to retry. Declare the result once, outside rendering. This defers the
     * work of {@code load}; it does not by itself move the component's code out of the bundle. */
    public static <P> ComponentType<P> lazy(String name, Supplier<Promise<ComponentType<P>>> load) {
        return LazyComponent.of(name, load).type();
    }
    /** The memo comparison, or null when every parent render renders this component. */
    public BiPredicate<P, P> propsEqual() { return propsEqual; }
    /** Default memo comparison for Java props. Strings/characters and boxed primitive
     * numbers compare as their native React scalars, including cross-boxed numeric
     * values, equal NaNs and distinct signed zeros. Other values use identity.
     * Longs outside JavaScript's exact integer range retain exact Java Long equality;
     * this does not make them valid native number bindings or wire values. */
    public static boolean sameValue(Object first, Object second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if ((first instanceof String || first instanceof Character)
                && (second instanceof String || second instanceof Character)) {
            return first.toString().equals(second.toString());
        }
        if (first instanceof Boolean) return first.equals(second);
        if (primitiveNumber(first) && primitiveNumber(second)) {
            if (inexactNativeLong(first) || inexactNativeLong(second)) return first.equals(second);
            return Double.doubleToLongBits(((Number) first).doubleValue())
                    == Double.doubleToLongBits(((Number) second).doubleValue());
        }
        return false;
    }
    private static boolean primitiveNumber(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double;
    }
    private static boolean inexactNativeLong(Object value) {
        return value instanceof Long number && (number < -9_007_199_254_740_991L || number > 9_007_199_254_740_991L);
    }
    /** Renderer entry point; do not call another component directly from application rendering.
     * Null deliberately renders no output while React retains the component's mounted
     * identity, hooks and effects. A thrown render failure still propagates. */
    public View render(P props) { return render.apply(props); }
}
