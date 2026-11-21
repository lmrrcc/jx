package jx;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

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
    /** The memo comparison, or null when every parent render renders this component. */
    public BiPredicate<P, P> propsEqual() { return propsEqual; }
    /** React's prop comparison ({@code Object.is}) for Java values: strings, characters, booleans
     * and boxed numbers compare by value, everything else by identity. */
    public static boolean sameValue(Object first, Object second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        boolean scalar = first instanceof String || first instanceof Character || first instanceof Boolean
                || first instanceof Byte || first instanceof Short || first instanceof Integer
                || first instanceof Long || first instanceof Float || first instanceof Double;
        return scalar && first.equals(second);
    }
    /** Renderer entry point; do not call another component directly from application rendering. */
    public View render(P props) { return Objects.requireNonNull(render.apply(props), "Component returned null; use Views.fragment()"); }
}
