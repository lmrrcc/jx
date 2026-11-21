package jx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Typed counterpart of React's {@code Children} for a {@code View children} prop.
 * Nested markup reaches a component as one fragment. These methods walk that fragment's direct
 * children and, like React, look through collection children ({@link View.Items}) but not inside
 * an element, component or written fragment. An empty position ({@link View.Empty}, from a null
 * or boolean child) is counted and visited, as React counts null, but left out of {@link #toList}. */
public final class Children {
    private Children() {}

    /** The children without empty positions, as an immutable list; empty for null. */
    public static List<View> toList(View children) {
        return positions(children).stream().filter(child -> !(child instanceof View.Empty)).toList();
    }

    private static List<View> positions(View children) {
        if (children == null) return List.of();
        List<View> result = new ArrayList<>();
        if (children instanceof View.Fragment fragment) fragment.children().forEach(child -> flatten(child, result));
        else flatten(children, result);
        return result;
    }

    private static void flatten(View child, List<View> result) {
        if (child instanceof View.Items items) items.children().forEach(item -> flatten(item, result));
        else result.add(child);
    }

    /** The number of positions, empty ones included. */
    public static int count(View children) { return positions(children).size(); }

    /** A fragment of {@code mapper} applied to each position in order. An empty position is passed
     * as {@link View.Empty}; a null result becomes an empty position. */
    public static View map(View children, Function<View, View> mapper) {
        Objects.requireNonNull(mapper);
        List<View> result = new ArrayList<>();
        for (View child : positions(children)) {
            View mapped = mapper.apply(child);
            result.add(mapped == null ? View.Empty.INSTANCE : mapped);
        }
        return new View.Fragment(result);
    }

    public static void forEach(View children, Consumer<View> action) {
        Objects.requireNonNull(action);
        positions(children).forEach(action);
    }

    /** The single child.
     * @throws IllegalArgumentException when there is not exactly one child */
    public static View only(View children) {
        List<View> list = toList(children);
        if (list.size() != 1) throw new IllegalArgumentException("Expected exactly one child, found " + list.size());
        return list.getFirst();
    }
}
