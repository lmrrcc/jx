package jx;

import java.util.Objects;
import java.util.function.Function;

/** Stable component identity. Declare descriptors once, normally as a static final field.
 * The renderer invokes the render function; applications compose descriptors through Views. */
public final class Component<P> {
    private final String name;
    private final Function<P, View> render;
    public Component(String name, Function<P, View> render) {
        this.name = Objects.requireNonNull(name);
        if (name.isBlank()) throw new IllegalArgumentException("Component name is empty");
        this.render = Objects.requireNonNull(render);
    }
    public String name() { return name; }
    /** Renderer entry point; do not call another component directly from application rendering. */
    public View render(P props) { return Objects.requireNonNull(render.apply(props), "Component returned null; use Views.fragment()"); }
}
