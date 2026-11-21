package jx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Typed counterpart of React's {@code Children} for a {@code View children} prop.
 * Nested markup reaches a component as one fragment; these methods treat that fragment's direct
 * children as the list, and any other view as a list of one. They do not look inside a child,
 * so a component child counts as one whatever it renders. Null and boolean children are dropped
 * when a view is built and are therefore never counted. */
public final class Children {
    private Children() {}

    /** The children as an immutable list; empty for null. */
    public static List<View> toList(View children) {
        if (children == null) return List.of();
        return children instanceof View.Fragment fragment ? fragment.children() : List.of(children);
    }

    public static int count(View children) { return toList(children).size(); }

    /** A fragment of {@code mapper} applied to each child in order; a null result is left out. */
    public static View map(View children, Function<View, View> mapper) {
        Objects.requireNonNull(mapper);
        List<View> result = new ArrayList<>();
        for (View child : toList(children)) {
            View mapped = mapper.apply(child);
            if (mapped != null) result.add(mapped);
        }
        return new View.Fragment(result);
    }

    public static void forEach(View children, Consumer<View> action) {
        Objects.requireNonNull(action);
        toList(children).forEach(action);
    }

    /** The single child.
     * @throws IllegalArgumentException when there is not exactly one child */
    public static View only(View children) {
        List<View> list = toList(children);
        if (list.size() != 1) throw new IllegalArgumentException("Expected exactly one child, found " + list.size());
        return list.getFirst();
    }
}
